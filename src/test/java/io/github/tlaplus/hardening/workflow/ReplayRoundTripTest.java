package io.github.tlaplus.hardening.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import at.forsyte.apalache.tla.lir.TlaVarDecl;
import io.github.tlaplus.hardening.checker.ExplorationCount;
import io.github.tlaplus.hardening.config.CheckerStageConfig;
import io.github.tlaplus.hardening.config.SeedConfig;
import io.github.tlaplus.hardening.config.ToolJar;
import io.github.tlaplus.hardening.corpus.CorpusDirectory;
import io.github.tlaplus.hardening.corpus.CorpusInput;
import io.github.tlaplus.hardening.corpus.CorpusStage;
import io.github.tlaplus.hardening.gen.InputKind;
import io.github.tlaplus.hardening.gen.InputRejectedException;
import io.github.tlaplus.hardening.gen.IrGenerationConfig;
import io.github.tlaplus.hardening.gen.trace.ItfTraces;
import io.github.tlaplus.hardening.gen.trace.TracePayload;
import io.github.tlaplus.hardening.signature.KnownDefectDatabase;
import io.github.tlaplus.hardening.workflow.spec.FuzzInputModule;
import io.github.tlaplus.hardening.workflow.spec.SpecArtifact;
import io.github.tlaplus.hardening.workflow.spec.SpecDecoders;
import io.github.tlaplus.hardening.workflow.tool.ToolBackend;
import io.github.tlaplus.hardening.workflow.tool.ToolWorker;
import io.github.tlaplus.hardening.workflow.worker.StageOutcome;
import io.github.tlaplus.hardening.workflow.worker.ToolInput;
import io.github.tlaplus.hardening.workflow.worker.ToolResult;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Random;
import org.apalache_mc.tla.jir.TlaModules;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Records the counterexamples TLC and Apalache report on generated modules and graphs, and replays
 * each against both checkers (ADR 0034 §4, §5). A checker's own trace is a behavior of the module
 * whose last state is what the checker claimed, so every checker that reported a counterexample on
 * the base reports one of exactly the trace's length on the replay. A checker that did not may fail
 * on a partial term in the trace's states, which conformance already compares.
 */
class ReplayRoundTripTest {
    private static final CheckerStageConfig CONFIG = new CheckerStageConfig(10, 120, 1024, 1);
    private static final List<CorpusStage> CHECKERS = List.of(CorpusStage.TLC, CorpusStage.APALACHE);
    /** Counterexamples each checker must record, and how many of them must have transitions. */
    private static final int RECORDED = 6;
    private static final int DEEP = 3;
    /**
     * On some samples Apalache refutes its own trace: a CHOOSE without a witness is free in its
     * search but fixed in the replay, where the trace's values assign the variables
     * (findings/apalache-bmc/apalache-bmc-024.md). The sample is pinned to a seed without such a
     * term; the campaign is where they are found.
     */
    private static final long SEED = 0x7ad0L;

    /** A recorded trace to replay, and the checkers that reported a counterexample on its base. */
    private record Replay(CorpusInput entry, List<CorpusStage> reporters) {}

    @Test
    void everyReportingCheckerConfirmsEachRecordedTrace(@TempDir Path directory) throws Exception {
        var decoders = SpecDecoders.of(IrGenerationConfig.defaults());
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"), "");
        try (var lock = corpus.acquireExclusiveLock(); var scratch = corpus.createScratch()) {
            var tools = new CheckerBackends.Tools(StagedTools.locate(ToolJar.APALACHE), List.of(), Optional.empty());
            var resources = new CheckerBackends.Resources(1, scratch, tools, SeedConfig.derive(0));
            try (var checkers = new Checkers(resources)) {
                var mismatches = new ArrayList<String>();
                for (var replay : record(decoders, checkers)) {
                    var artifact = decoders.decode(replay.entry());
                    var transitions = artifact.trace().orElseThrow().transitions();
                    for (var stage : replay.reporters()) {
                        var result = checkers.check(stage, artifact, false);
                        if (result.outcome() != StageOutcome.COUNTEREXAMPLE
                                || !length(result).equals(OptionalLong.of(transitions))) {
                            mismatches.add(stage.displayName() + " on a replay of " + transitions + " transitions: "
                                    + result.outcome() + ", " + result.diagnostic());
                        }
                    }
                }
                assertEquals(List.of(), mismatches);
            }
        }
    }

    /** Checks sampled modules and graphs until each checker has recorded enough counterexamples. */
    private static List<Replay> record(SpecDecoders decoders, Checkers checkers) throws Exception {
        var knownDefects = KnownDefectDatabase.load(List.of(Path.of("signatures/all-defects.toml")));
        var recorded = new EnumMap<CorpusStage, Integer>(CorpusStage.class);
        var deep = new EnumMap<CorpusStage, Integer>(CorpusStage.class);
        var replays = new ArrayList<Replay>();
        var random = new Random(SEED);
        for (var attempt = 0; attempt < 400 && CHECKERS.stream().anyMatch(stage ->
                recorded.getOrDefault(stage, 0) < RECORDED || deep.getOrDefault(stage, 0) < DEEP); attempt++) {
            var bytes = new byte[64 + random.nextInt(512)];
            random.nextBytes(bytes);
            var kind = attempt % 2 == 0 ? InputKind.MODULE : InputKind.GRAPH;
            final SpecArtifact artifact;
            try {
                artifact = decoders.decode(new CorpusInput(kind, bytes));
            } catch (InputRejectedException rejected) {
                continue;
            }
            if (!knownDefects.quarantining(artifact.module(), FuzzInputModule.ENTRY_POINTS).isEmpty()) {
                continue;
            }
            var variables = TlaModules.declarations(artifact.module()).stream()
                    .filter(TlaVarDecl.class::isInstance).map(TlaVarDecl.class::cast).toList();
            var payloads = new ArrayList<TracePayload>();
            var reporters = new ArrayList<CorpusStage>();
            for (var stage : CHECKERS) {
                var result = checkers.check(stage, artifact, true);
                if (result.outcome() != StageOutcome.COUNTEREXAMPLE) {
                    continue;
                }
                var trace = ItfTraces.read(result.trace().orElseThrow(
                        () -> new AssertionError(stage + " recorded no trace: " + result.diagnostic())), variables);
                assertEquals(length(result), OptionalLong.of(trace.transitions()), stage + " trace length");
                reporters.add(stage);
                recorded.merge(stage, 1, Integer::sum);
                deep.merge(stage, trace.transitions() > 0 ? 1 : 0, Integer::sum);
                payloads.add(new TracePayload(kind, bytes, trace));
            }
            payloads.forEach(payload -> replays.add(
                    new Replay(new CorpusInput(InputKind.TRACE, payload.encode()), List.copyOf(reporters))));
        }
        for (var stage : CHECKERS) {
            assertTrue(recorded.getOrDefault(stage, 0) >= RECORDED, "counterexamples of " + stage + ": " + recorded);
            assertTrue(deep.getOrDefault(stage, 0) >= DEEP, "counterexamples with transitions of " + stage + ": " + deep);
        }
        return replays;
    }

    private static OptionalLong length(ToolResult result) {
        return result.metrics().map(metrics -> metrics.count(ExplorationCount.TRACE_LENGTH)).orElse(OptionalLong.empty());
    }

    /** One worker per checker, replaced after a crash. */
    private static final class Checkers implements AutoCloseable {
        private final EnumMap<CorpusStage, ToolBackend> backends = new EnumMap<>(CorpusStage.class);
        private final EnumMap<CorpusStage, ToolWorker> workers = new EnumMap<>(CorpusStage.class);

        Checkers(CheckerBackends.Resources resources) throws Exception {
            for (var stage : CHECKERS) {
                backends.put(stage, CheckerBackends.create(stage, CONFIG, resources));
            }
        }

        ToolResult check(CorpusStage stage, SpecArtifact artifact, boolean recordsTrace) throws Exception {
            var backend = backends.get(stage);
            var worker = workers.get(stage);
            if (worker == null) {
                worker = backend.startWorker();
                workers.put(stage, worker);
            }
            var result = worker.check(new ToolInput(
                    backend.renderer().apply(artifact), artifact.request().withTraceRecording(recordsTrace)));
            if (result.outcome() == StageOutcome.CRASH) {
                worker.close();
                workers.remove(stage);
            }
            return result;
        }

        @Override
        public void close() {
            workers.values().forEach(ToolWorker::close);
        }
    }
}
