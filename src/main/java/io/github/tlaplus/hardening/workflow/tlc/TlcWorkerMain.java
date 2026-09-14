package io.github.tlaplus.hardening.workflow.tlc;

import io.github.tlaplus.hardening.config.CheckerProfile;
import io.github.tlaplus.hardening.workflow.spec.FuzzInputModule;
import io.github.tlaplus.hardening.workflow.worker.BoundedTextOutputStream;
import io.github.tlaplus.hardening.workflow.worker.StandardModuleResources;
import io.github.tlaplus.hardening.workflow.worker.ToolResult;
import io.github.tlaplus.hardening.workflow.worker.ToolWorkerConnection;
import io.github.tlaplus.hardening.workflow.worker.ToolWorkerRuntime;
import io.github.tlaplus.hardening.workflow.worker.WorkerDiagnostics;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import tlc2.TLC;
import tlc2.output.EC;
import util.SimpleFilenameToStream;
import util.ToolIO;

/** One-shot TLC child process. */
public final class TlcWorkerMain {
    static final String WORKERS_PROPERTY = "fuzztla.tlc.workers";

    private TlcWorkerMain() {}

    public static void main(String[] ignoredArguments) {
        ToolWorkerRuntime.main(TlcWorkerMain::run, System.err);
    }

    private static void run() throws Exception {
        var connection = ToolWorkerConnection.connect();
        System.setOut(System.err);
        StandardModuleResources.requireBundled(TlcWorkerMain.class);
        requireRuntimeDependencies();

        var temporaryDirectory = Files.createTempDirectory("fuzztla-tlc-");
        var specification =
                temporaryDirectory.resolve(FuzzInputModule.MODULE_NAME + ".tla");
        var configuration =
                temporaryDirectory.resolve(FuzzInputModule.MODULE_NAME + ".cfg");
        var metadata = temporaryDirectory.resolve("states");

        var diagnostics = ToolWorkerRuntime.diagnosticBuffer("TLC");
        try (connection;
                var diagnosticStream = new PrintStream(
                        diagnostics, true, StandardCharsets.UTF_8)) {
            System.setOut(diagnosticStream);
            ToolIO.out = diagnosticStream;
            ToolIO.err = diagnosticStream;
            ToolIO.setUserDir(temporaryDirectory.toString());

            var tlc = new TLC();
            tlc.setResolver(new SimpleFilenameToStream(temporaryDirectory.toString()));
            var workers = Integer.getInteger(
                    WORKERS_PROPERTY, CheckerProfile.TLC.defaults().workers());
            var specBase = withoutExtension(specification);
            var configBase = withoutExtension(configuration);
            var arguments = new String[] {
                "-workers", Integer.toString(workers),
                "-deadlock",
                "-noGenerateSpecTE",
                "-metadir", metadata.toString(),
                "-config", configBase,
                specBase
            };
            if (!tlc.handleParameters(arguments)) {
                throw new IOException("TLC rejected the fixed worker parameters");
            }

            ToolWorkerRuntime.serve(
                    connection,
                    ToolWorkerRuntime.Lifetime.ONE_INPUT,
                    source -> {
                        ToolWorkerRuntime.writeInput(specification, source);
                        // TLC reads the configuration when it processes the input, not when it
                        // accepts its parameters, so the file can follow the request.
                        Files.writeString(
                                configuration,
                                TlcConfiguration.text(source.request()),
                                StandardCharsets.UTF_8,
                                StandardOpenOption.CREATE_NEW,
                                StandardOpenOption.WRITE);
                        return check(tlc, diagnostics);
                    });
        }
    }

    /** Checks the written specification and classifies whatever TLC reports. */
    private static ToolResult check(TLC tlc, BoundedTextOutputStream diagnostics) {
        try {
            var errorCode = tlc.process();
            var exitStatus = EC.ExitStatus.errorConstantToExitStatus(errorCode);
            var summary =
                    "TLC error code " + errorCode + " mapped to exit status " + exitStatus;
            return TlcOutcomeClassifier.classifyErrorCode(
                    errorCode, WorkerDiagnostics.append(summary, diagnostics.text()));
        } catch (Exception | StackOverflowError exception) {
            return ToolResult.crash(exception, diagnostics.text());
        }
    }

    private static String withoutExtension(Path path) {
        var value = path.toString();
        var dot = value.lastIndexOf('.');
        return dot < 0 ? value : value.substring(0, dot);
    }

    private static void requireRuntimeDependencies() throws IOException {
        try {
            Class.forName("com.google.gson.JsonElement");
        } catch (ClassNotFoundException exception) {
            throw new IOException("missing TLC runtime dependency: Gson", exception);
        }
    }
}
