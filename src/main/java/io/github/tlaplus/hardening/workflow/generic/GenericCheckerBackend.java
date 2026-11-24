package io.github.tlaplus.hardening.workflow.generic;

import io.github.tlaplus.hardening.common.ArchRole;
import io.github.tlaplus.hardening.common.Diagnostics;
import io.github.tlaplus.hardening.common.FileTrees;
import io.github.tlaplus.hardening.common.Key;
import io.github.tlaplus.hardening.common.Preconditions;
import io.github.tlaplus.hardening.common.Role;
import io.github.tlaplus.hardening.config.CheckerStageConfig;
import io.github.tlaplus.hardening.config.GenericCheckerConfig;
import io.github.tlaplus.hardening.config.GenericPlaceholder;
import io.github.tlaplus.hardening.corpus.CorpusStage;
import io.github.tlaplus.hardening.workflow.WorkflowException;
import io.github.tlaplus.hardening.workflow.apalache.ApalacheIrJson;
import io.github.tlaplus.hardening.workflow.spec.SpecArtifact;
import io.github.tlaplus.hardening.workflow.spec.SpecText;
import io.github.tlaplus.hardening.workflow.tool.PropertyRunsVerdict;
import io.github.tlaplus.hardening.workflow.tool.ToolBackend;
import io.github.tlaplus.hardening.workflow.tool.ToolWorker;
import io.github.tlaplus.hardening.workflow.worker.OneShotTool;
import io.github.tlaplus.hardening.workflow.worker.StageOutcome;
import io.github.tlaplus.hardening.workflow.worker.StandardModuleResources;
import io.github.tlaplus.hardening.workflow.worker.ToolInput;
import io.github.tlaplus.hardening.workflow.worker.ToolResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Runs the generic checker of ADR 0033 as one native process per input, the lifecycle {@link
 * ToolBackend} describes for TLC, but without a JVM. Each invocation gets a fresh working directory
 * with the module, its configuration file, and the bundled modules every generated module extends;
 * the directory is removed afterwards. A metamorphic module whose properties are checked apart takes
 * up to three invocations, which {@link PropertyRunsVerdict} folds into one result.
 */
@ArchRole(Role.ADAPTER)
@Key
public final class GenericCheckerBackend implements ToolBackend {
    /**
     * The bundled modules a generated module extends and a compatible tool may lack: Manyan resolves
     * neither (ADR 0033, probe P1). Integers is left to the tool.
     */
    static final List<String> BUNDLED_MODULES = List.of("Apalache.tla", "Variants.tla");

    private final CorpusStage stage;
    private final CheckerStageConfig config;
    private final GenericCheckerConfig checker;
    private final GenericConvention convention;
    private final Path scratchDirectory;

    public GenericCheckerBackend(
            CorpusStage stage, CheckerStageConfig config, GenericCheckerConfig checker, Path scratchDirectory) {
        this.stage = Objects.requireNonNull(stage, "stage");
        this.config = Objects.requireNonNull(config, "config");
        Preconditions.requirePositive(config.workers(), "workers");
        this.checker = Objects.requireNonNull(checker, "checker");
        this.convention = GenericConvention.of(checker.compatibility());
        this.scratchDirectory = Objects.requireNonNull(scratchDirectory, "scratchDirectory");
    }

    @Override
    public CorpusStage stage() {
        return stage;
    }

    @Override
    public int workerCount() {
        return config.workers();
    }

    /** One process, which the arguments should limit to one worker of its own. */
    @Override
    public int cpuPermits() {
        return 1;
    }

    /** TLA+ source, or Apalache's JSON IR when the arguments name {@code {ir}}. */
    @Override
    public Function<SpecArtifact, String> renderer() {
        if (checker.names(GenericPlaceholder.IR)) {
            return artifact -> ApalacheIrJson.render(artifact.module());
        }
        return SpecText::render;
    }

    @Override
    public ToolWorker startWorker() {
        return source -> {
            var relation = check(source);
            if (!source.request().kind().hasPropertyRuns()) {
                return relation;
            }
            return PropertyRunsVerdict.fold(relation,
                    kind -> check(new ToolInput(source.text(), source.request().withKind(kind))));
        };
    }

    /** Runs one invocation in a fresh working directory and classifies its exit status. */
    ToolResult check(ToolInput source) throws WorkflowException, InterruptedException {
        Path directory = null;
        try {
            Files.createDirectories(scratchDirectory);
            directory = Files.createTempDirectory(scratchDirectory, "generic-");
            var invocation = new GenericInvocation(directory, source.request());
            write(invocation, source);
            var command = new OneShotTool.Invocation(
                    invocation.command(checker), directory, config.timeout(), checker.name());
            var attempt = OneShotTool.attempt(command);
            if (attempt.timedOut()) {
                return new ToolResult(StageOutcome.CRASH,
                        Diagnostics.append(OneShotTool.timeoutMessage(command), attempt.diagnostics()));
            }
            var status = attempt.exitCode().getAsInt();
            return convention.classifier().classify(status,
                    Diagnostics.append(checker.name() + " exited with status " + status, attempt.diagnostics()));
        } catch (IOException exception) {
            return ToolResult.crash(exception, checker.name() + " could not be run");
        } finally {
            if (directory != null) {
                try {
                    FileTrees.deleteRecursively(directory);
                } catch (IOException ignored) {
                    // The stage's scratch directory is transient, so the run removes what is left.
                }
            }
        }
    }

    private void write(GenericInvocation invocation, ToolInput source) throws IOException {
        var module = checker.names(GenericPlaceholder.IR) ? invocation.ir() : invocation.module();
        Files.writeString(module, source.text(), StandardCharsets.UTF_8);
        Files.writeString(invocation.configuration(), convention.configuration().apply(source.request()),
                StandardCharsets.UTF_8);
        for (var name : BUNDLED_MODULES) {
            try (var resource = GenericCheckerBackend.class.getClassLoader()
                    .getResourceAsStream(StandardModuleResources.PREFIX + name)) {
                if (resource == null) {
                    throw new IOException("missing standard module: " + StandardModuleResources.PREFIX + name);
                }
                Files.copy(resource, invocation.directory().resolve(name));
            }
        }
    }
}
