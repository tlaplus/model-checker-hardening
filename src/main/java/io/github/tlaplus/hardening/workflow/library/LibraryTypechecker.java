package io.github.tlaplus.hardening.workflow.library;

import at.forsyte.apalache.tla.lir.TlaModule;
import io.github.tlaplus.hardening.config.CheckerStageConfig;
import io.github.tlaplus.hardening.workflow.WorkflowException;
import io.github.tlaplus.hardening.workflow.apalache.ApalacheIrJson;
import io.github.tlaplus.hardening.workflow.worker.JavaLaunch;
import io.github.tlaplus.hardening.workflow.worker.OneShotTool;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

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
                JavaLaunch.executable(),
                JavaLaunch.maximumHeap(settings.maximumHeapMegabytes()),
                "-Duser.home=" + scratch, "-Djava.io.tmpdir=" + scratch,
                "-jar", jar.toString(), "--config-file=" + toolConfig,
                "--out-dir=" + scratch.resolve("out"), "--features=rows",
                "typecheck", "--infer-poly=true", "--output=" + output, source.toString());
        var result = OneShotTool.run(new OneShotTool.Invocation(command, source.getParent(),
                Duration.ofSeconds(settings.timeoutSeconds()), "typechecking custom module " + module));
        if (result.exitCode() != 0) {
            throw new WorkflowException("typechecking custom module " + module + " failed (exit "
                    + result.exitCode() + "):\n" + result.diagnostics());
        }
        var decoded = ApalacheIrJson.parse(output, MAXIMUM_JSON_BYTES);
        if (!decoded.name().equals(module)) {
            throw new WorkflowException("expected typed module " + module + ", found " + decoded.name());
        }
        return decoded;
    }
}
