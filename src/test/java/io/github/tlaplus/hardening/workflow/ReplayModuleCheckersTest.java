package io.github.tlaplus.hardening.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.tlaplus.hardening.checker.ExplorationCount;
import io.github.tlaplus.hardening.config.CheckerStageConfig;
import io.github.tlaplus.hardening.config.SeedConfig;
import io.github.tlaplus.hardening.config.ToolJar;
import io.github.tlaplus.hardening.corpus.CorpusDirectory;
import io.github.tlaplus.hardening.corpus.CorpusInput;
import io.github.tlaplus.hardening.corpus.CorpusStage;
import io.github.tlaplus.hardening.gen.IrGenerationConfig;
import io.github.tlaplus.hardening.gen.trace.TraceEnding;
import io.github.tlaplus.hardening.workflow.spec.ReplayFixtures;
import io.github.tlaplus.hardening.workflow.spec.SpecDecoders;
import io.github.tlaplus.hardening.workflow.worker.StageOutcome;
import io.github.tlaplus.hardening.workflow.worker.ToolInput;
import java.nio.file.Path;
import java.util.List;
import java.util.OptionalLong;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Runs TLC and Apalache on replay modules (ADR 0034 §3, §4): a trace that is a behavior and whose
 * last state is what it claims yields a counterexample of exactly its transitions, and any other
 * trace a pass.
 */
class ReplayModuleCheckersTest {
    private static final CheckerStageConfig CONFIG = new CheckerStageConfig(10, 120, 1024, 1);
    private static final List<CorpusStage> CHECKERS = List.of(CorpusStage.TLC, CorpusStage.APALACHE);

    /** A replayed trace and the outcome every checker must report on it. */
    private record Case(String name, CorpusInput entry, StageOutcome outcome, OptionalLong length) {
        static Case counterexample(String name, CorpusInput entry, long length) {
            return new Case(name, entry, StageOutcome.COUNTEREXAMPLE, OptionalLong.of(length));
        }

        static Case pass(String name, CorpusInput entry) {
            return new Case(name, entry, StageOutcome.PASS, OptionalLong.empty());
        }
    }

    @Test
    void theCheckersConfirmBehaviorsAndRefuteTheRest(@TempDir Path directory) throws Exception {
        var invariant = new TraceEnding.Invariant();
        var prefix = new TraceEnding.Prefix();
        var cases = List.of(
                Case.counterexample("an initial violation", ReplayFixtures.module(invariant, false, 0), 0),
                Case.counterexample("a prefix", ReplayFixtures.module(prefix, false, 0, false, 1, false, 2), 2),
                Case.counterexample("a stuttering lasso",
                        ReplayFixtures.module(new TraceEnding.Loop(1), false, 0, false, 1, false, 1), 2),
                Case.counterexample("a path to the graph's target", ReplayFixtures.graph(invariant, 10), 10),
                Case.pass("an initial state that Init excludes", ReplayFixtures.module(prefix, true, 0)),
                Case.pass("a transition that Next excludes", ReplayFixtures.module(prefix, false, 0, true, 1)),
                Case.pass("a last state that satisfies Inv", ReplayFixtures.graph(invariant, 9)),
                Case.pass("a skipped graph node", ReplayFixtures.graph(prefix, 2, 0, 2, 3)),
                Case.pass("a lasso whose last state is not its loop state",
                        ReplayFixtures.module(new TraceEnding.Loop(0), false, 0, false, 1, false, 2)));
        var decoders = SpecDecoders.of(IrGenerationConfig.defaults());
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"), "");
        try (var lock = corpus.acquireExclusiveLock(); var scratch = corpus.createScratch()) {
            var tools = new CheckerBackends.Tools(StagedTools.locate(ToolJar.APALACHE), List.of(), Optional.empty());
            var resources = new CheckerBackends.Resources(1, scratch, tools, SeedConfig.derive(0));
            for (var stage : CHECKERS) {
                var backend = CheckerBackends.create(stage, CONFIG, resources);
                try (var worker = backend.startWorker()) {
                    for (var replay : cases) {
                        var artifact = decoders.decode(replay.entry());
                        var result = worker.check(new ToolInput(backend.renderer().apply(artifact), artifact.request()));
                        var context = stage.displayName() + " on " + replay.name() + ": " + result.diagnostic();
                        assertEquals(replay.outcome(), result.outcome(), context);
                        if (replay.length().isPresent()) {
                            assertEquals(replay.length(), result.metrics().orElseThrow()
                                    .count(ExplorationCount.TRACE_LENGTH), context);
                        }
                    }
                }
            }
        }
    }
}
