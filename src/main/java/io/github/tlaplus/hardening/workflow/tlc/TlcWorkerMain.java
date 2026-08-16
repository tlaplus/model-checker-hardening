package io.github.tlaplus.hardening.workflow.tlc;

import io.github.tlaplus.hardening.config.TlcStageConfig;
import io.github.tlaplus.hardening.workflow.worker.StageOutcome;
import io.github.tlaplus.hardening.workflow.worker.StandardModuleResources;
import io.github.tlaplus.hardening.workflow.worker.ToolResult;
import io.github.tlaplus.hardening.workflow.worker.ToolWorkerProtocol;
import io.github.tlaplus.hardening.workflow.worker.WorkerDiagnostics;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
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

/** One-shot TLC child process. Its stdout is reserved exclusively for the protocol. */
public final class TlcWorkerMain {
    static final String WORKERS_PROPERTY = "fuzztla.tlc.workers";

    private TlcWorkerMain() {}

    public static void main(String[] ignoredArguments) {
        try {
            run();
        } catch (Exception | StackOverflowError exception) {
            exception.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static void run() throws IOException {
        var protocolOutput = new DataOutputStream(new BufferedOutputStream(
                new FileOutputStream(FileDescriptor.out)));
        System.setOut(System.err);
        StandardModuleResources.require(
                TlcWorkerMain.class, "Integers.tla", "Apalache.tla", "Variants.tla");
        requireRuntimeDependencies();

        var temporaryDirectory = Files.createTempDirectory("fuzztla-tlc-");
        var specification = temporaryDirectory.resolve("FuzzInput.tla");
        var configuration = temporaryDirectory.resolve("FuzzInput.cfg");
        var metadata = temporaryDirectory.resolve("states");
        Files.writeString(
                configuration,
                "INIT Init\nNEXT Next\nINVARIANT Inv\n",
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE);

        var diagnostics = new ByteArrayOutputStream();
        try (var diagnosticStream = new PrintStream(
                diagnostics, true, StandardCharsets.UTF_8)) {
            System.setOut(diagnosticStream);
            ToolIO.out = diagnosticStream;
            ToolIO.err = diagnosticStream;
            ToolIO.setUserDir(temporaryDirectory.toString());

            var tlc = new TLC();
            tlc.setResolver(new SimpleFilenameToStream(temporaryDirectory.toString()));
            var workers = Integer.getInteger(
                    WORKERS_PROPERTY, TlcStageConfig.DEFAULT_WORKERS);
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

            var protocolInput = new DataInputStream(new BufferedInputStream(System.in));
            ToolWorkerProtocol.writeHandshake(protocolOutput);
            var source = ToolWorkerProtocol.readRequest(protocolInput);
            if (source == null) {
                return;
            }
            Files.writeString(
                    specification,
                    source,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);

            ToolResult result;
            try {
                var errorCode = tlc.process();
                var exitStatus = EC.ExitStatus.errorConstantToExitStatus(errorCode);
                var summary = "TLC error code "
                        + errorCode
                        + " mapped to exit status "
                        + exitStatus;
                result = new ToolResult(
                        TlcOutcomeClassifier.classifyErrorCode(errorCode),
                        WorkerDiagnostics.append(
                                summary, diagnostics.toString(StandardCharsets.UTF_8)));
            } catch (Exception | StackOverflowError exception) {
                result = new ToolResult(
                        StageOutcome.CRASH,
                        WorkerDiagnostics.append(
                                diagnostics.toString(StandardCharsets.UTF_8),
                                WorkerDiagnostics.stackTrace(exception)));
            }
            ToolWorkerProtocol.writeResult(protocolOutput, result);
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
