package io.github.tlaplus.hardening.workflow.parser;

import io.github.tlaplus.hardening.workflow.worker.StageOutcome;
import io.github.tlaplus.hardening.workflow.worker.StandardModuleResources;
import io.github.tlaplus.hardening.workflow.worker.ToolResult;
import io.github.tlaplus.hardening.workflow.worker.ToolWorkerConnection;
import io.github.tlaplus.hardening.workflow.worker.ToolWorkerProtocol;
import io.github.tlaplus.hardening.workflow.worker.WorkerDiagnostics;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import tla2sany.drivers.SANY;
import tla2sany.drivers.SanySettings;
import tla2sany.modanalyzer.SpecObj;
import tla2sany.output.LogLevel;
import tla2sany.output.SimpleSanyOutput;
import util.SimpleFilenameToStream;
import util.ToolIO;

/** Child-process entry point for persistent isolated SANY requests. */
public final class ParserWorkerMain {
    private ParserWorkerMain() {}

    static void main(String[] ignoredArguments) {
        try {
            run();
        } catch (Exception | StackOverflowError exception) {
            exception.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static void run() throws IOException {
        var connection = ToolWorkerConnection.connect();
        var protocolOutput = connection.output();
        var protocolInput = connection.input();
        System.setOut(System.err);
        StandardModuleResources.require(
                ParserWorkerMain.class, "Integers.tla", "Apalache.tla", "Variants.tla");

        var temporaryDirectory = Files.createTempDirectory("fuzztla-sany-");
        var specification = temporaryDirectory.resolve("FuzzInput.tla");
        ToolIO.setUserDir(temporaryDirectory.toString());
        var resolver = new SimpleFilenameToStream(temporaryDirectory.toString());

        ToolWorkerProtocol.writeHandshake(protocolOutput);
        try {
            while (true) {
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
                var result = parse(specification, resolver);
                ToolWorkerProtocol.writeResult(protocolOutput, result);
                if (result.outcome() == StageOutcome.CRASH) {
                    return;
                }
            }
        } finally {
            Files.deleteIfExists(specification);
            Files.deleteIfExists(temporaryDirectory);
            connection.close();
        }
    }

    private static ToolResult parse(
            Path specification, SimpleFilenameToStream resolver) {
        var diagnostics = new ByteArrayOutputStream();
        try (var stream = new PrintStream(diagnostics, true, StandardCharsets.UTF_8)) {
            var output = new SimpleSanyOutput(stream, LogLevel.INFO);
            var specObj = new SpecObj(specification.toString(), resolver);
            var exitCode = SANY.parse(
                    specObj,
                    specification.toString(),
                    output,
                    SanySettings.defaultSettings());
            var diagnostic = diagnostics.toString(StandardCharsets.UTF_8);
            return switch (exitCode) {
                case OK -> new ToolResult(StageOutcome.PASS, diagnostic);
                case SYNTAX_PARSING_FAILURE,
                                SEMANTIC_ANALYSIS_OR_LEVEL_CHECKING_FAILURE,
                                ERROR ->
                        new ToolResult(StageOutcome.FAIL, diagnostic);
            };
        } catch (Exception | StackOverflowError exception) {
            return new ToolResult(
                    StageOutcome.CRASH,
                    WorkerDiagnostics.append(
                            diagnostics.toString(StandardCharsets.UTF_8),
                            WorkerDiagnostics.stackTrace(exception)));
        }
    }

    static String stackTrace(Throwable exception) {
        return WorkerDiagnostics.stackTrace(exception);
    }

}
