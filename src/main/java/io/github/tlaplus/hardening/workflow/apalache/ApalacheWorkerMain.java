package io.github.tlaplus.hardening.workflow.apalache;

import io.github.tlaplus.hardening.common.FileTrees;
import io.github.tlaplus.hardening.workflow.worker.BoundedTextOutputStream;
import io.github.tlaplus.hardening.workflow.worker.StageOutcome;
import io.github.tlaplus.hardening.workflow.worker.ToolResult;
import io.github.tlaplus.hardening.workflow.worker.ToolWorkerConnection;
import io.github.tlaplus.hardening.workflow.worker.ToolWorkerRuntime;
import io.github.tlaplus.hardening.workflow.worker.WorkerDiagnostics;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import scala.Console$;

/** Persistent child process that invokes Apalache's {@code Tool.run} sequentially. */
public final class ApalacheWorkerMain {
    private static final int MAXIMUM_OUTPUT_BYTES = 1024 * 1024 - 128;
    private static final String SPECIFICATION_FILE = "FuzzInput.tla";
    private static final String TOOL_CLASS = "at.forsyte.apalache.tla.Tool";

    private ApalacheWorkerMain() {}

    public static void main(String[] ignoredArguments) {
        var processError = System.err;
        ToolWorkerRuntime.main(() -> run(processError), processError);
    }

    private static void run(PrintStream processError) throws Exception {
        try (var connection = ToolWorkerConnection.connect()) {
            System.setOut(processError);
            var workerDirectory = Path.of(System.getProperty("java.io.tmpdir"))
                    .toAbsolutePath()
                    .normalize();
            System.setProperty("user.home", workerDirectory.toString());
            setScalaConsole(processError);
            var toolRun = resolveToolRun();
            var cleanupPending = new ArrayList<Path>();

            try {
                ToolWorkerRuntime.serve(
                        connection,
                        ToolWorkerRuntime.Lifetime.UNTIL_CRASH,
                        source -> {
                            var jobDirectory =
                                    Files.createTempDirectory(workerDirectory, "job-");
                            var specification = jobDirectory.resolve(SPECIFICATION_FILE);
                            Files.writeString(
                                    specification,
                                    source,
                                    StandardCharsets.UTF_8,
                                    StandardOpenOption.CREATE_NEW,
                                    StandardOpenOption.WRITE);
                            var result =
                                    check(toolRun, jobDirectory, specification, processError);

                            // Tool.run resets Logback at the beginning of every invocation.
                            // Once the current invocation returns, files retained by the
                            // previous one are closed.
                            deletePending(cleanupPending);
                            cleanupPending.add(jobDirectory);
                            return result;
                        });
            } finally {
                System.setOut(processError);
                System.setErr(processError);
            }
        }
    }

    private static ToolResult check(
            Method toolRun,
            Path jobDirectory,
            Path specification,
            PrintStream processError) {
        var diagnostics =
                new BoundedTextOutputStream(MAXIMUM_OUTPUT_BYTES, "Apalache output");
        try (var diagnosticStream = new PrintStream(
                diagnostics, true, StandardCharsets.UTF_8)) {
            System.setOut(diagnosticStream);
            System.setErr(diagnosticStream);
            setScalaConsole(diagnosticStream);
            try {
                var exitStatus = (int) toolRun.invoke(
                        null, (Object) arguments(jobDirectory, specification));
                diagnosticStream.flush();
                return ApalacheOutcomeClassifier.classify(exitStatus, diagnostics.text());
            } catch (Exception | StackOverflowError exception) {
                diagnosticStream.flush();
                return new ToolResult(
                        StageOutcome.CRASH,
                        WorkerDiagnostics.append(
                                diagnostics.text(), WorkerDiagnostics.stackTrace(exception)));
            } finally {
                System.setOut(processError);
                System.setErr(processError);
                setScalaConsole(processError);
            }
        }
    }

    private static void setScalaConsole(PrintStream stream) {
        Console$.MODULE$.setOutDirect(stream);
        Console$.MODULE$.setErrDirect(stream);
    }

    private static Method resolveToolRun() throws ReflectiveOperationException {
        var method = Class.forName(TOOL_CLASS).getMethod("run", String[].class);
        if (!Modifier.isStatic(method.getModifiers()) || method.getReturnType() != int.class) {
            throw new NoSuchMethodException(TOOL_CLASS + ".run(String[]) must return int");
        }
        return method;
    }

    private static String[] arguments(Path jobDirectory, Path specification) {
        return new String[] {
            "--out-dir=" + jobDirectory.resolve("out"),
            "check",
            "--init=Init",
            "--next=Next",
            "--inv=Inv",
            "--length=0",
            "--no-deadlock",
            specification.toString()
        };
    }

    private static void deletePending(ArrayList<Path> pending) {
        pending.removeIf(path -> {
            try {
                FileTrees.deleteRecursively(path);
                return true;
            } catch (IOException ignored) {
                // The parent removes the complete worker directory after the JVM exits.
                return false;
            }
        });
    }
}
