package io.github.tlaplus.hardening.workflow.library;

import at.forsyte.apalache.io.json.DefaultTagJsonReader;
import at.forsyte.apalache.io.json.ujsonimpl.UJsonRepresentation;
import at.forsyte.apalache.io.json.ujsonimpl.UJsonToTla;
import at.forsyte.apalache.tla.lir.TlaModule;
import io.github.tlaplus.hardening.config.CheckerStageConfig;
import io.github.tlaplus.hardening.workflow.WorkflowException;
import io.github.tlaplus.hardening.workflow.worker.OneShotTool;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import scala.Option;
import ujson.Readable;

/** Finite, isolated Snowcat invocation producing a typed module rather than a checker verdict. */
final class LibraryTypechecker {
    private static final int MAXIMUM_JSON_BYTES = 64 * 1024 * 1024;
    private final Path jar;
    private final Path scratch;
    private final Path toolConfig;
    private final CheckerStageConfig settings;

    LibraryTypechecker(Path jar, Path scratch, CheckerStageConfig settings) throws IOException {
        this.jar = jar;
        this.scratch = scratch;
        this.settings = settings;
        toolConfig = scratch.resolve("apalache.json");
        Files.writeString(toolConfig, "{}");
    }

    TlaModule check(Path source, String module) throws IOException, InterruptedException, WorkflowException {
        var output = scratch.resolve(module + ".json");
        var command = List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Xmx" + settings.maximumHeapMegabytes() + "m",
                "-Duser.home=" + scratch, "-Djava.io.tmpdir=" + scratch,
                "-jar", jar.toString(), "--config-file=" + toolConfig,
                "--out-dir=" + scratch.resolve("out"), "--features=rows",
                "typecheck", "--infer-poly=true", "--output=" + output, source.toString());
        var result = OneShotTool.run(new OneShotTool.Invocation(command, source.getParent(),
                Duration.ofSeconds(settings.timeoutSeconds())));
        if (result.exitCode() != 0) {
            throw new WorkflowException("typechecking custom module " + module + " failed (exit "
                    + result.exitCode() + "):\n" + result.diagnostics());
        }
        var decoded = readTypedModule(output);
        if (!decoded.name().equals(module)) {
            throw new WorkflowException("expected typed module " + module + ", found " + decoded.name());
        }
        return decoded;
    }

    /** The direct reader preserves generic tags, including polymorphic empty collection literals. */
    static TlaModule readTypedModule(Path path) throws IOException {
        if (!Files.isRegularFile(path) || Files.size(path) > MAXIMUM_JSON_BYTES) {
            throw new IOException("missing or oversized typechecker output: " + path);
        }
        var json = ujson.package$.MODULE$.read(Readable.fromString(Files.readString(path)), false);
        var decoded = new UJsonToTla(Option.empty(), DefaultTagJsonReader::apply)
                .fromSingleModule(new UJsonRepresentation(json));
        if (decoded.isFailure()) throw new IOException("invalid typechecker IR: " + decoded);
        return decoded.get();
    }
}
