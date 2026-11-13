package io.github.tlaplus.hardening.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import at.forsyte.apalache.tla.lir.OperEx;
import io.github.tlaplus.hardening.checker.ExplorationCount;
import io.github.tlaplus.hardening.config.CheckerStageConfig;
import io.github.tlaplus.hardening.config.SeedConfig;
import io.github.tlaplus.hardening.corpus.CorpusDirectory;
import io.github.tlaplus.hardening.corpus.CorpusStage;
import io.github.tlaplus.hardening.gen.ExpressionCategory;
import io.github.tlaplus.hardening.gen.Generator;
import io.github.tlaplus.hardening.gen.GraphLimits;
import io.github.tlaplus.hardening.gen.InputRejectedException;
import io.github.tlaplus.hardening.gen.IrGenerationConfig;
import io.github.tlaplus.hardening.gen.IrGenerators;
import io.github.tlaplus.hardening.gen.TlaIrTestSupport;
import io.github.tlaplus.hardening.gen.graph.GeneratedGraph;
import io.github.tlaplus.hardening.gen.library.OperatorLibrary;
import io.github.tlaplus.hardening.gen.rewrite.Tautologies;
import io.github.tlaplus.hardening.signature.KnownDefectDatabase;
import io.github.tlaplus.hardening.config.ToolJar;
import io.github.tlaplus.hardening.workflow.library.ShippedRules;
import io.github.tlaplus.hardening.workflow.spec.FuzzInputModule;
import io.github.tlaplus.hardening.workflow.spec.SpecArtifact;
import io.github.tlaplus.hardening.workflow.worker.StageOutcome;
import io.github.tlaplus.hardening.workflow.worker.ToolInput;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.regex.Pattern;
import org.apalache_mc.tla.jir.TlaExpressions;
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
    private static final Pattern KNOWN_TLC_CRASH =
            Pattern.compile("TLC error code 21(69|7\\d|8\\d) mapped to exit status 255");
    /** The shortest distance of a sampled reachable target, so the checkers unroll deep spines. */
    private static final int DEEP = 10;

    @Test
    void theCheckersReportTheOutcomeTheGraphPredicts(@TempDir Path directory) throws Exception {
        assertPredicted(
                samples(IrGenerators.graphs(IrGenerationConfig.defaults()), 4, 3, FuzzInputModule.ENTRY_POINTS,
                        0x67bcL),
                directory);
    }

    /**
     * Tautological guards from the shipped rules leave the prediction unchanged (ADR 0023 §6), so
     * both checkers evaluate every guard on a shortest path to TRUE.
     */
    @Test
    void guardedEdgesLeaveTheOutcomeAsTheGraphPredicts(@TempDir Path directory) throws Exception {
        var samples = samples(
                IrGenerators.graphs(IrGenerationConfig.defaults(), new Tautologies(ShippedRules.library())), 4, 3,
                FuzzInputModule.ENTRY_POINTS, 0x67b8L);
        // An edge conjoins step < B, pc = u, Node<u>, pc' = v and the step update besides its guards.
        assertTrue(samples.stream().flatMap(graph -> TlaIrTestSupport.disjuncts(graph.spec().nextAction()).stream())
                .anyMatch(edge -> TlaExpressions.arguments((OperEx) edge).size() > 5), "no sample has a guard");
        assertPredicted(samples, directory);
    }

    /**
     * A property generated for mt to adopt leaves the prediction unchanged: gbt checks the invariant
     * alone (ADR 0027 §8). The samples leave out what a recorded defect would decide in the
     * definitions gbt evaluates; the property, which gbt does not check, may trip Apalache's limits
     * on fairness and ENABLED.
     */
    @Test
    void aPropertyLeavesTheOutcomeAsTheGraphPredicts(@TempDir Path directory) throws Exception {
        var config = IrGenerationConfig.defaults()
                .withIgnoredCategories(Set.of(ExpressionCategory.UNBOUND, ExpressionCategory.EXOTIC))
                .withGraphLimits(GraphLimits.defaults().withProperties(true));
        var samples = samples(IrGenerators.graphs(config), 4, 3,
                List.of(FuzzInputModule.INIT, FuzzInputModule.NEXT, FuzzInputModule.INV), 0x67bbL);
        assertTrue(samples.stream().anyMatch(graph -> graph.spec().property().isPresent()), "no sample has a property");
        assertPredicted(samples, directory);
    }

    private static void assertPredicted(List<GeneratedGraph> samples, Path directory) throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"), "");
        var counterexamples = new EnumMap<CorpusStage, Integer>(CorpusStage.class);
        try (var lock = corpus.acquireExclusiveLock(); var scratch = corpus.createScratch()) {
            var resources = new CheckerBackends.Resources(1, scratch, StagedTools.locate(ToolJar.APALACHE), List.of(),
                    SeedConfig.derive(0));
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
                        counterexamples.merge(stage, 1, Integer::sum);
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
        for (var stage : CHECKERS) {
            assertTrue(counterexamples.getOrDefault(stage, 0) > 0, "no counterexample was checked: " + counterexamples);
        }
    }

    /** Decodes graphs until it has {@code reachable} targets at least {@link #DEEP} steps away and {@code unreachable} missed ones. */
    private static List<GeneratedGraph> samples(Generator<GeneratedGraph> generator, int reachable, int unreachable,
            List<String> entryPoints, long seed) throws Exception {
        // As admission does, leave out the inputs that a recorded defect would decide.
        var knownDefects = KnownDefectDatabase.load(List.of(Path.of("signatures/all-defects.toml")));
        // The filter reshuffles the sample whenever the database changes, and a value-dependent
        // failure can absorb a sample's counterexample, so each call site pins its own seed: pick
        // one for which every checker still sees a counterexample.
        var random = new Random(seed);
        var reached = new ArrayList<GeneratedGraph>();
        var missed = new ArrayList<GeneratedGraph>();
        for (var attempt = 0; attempt < 6000 && (reached.size() < reachable || missed.size() < unreachable); attempt++) {
            var input = new byte[256 + random.nextInt(768)];
            random.nextBytes(input);
            final GeneratedGraph graph;
            try {
                graph = generator.generate(input);
            } catch (InputRejectedException rejected) {
                continue;
            }
            var artifact = SpecArtifact.fromGeneratedGraph(graph, OperatorLibrary.empty());
            if (!knownDefects.quarantining(artifact.module(), entryPoints).isEmpty()) {
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
