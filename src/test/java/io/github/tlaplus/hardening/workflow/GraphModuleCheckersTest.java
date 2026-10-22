package io.github.tlaplus.hardening.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tlaplus.hardening.checker.ExplorationCount;
import io.github.tlaplus.hardening.config.CheckerStageConfig;
import io.github.tlaplus.hardening.corpus.CorpusDirectory;
import io.github.tlaplus.hardening.corpus.CorpusStage;
import io.github.tlaplus.hardening.gen.InputRejectedException;
import io.github.tlaplus.hardening.gen.IrGenerationConfig;
import io.github.tlaplus.hardening.gen.IrGenerators;
import io.github.tlaplus.hardening.gen.graph.GeneratedGraph;
import io.github.tlaplus.hardening.gen.library.OperatorLibrary;
import io.github.tlaplus.hardening.signature.KnownDefectDatabase;
import io.github.tlaplus.hardening.workflow.apalache.ApalacheDistribution;
import io.github.tlaplus.hardening.workflow.spec.SpecArtifact;
import io.github.tlaplus.hardening.workflow.worker.StageOutcome;
import io.github.tlaplus.hardening.workflow.worker.ToolInput;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Runs TLC and Apalache on decoded graph modules and holds each to the outcome its graph predicts
 * (ADR 0023 §4, §5): the predicted verdict or a failure, and a counterexample at least as long as
 * the shortest path to the target, exactly as long from a checker whose
 * {@link CorpusStage#counterexampleSearch()} is {@code SHORTEST}, as both are. This pins those
 * facts on the checkers themselves; a 600-entry probe campaign measured them first.
 */
class GraphModuleCheckersTest {
    /** Apalache type-checks a module in seconds; the bound only has to catch a hang. */
    private static final CheckerStageConfig CONFIG = new CheckerStageConfig(10, 120, 1024, 1);
    private static final List<CorpusStage> CHECKERS = List.of(CorpusStage.TLC, CorpusStage.APALACHE);
    /**
     * The open finding findings/TLC/tlc-002.md: TLC reports an evaluation error raised in a
     * standard-module operator, such as {@code x % 0} in Next, under a TLC_MODULE_* constant
     * that exits 255, which the workflow records as a crash. Deep spines reach such errors often.
     */
    private static final java.util.regex.Pattern KNOWN_TLC_CRASH =
            java.util.regex.Pattern.compile("TLC error code 21(69|7\\d|8\\d) mapped to exit status 255");
    /** The shortest distance of a sampled reachable target, so the checkers unroll deep spines. */
    private static final int DEEP = 10;

    @Test
    void theCheckersReportTheOutcomeTheGraphPredicts(@TempDir Path directory) throws Exception {
        var samples = samples(4, 3);
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"), "");
        var counterexamples = 0;
        try (var lock = corpus.acquireExclusiveLock(); var scratch = corpus.createScratch()) {
            var resources = new CheckerBackends.Resources(1, scratch, ApalacheDistribution.locate(), List.of());
            for (var stage : CHECKERS) {
                var backend = CheckerBackends.create(stage, CONFIG, resources);
                try (var worker = backend.startWorker()) {
                    for (var graph : samples) {
                        var artifact = SpecArtifact.fromGeneratedGraph(graph, OperatorLibrary.empty());
                        var result = worker.check(new ToolInput(backend.renderer().apply(artifact), artifact.request()));
                        var context = stage.displayName() + " on " + graph.graph() + " with target " + graph.target()
                                + ": " + result.diagnostic();
                        if (result.outcome() == StageOutcome.CRASH && stage == CorpusStage.TLC
                                && KNOWN_TLC_CRASH.matcher(result.diagnostic()).find()) {
                            continue;
                        }
                        assertNotEquals(StageOutcome.CRASH, result.outcome(), context);
                        if (result.outcome() == StageOutcome.FAIL) {
                            continue;
                        }
                        var distance = graph.targetDistance();
                        assertEquals(distance.isPresent() ? StageOutcome.COUNTEREXAMPLE : StageOutcome.PASS,
                                result.outcome(), context);
                        if (distance.isEmpty()) {
                            continue;
                        }
                        counterexamples++;
                        var length = result.metrics().orElseThrow(() -> new AssertionError("no metrics: " + context))
                                .count(ExplorationCount.TRACE_LENGTH);
                        assertTrue(length.isPresent(), "no trace length: " + context);
                        if (stage.counterexampleSearch() == CorpusStage.CounterexampleSearch.SHORTEST) {
                            assertEquals(distance.getAsInt(), length.getAsLong(), context);
                        } else {
                            assertTrue(length.getAsLong() >= distance.getAsInt(), context);
                        }
                    }
                }
            }
        }
        assertTrue(counterexamples >= CHECKERS.size(), "too few counterexamples were checked: " + counterexamples);
    }

    /** Decodes graphs until it has {@code reachable} targets at least {@link #DEEP} steps away and {@code unreachable} missed ones. */
    private static List<GeneratedGraph> samples(int reachable, int unreachable) throws Exception {
        var generator = IrGenerators.graphs(IrGenerationConfig.defaults());
        // As admission does, leave out the inputs that a recorded defect would decide.
        var knownDefects = KnownDefectDatabase.load(List.of(Path.of("signatures/all-defects.toml")));
        var random = new Random(0x67b7L);
        var reached = new ArrayList<GeneratedGraph>();
        var missed = new ArrayList<GeneratedGraph>();
        for (var attempt = 0; attempt < 2000 && (reached.size() < reachable || missed.size() < unreachable); attempt++) {
            var input = new byte[256 + random.nextInt(768)];
            random.nextBytes(input);
            final GeneratedGraph graph;
            try {
                graph = generator.generate(input);
            } catch (InputRejectedException rejected) {
                continue;
            }
            var artifact = SpecArtifact.fromGeneratedGraph(graph, OperatorLibrary.empty());
            if (!knownDefects.matches(artifact.module(), artifact.entryPoints()).isEmpty()) {
                continue;
            }
            var distance = graph.targetDistance();
            if (distance.isPresent() && distance.getAsInt() >= DEEP && reached.size() < reachable) {
                reached.add(graph);
            } else if (distance.isEmpty() && missed.size() < unreachable) {
                missed.add(graph);
            }
        }
        assertEquals(reachable, reached.size());
        assertEquals(unreachable, missed.size());
        var samples = new ArrayList<>(reached);
        samples.addAll(missed);
        return samples;
    }
}
