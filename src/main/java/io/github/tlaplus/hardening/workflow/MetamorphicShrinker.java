package io.github.tlaplus.hardening.workflow;

import io.github.tlaplus.hardening.config.FuzzTlaConfig;
import io.github.tlaplus.hardening.corpus.CorpusDirectory;
import io.github.tlaplus.hardening.corpus.CorpusException;
import io.github.tlaplus.hardening.corpus.CorpusInput;
import io.github.tlaplus.hardening.corpus.Technique;
import io.github.tlaplus.hardening.gen.InputKind;
import io.github.tlaplus.hardening.gen.InputRejectedException;
import io.github.tlaplus.hardening.gen.rewrite.MetamorphicPayload;
import io.github.tlaplus.hardening.workflow.apalache.ApalacheDistribution;
import io.github.tlaplus.hardening.workflow.spec.SpecArtifact;
import io.github.tlaplus.hardening.workflow.spec.SpecDecoders;
import io.github.tlaplus.hardening.workflow.tool.ToolBackend;
import io.github.tlaplus.hardening.workflow.tool.ToolWorker;
import io.github.tlaplus.hardening.workflow.worker.StageOutcome;
import io.github.tlaplus.hardening.workflow.worker.ToolInput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Reduces a metamorphic counterexample for triage (ADR 0016 §8). A rewrite marker is the low bit of
 * a byte of the rewrite payload, so the shrinker clears the low bit of each odd byte of that payload
 * in turn, and keeps the change whenever the input still violates the relation. It repeats until a
 * pass keeps nothing. The base payload is never touched, so the base module stays the same.
 */
public final class MetamorphicShrinker {
    /** Whether an input still violates the metamorphic relation. */
    @FunctionalInterface
    public interface Violation {
        boolean holds(byte[] input) throws WorkflowException, InterruptedException;
    }

    /**
     * A shrunk input.
     *
     * @param checks how many candidates were checked
     * @param cleared how many low bits were cleared
     */
    public record Result(byte[] input, int checks, int cleared) {
        public Result {
            input = Objects.requireNonNull(input, "input").clone();
        }

        @Override
        public byte[] input() {
            return input.clone();
        }
    }

    private MetamorphicShrinker() {}

    /** Shrinks {@code input}, which must violate the relation, against {@code violation}. */
    public static Result shrink(byte[] input, Violation violation) throws WorkflowException, InterruptedException {
        Objects.requireNonNull(violation, "violation");
        var current = Objects.requireNonNull(input, "input").clone();
        var rewrite = MetamorphicPayload.rewriteOffset(current);
        var checks = 0;
        var cleared = 0;
        var changed = true;
        while (changed) {
            changed = false;
            for (var offset = rewrite; offset < current.length; offset++) {
                if ((current[offset] & 1) == 0) {
                    continue;
                }
                var candidate = current.clone();
                candidate[offset] &= (byte) ~1;
                checks++;
                if (violation.holds(candidate)) {
                    current = candidate;
                    cleared++;
                    changed = true;
                }
            }
        }
        return new Result(current, checks, cleared);
    }

    /**
     * Shrinks a metamorphic entry of {@code corpus} against the checkers its configuration runs,
     * holding the corpus lock so no run shares the checkers' scratch directories.
     */
    public static Result shrink(CorpusDirectory corpus, FuzzTlaConfig config, CorpusInput entry, int maximumCpus)
            throws IOException, CorpusException, WorkflowException, InterruptedException {
        var decoders = CorpusReplay.decoders(corpus, config);
        if (CorpusRecords.TECHNIQUE.read(corpus) != Technique.MT) {
            throw new WorkflowException("only a metamorphic corpus can be shrunk; this one runs --how="
                    + CorpusRecords.TECHNIQUE.read(corpus).encodedName());
        }
        try (var lock = corpus.acquireExclusiveLock(); var scratch = corpus.createScratch()) {
            var resources = new CheckerBackends.Resources(maximumCpus, scratch, ApalacheDistribution.locate(),
                    config.libraries().sourceCheckerClasspath());
            var checkers = new ArrayList<Checker>();
            try {
                for (var stage : config.workflow().enabledCheckers()) {
                    var backend = CheckerBackends.create(stage, config.workflow().checker(stage), resources);
                    checkers.add(new Checker(backend, backend.startWorker()));
                }
                var violation = violation(decoders, entry.kind(), checkers);
                if (!violation.holds(entry.input())) {
                    throw new WorkflowException("the entry does not violate the metamorphic relation");
                }
                return shrink(entry.input(), violation);
            } finally {
                checkers.forEach(checker -> checker.worker().close());
            }
        }
    }

    /** One running checker and how it renders a module. */
    record Checker(ToolBackend backend, ToolWorker worker) {}

    /** An input violates the relation when it decodes to a rewrite and some checker finds a counterexample. */
    static Violation violation(SpecDecoders decoders, InputKind kind, List<Checker> checkers) {
        return input -> {
            final SpecArtifact artifact;
            try {
                artifact = decoders.decode(new CorpusInput(kind, input));
            } catch (InputRejectedException rejected) {
                return false;
            }
            for (var checker : checkers) {
                var result = checker.worker().check(
                        new ToolInput(checker.backend().renderer().apply(artifact), artifact.request()));
                if (result.outcome() == StageOutcome.COUNTEREXAMPLE) {
                    return true;
                }
            }
            return false;
        };
    }
}
