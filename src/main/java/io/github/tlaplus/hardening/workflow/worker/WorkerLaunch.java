package io.github.tlaplus.hardening.workflow.worker;

import static io.github.tlaplus.hardening.common.Cleanup.suppressIOException;
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
            suppressIOException(exception, () -> FileTrees.deleteRecursively(temporaryDirectory));
            throw new WorkflowException(
                    "cannot create " + description + " protocol listener", exception);
        }

        var token = UUID.randomUUID().toString();
        final Process process;
        try {
            process = new ProcessBuilder(command(spec, temporaryDirectory, listener, token))
                    .start();
        } catch (IOException exception) {
            suppressIOException(exception, listener::close);
            suppressIOException(exception, () -> FileTrees.deleteRecursively(temporaryDirectory));
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
        } catch (TimeoutException | ExecutionException | InterruptedException exception) {
            suppressIOException(exception, listener::close);
            throw startupFailure(process, output, exception, description,
                    description + " did not connect within " + timeout);
        }
        try {
            listener.close();
        } catch (IOException exception) {
            suppressIOException(exception, socket::close);
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
            suppressIOException(exception, socket::close);
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
        } catch (TimeoutException | ExecutionException | InterruptedException exception) {
            channel.close();
            throw startupFailure(process, output, exception, description,
                    description + " did not start within " + timeout);
        }
    }

    /** Both startup waits retire the child identically, but keep their phase-specific timeout. */
    private static WorkflowException startupFailure(
            Process process, WorkerOutput output, Exception failure, String description, String timeout)
            throws InterruptedException {
        if (failure instanceof InterruptedException interrupted) {
            terminate(process);
            output.close();
            throw interrupted;
        }
        return abandon(process, output,
                failure instanceof TimeoutException ? timeout : description + " failed during startup",
                failure instanceof ExecutionException execution ? execution.getCause() : failure);
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
        command.add(JavaLaunch.executable());
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
        stop(process, false);
    }

    /** Waits for a child that was asked to stop, escalating if it does not. */
    static void awaitStop(Process process) {
        stop(process, true);
    }

    private static void stop(Process process, boolean graceful) {
        try {
            if (!graceful || !process.waitFor(
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

}
