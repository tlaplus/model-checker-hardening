package io.github.tlaplus.hardening.corpus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tlaplus.hardening.gen.InputKind;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class EntryProgressTest {
    @Test
    void anEntryNoStageHasJudgedIsNeitherSettledNorUngated() {
        var progress = EntryProgress.admitted(3);

        assertEquals(3, progress.generation());
        assertFalse(progress.isSettled());
        assertFalse(progress.isUngated());
        assertEquals(Optional.empty(), progress.verdict(CorpusStage.PARSER));
    }

    @Test
    void aParserRejectionOrCrashIsTerminalButAParserPassIsNot() {
        assertFalse(settled(CorpusStage.PARSER, CorpusVerdict.PASS));
        assertTrue(settled(CorpusStage.PARSER, CorpusVerdict.FAIL));
        assertTrue(settled(CorpusStage.PARSER, CorpusVerdict.CRASH));
    }

    @Test
    void aCheckerCrashSettlesTheEntryOnlyOnceEveryBranchHasFinished() {
        var crashed = EntryProgress.admitted(0)
                .with(CorpusStage.PARSER, CorpusVerdict.PASS)
                .with(CorpusStage.TLC, CorpusVerdict.CRASH);
        assertFalse(crashed.isSettled());

        assertTrue(crashed.with(CorpusStage.APALACHE, CorpusVerdict.PASS).isSettled());
        assertTrue(crashed.with(CorpusStage.APALACHE, CorpusVerdict.CRASH).isSettled());
    }

    @Test
    void checkerBranchesThatAllFinishWithoutACrashWaitForTheAggregator() {
        var checked = EntryProgress.admitted(0)
                .with(CorpusStage.PARSER, CorpusVerdict.PASS)
                .with(CorpusStage.TLC, CorpusVerdict.PASS)
                .with(CorpusStage.APALACHE, CorpusVerdict.COUNTEREXAMPLE);

        assertFalse(checked.isSettled());
        assertTrue(checked.with(CorpusStage.AGGREGATOR, CorpusVerdict.FAIL).isSettled());
    }

    @Test
    void onlyAnAggregatorPassThatTheGateHasNotJudgedIsUngated() {
        var aggregated = EntryProgress.admitted(1).with(CorpusStage.AGGREGATOR, CorpusVerdict.PASS);

        assertTrue(aggregated.isUngated());
        assertFalse(aggregated.with(CorpusStage.QUALITY, CorpusVerdict.PASS).isUngated());
        assertFalse(aggregated.with(CorpusStage.QUALITY, CorpusVerdict.FAIL).isUngated());
        assertFalse(EntryProgress.admitted(1)
                .with(CorpusStage.AGGREGATOR, CorpusVerdict.FAIL)
                .isUngated());
    }

    /**
     * The startup scan reads progress from a stored envelope and a running workflow accumulates it
     * from stage events. The two must produce the same record, or a resumed run would disagree with
     * a fresh one about where a generation stands.
     */
    @Test
    void readingAnEnvelopeAndAccumulatingEventsAgree() {
        for (var history : histories()) {
            var accumulated = EntryProgress.admitted(2);
            var stages = new ArrayList<StageMetadata>();
            for (var step : history) {
                accumulated = accumulated.with(step.stage(), step.verdict());
                stages.add(metadata(step.stage(), step.verdict()));
            }
            var read = EntryProgress.of(2, new CorpusEnvelope(
                    new CorpusInput(InputKind.EXPRESSION, new byte[] {1}),
                    Optional.empty(),
                    stages));

            assertEquals(accumulated.verdicts(), read.verdicts(), history.toString());
            assertEquals(accumulated.isSettled(), read.isSettled(), history.toString());
            assertEquals(accumulated.isUngated(), read.isUngated(), history.toString());
        }
    }

    /** One stage transition of an entry. */
    private record Step(CorpusStage stage, CorpusVerdict verdict) {}

    private static List<List<Step>> histories() {
        return List.of(
                List.of(),
                List.of(new Step(CorpusStage.PARSER, CorpusVerdict.FAIL)),
                List.of(new Step(CorpusStage.PARSER, CorpusVerdict.CRASH)),
                List.of(new Step(CorpusStage.PARSER, CorpusVerdict.PASS)),
                List.of(
                        new Step(CorpusStage.PARSER, CorpusVerdict.PASS),
                        new Step(CorpusStage.TLC, CorpusVerdict.CRASH)),
                List.of(
                        new Step(CorpusStage.PARSER, CorpusVerdict.PASS),
                        new Step(CorpusStage.TLC, CorpusVerdict.CRASH),
                        new Step(CorpusStage.APALACHE, CorpusVerdict.PASS)),
                List.of(
                        new Step(CorpusStage.PARSER, CorpusVerdict.PASS),
                        new Step(CorpusStage.TLC, CorpusVerdict.PASS),
                        new Step(CorpusStage.APALACHE, CorpusVerdict.PASS)),
                List.of(
                        new Step(CorpusStage.PARSER, CorpusVerdict.PASS),
                        new Step(CorpusStage.TLC, CorpusVerdict.PASS),
                        new Step(CorpusStage.APALACHE, CorpusVerdict.PASS),
                        new Step(CorpusStage.AGGREGATOR, CorpusVerdict.PASS)),
                List.of(
                        new Step(CorpusStage.PARSER, CorpusVerdict.PASS),
                        new Step(CorpusStage.TLC, CorpusVerdict.PASS),
                        new Step(CorpusStage.APALACHE, CorpusVerdict.COUNTEREXAMPLE),
                        new Step(CorpusStage.AGGREGATOR, CorpusVerdict.FAIL)),
                List.of(
                        new Step(CorpusStage.PARSER, CorpusVerdict.PASS),
                        new Step(CorpusStage.TLC, CorpusVerdict.PASS),
                        new Step(CorpusStage.APALACHE, CorpusVerdict.PASS),
                        new Step(CorpusStage.AGGREGATOR, CorpusVerdict.PASS),
                        new Step(CorpusStage.QUALITY, CorpusVerdict.PASS)));
    }

    private static boolean settled(CorpusStage stage, CorpusVerdict verdict) {
        return EntryProgress.admitted(0).with(stage, verdict).isSettled();
    }

    private static StageMetadata metadata(CorpusStage stage, CorpusVerdict verdict) {
        var now = Instant.EPOCH;
        return new StageMetadata(stage.metadataName(), verdict, now, now);
    }
}
