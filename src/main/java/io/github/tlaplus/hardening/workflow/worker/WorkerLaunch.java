package io.github.tlaplus.hardening.workflow.worker;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;

import io.github.tlaplus.hardening.common.FileTrees;
import io.github.tlaplus.hardening.workflow.WorkflowException;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Brings one isolated worker up: private scratch storage, a private loopback listener, the child
 * JVM, and an authenticated control connection.
 *
 * <p>The connection is authenticated because the listener accepts from anywhere on the loopback
 * interface: the parent passes a one-time token to the child as a system property, and a child that
 * does not present it is not this worker.
 *
 * <p>Any failure here leaves nothing behind: the listener, the child, the drained output, and the
 * scratch tree are all released before the failure is reported.
 */
final class WorkerLaunch {
    private static final Duration PROCESS_TERMINATION_TIMEOUT = Duration.ofMillis(500);

    private WorkerLaunch() {}

    /** A worker that has connected and authenticated, ready to accept requests. */
    record Launched(Process process, WorkerChannel channel, WorkerOutput output) {}

    static Launched start(WorkerSpec spec) throws WorkflowException, InterruptedException {
        var description = spec.description();
        var scratch = spec.scratchDirectory().toAbsolutePath().normalize();
        if (!Files.isDirectory(scratch, NOFOLLOW_LINKS)) {
            throw new WorkflowException(
                    description + " scratch directory does not exist: " + scratch);
        }

        // Give the child private scratch storage and a private control-channel listener.
        final Path temporaryDirectory;
        try {
            temporaryDirectory = Files.createTempDirectory(scratch, "worker-");
        } catch (IOException exception) {
            throw new WorkflowException(
                    "cannot create " + description + " scratch directory", exception);
        }

        final ServerSocket listener;
        try {
            listener = new ServerSocket();
            listener.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 1);
        } catch (IOException exception) {
            deleteAfterFailure(temporaryDirectory, exception);
            throw new WorkflowException(
                    "cannot create " + description + " protocol listener", exception);
        }

        var token = UUID.randomUUID().toString();
        final Process process;
        try {
            process = new ProcessBuilder(command(spec, temporaryDirectory, listener, token))
                    .start();
        } catch (IOException exception) {
            closeListener(listener, exception);
            deleteAfterFailure(temporaryDirectory, exception);
            throw new WorkflowException("cannot start " + description, exception);
        }

        // Drain process output continuously so native libraries cannot block the control channel.
        var output = new WorkerOutput(description, process, temporaryDirectory);
        try {
            process.getOutputStream().close();
        } catch (IOException ignored) {
            // The protocol uses a private socket; the child does not consume standard input.
        }

        return connect(spec, listener, process, output, token);
    }

    /** Accepts the child's connection and checks its handshake under the startup deadline. */
    private static Launched connect(
            WorkerSpec spec,
            ServerSocket listener,
            Process process,
            WorkerOutput output,
            String token)
            throws WorkflowException, InterruptedException {
        var description = spec.description();
        var timeout = spec.startupTimeout();
        final Socket socket;
        try {
            socket = WorkerChannel.await(listener::accept, timeout);
        } catch (TimeoutException exception) {
            closeListener(listener, exception);
            throw abandon(
                    process, output, description + " did not connect within " + timeout, exception);
        } catch (ExecutionException exception) {
            closeListener(listener, exception);
            throw abandon(
                    process, output, description + " failed during startup", exception.getCause());
        } catch (InterruptedException exception) {
            closeListener(listener, exception);
            terminate(process);
            output.close();
            throw exception;
        }
        try {
            listener.close();
        } catch (IOException exception) {
            closeSocket(socket, exception);
            throw abandon(
                    process,
                    output,
                    "cannot close " + description + " protocol listener",
                    exception);
        }

        final WorkerChannel channel;
        try {
            channel = WorkerChannel.open(socket);
        } catch (IOException exception) {
            closeSocket(socket, exception);
            throw abandon(
                    process,
                    output,
                    description + " could not open its protocol connection",
                    exception);
        }

        try {
            if (!channel.readHandshake(timeout, token)) {
                channel.close();
                throw abandon(
                        process, output, description + " protocol handshake failed", null);
            }
            return new Launched(process, channel, output);
        } catch (TimeoutException exception) {
            channel.close();
            throw abandon(
                    process, output, description + " did not start within " + timeout, exception);
        } catch (ExecutionException exception) {
            channel.close();
            throw abandon(
                    process, output, description + " failed during startup", exception.getCause());
        } catch (InterruptedException exception) {
            channel.close();
            terminate(process);
            output.close();
            throw exception;
        }
    }

    /** Releases a worker that never became usable and reports why, with everything it printed. */
    private static WorkflowException abandon(
            Process process, WorkerOutput output, String diagnostic, Throwable cause) {
        terminate(process);
        output.close();
        return cause == null
                ? new WorkflowException(output.describe(diagnostic))
                : new WorkflowException(output.describe(diagnostic), cause);
    }

    private static List<String> command(
            WorkerSpec spec, Path temporaryDirectory, ServerSocket listener, String token) {
        var command = new ArrayList<String>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.addAll(spec.jvmArguments());
        command.add("-XX:ErrorFile=" + WorkerOutput.fatalErrorReportPattern(temporaryDirectory));
        command.add("-Djava.io.tmpdir=" + temporaryDirectory);
        command.add("-D" + ToolWorkerProtocol.PORT_PROPERTY + "=" + listener.getLocalPort());
        command.add("-D" + ToolWorkerProtocol.TOKEN_PROPERTY + "=" + token);
        command.add("-cp");
        command.add(classpath(spec.classpathPrefix()));
        command.add(spec.workerMain().getName());
        return command;
    }

    /** Puts the worker's own class path in front of this JVM's, so its tool version wins. */
    private static String classpath(List<Path> prefix) {
        var entries = new ArrayList<String>(prefix.size() + 1);
        for (var path : prefix) {
            entries.add(path.toAbsolutePath().normalize().toString());
        }
        entries.add(System.getProperty("java.class.path"));
        return String.join(File.pathSeparator, entries);
    }

    /** Ends a child process, escalating from a request to a forced kill. */
    static void terminate(Process process) {
        if (!process.isAlive()) {
            return;
        }
        process.destroy();
        try {
            if (!process.waitFor(
                    PROCESS_TERMINATION_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                process.waitFor(
                        PROCESS_TERMINATION_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }

    /** Waits for a child that was asked to stop, escalating if it does not. */
    static void awaitStop(Process process) {
        try {
            if (!process.waitFor(
                    PROCESS_TERMINATION_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroy();
            }
            if (!process.waitFor(
                    PROCESS_TERMINATION_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                process.waitFor(
                        PROCESS_TERMINATION_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }

    private static void closeListener(ServerSocket listener, Throwable failure) {
        try {
            listener.close();
        } catch (IOException closeException) {
            failure.addSuppressed(closeException);
        }
    }

    private static void closeSocket(Socket socket, Throwable failure) {
        try {
            socket.close();
        } catch (IOException closeException) {
            failure.addSuppressed(closeException);
        }
    }

    private static void deleteAfterFailure(Path directory, Throwable failure) {
        try {
            FileTrees.deleteRecursively(directory);
        } catch (IOException cleanupException) {
            failure.addSuppressed(cleanupException);
        }
    }
}
