package io.github.tlaplus.hardening.workflow;

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
import tla2sany.drivers.SANY;
import tla2sany.drivers.SanySettings;
import tla2sany.modanalyzer.SpecObj;
import tla2sany.output.LogLevel;
import tla2sany.output.SimpleSanyOutput;
import util.SimpleFilenameToStream;
import util.ToolIO;

/** Child-process entry point. Its stdout is reserved exclusively for the binary protocol. */
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
        var protocolOutput = new DataOutputStream(new BufferedOutputStream(
                new FileOutputStream(FileDescriptor.out)));
        System.setOut(System.err);
        requireStandardModule("Integers.tla");
        requireStandardModule("Apalache.tla");
        requireStandardModule("Variants.tla");

        var temporaryDirectory = Files.createTempDirectory("fuzztla-sany-");
        var specification = temporaryDirectory.resolve("FuzzInput.tla");
        ToolIO.setUserDir(temporaryDirectory.toString());
        var protocolInput = new DataInputStream(new BufferedInputStream(System.in));

        protocolOutput.writeInt(ParserWorkerProtocol.MAGIC);
        protocolOutput.writeInt(ParserWorkerProtocol.VERSION);
        protocolOutput.flush();
        try {
            while (true) {
                var length = protocolInput.readInt();
                if (length == ParserWorkerProtocol.STOP) {
                    return;
                }
                if (length < 0 || length > ParserWorkerProtocol.MAXIMUM_MESSAGE_BYTES) {
                    throw new IOException("invalid parser request length: " + length);
                }
                var bytes = protocolInput.readNBytes(length);
                if (bytes.length != length) {
                    throw new IOException("truncated parser request");
                }
                Files.writeString(
                        specification,
                        new String(bytes, StandardCharsets.UTF_8),
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE);
                var result = parse(specification, temporaryDirectory);
                writeResult(protocolOutput, result);
                if (result.outcome() == ParserResult.Outcome.CRASH) {
                    return;
                }
            }
        } finally {
            Files.deleteIfExists(specification);
            Files.deleteIfExists(temporaryDirectory);
        }
    }

    private static ParserResult parse(Path specification, Path libraryDirectory) {
        var diagnostics = new ByteArrayOutputStream();
        try (var stream = new PrintStream(diagnostics, true, StandardCharsets.UTF_8)) {
            var output = new SimpleSanyOutput(stream, LogLevel.INFO);
            var resolver = new SimpleFilenameToStream(libraryDirectory.toString());
            var specObj = new SpecObj(specification.toString(), resolver);
            var exitCode = SANY.parse(
                    specObj,
                    specification.toString(),
                    output,
                    SanySettings.defaultSettings());
            var diagnostic = diagnostics.toString(StandardCharsets.UTF_8);
            return switch (exitCode) {
                case OK -> new ParserResult(ParserResult.Outcome.PASS, diagnostic);
                case SYNTAX_PARSING_FAILURE,
                                SEMANTIC_ANALYSIS_OR_LEVEL_CHECKING_FAILURE ->
                        new ParserResult(ParserResult.Outcome.FAIL, diagnostic);
                case ERROR -> new ParserResult(ParserResult.Outcome.CRASH, diagnostic);
            };
        } catch (Exception | StackOverflowError exception) {
            var message = exception.getMessage();
            return new ParserResult(
                    ParserResult.Outcome.CRASH,
                    message == null || message.isBlank()
                            ? exception.getClass().getSimpleName()
                            : message);
        }
    }

    private static void writeResult(DataOutputStream output, ParserResult result)
            throws IOException {
        var diagnostic = result.diagnostic().getBytes(StandardCharsets.UTF_8);
        if (diagnostic.length > ParserWorkerProtocol.MAXIMUM_DIAGNOSTIC_BYTES) {
            diagnostic = java.util.Arrays.copyOf(
                    diagnostic, ParserWorkerProtocol.MAXIMUM_DIAGNOSTIC_BYTES);
        }
        output.writeInt(result.outcome().protocolCode());
        output.writeInt(diagnostic.length);
        output.write(diagnostic);
        output.flush();
    }

    private static void requireStandardModule(String name) throws IOException {
        var resource = "tla2sany/StandardModules/" + name;
        if (ParserWorkerMain.class.getClassLoader().getResource(resource) == null) {
            throw new IOException("missing parser standard module: " + resource);
        }
    }
}
