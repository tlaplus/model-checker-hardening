package io.github.tlaplus.hardening.workflow.apalache;

import io.github.tlaplus.hardening.checker.ExplorationCount;
import io.github.tlaplus.hardening.checker.ExplorationMetrics;
import io.github.tlaplus.hardening.common.FileTrees;
import io.github.tlaplus.hardening.workflow.spec.FuzzInputModule;
import io.github.tlaplus.hardening.workflow.worker.CheckRequest;
import io.github.tlaplus.hardening.workflow.worker.StageOutcome;
import io.github.tlaplus.hardening.workflow.worker.ToolResult;
import io.github.tlaplus.hardening.workflow.worker.ToolWorkerConnection;
import io.github.tlaplus.hardening.workflow.worker.ToolWorkerRuntime;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import scala.Console$;

/** Persistent child process that invokes Apalache's {@code Tool.run} sequentially. */
public final class ApalacheWorkerMain {
    private static final String SPECIFICATION_FILE = FuzzInputModule.MODULE_NAME + ".json";
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
                            ToolWorkerRuntime.writeInput(specification, source);
                            var result =
                                    check(
                                            toolRun,
                                            jobDirectory,
                                            specification,
                                            source.request(),
                                            processError);

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
            CheckRequest request,
            PrintStream processError) {
        var diagnostics = ToolWorkerRuntime.diagnosticBuffer("Apalache");
        try (var diagnosticStream = new PrintStream(
                diagnostics, true, StandardCharsets.UTF_8)) {
            System.setOut(diagnosticStream);
            System.setErr(diagnosticStream);
            setScalaConsole(diagnosticStream);
            try {
                var exitStatus = (int) toolRun.invoke(
                        null, (Object) ApalacheArguments.check(jobDirectory, specification, request));
                diagnosticStream.flush();
                return withTraceLength(
                        ApalacheOutcomeClassifier.classify(exitStatus, diagnostics.text()), jobDirectory);
            } catch (Exception | StackOverflowError exception) {
                diagnosticStream.flush();
                return ToolResult.crash(exception, diagnostics.text());
            } finally {
                System.setOut(processError);
                System.setErr(processError);
                setScalaConsole(processError);
            }
        }
    }

    /** Attaches the counterexample's length, which Apalache wrote below the job's output directory. */
    private static ToolResult withTraceLength(ToolResult result, Path jobDirectory) {
        if (result.outcome() != StageOutcome.COUNTEREXAMPLE) {
            return result;
        }
        var length = ApalacheTraceLength.read(ApalacheArguments.outputDirectory(jobDirectory));
        return length.isEmpty()
                ? result
                : result.withMetrics(ExplorationMetrics.builder()
                        .count(ExplorationCount.TRACE_LENGTH, length.getAsLong())
                        .build());
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
