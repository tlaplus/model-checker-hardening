package io.github.tlaplus.hardening.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tlaplus.hardening.checker.CheckerFailure;
import io.github.tlaplus.hardening.checker.CheckerFailureCode;
import io.github.tlaplus.hardening.checker.ExplorationCount;
import io.github.tlaplus.hardening.checker.ExplorationMetrics;
import io.github.tlaplus.hardening.checker.ExplorationPhase;
import io.github.tlaplus.hardening.corpus.CorpusEnvelope;
import io.github.tlaplus.hardening.corpus.CorpusInput;
import io.github.tlaplus.hardening.corpus.CorpusVerdict;
import io.github.tlaplus.hardening.corpus.GenerationMetadata;
import io.github.tlaplus.hardening.corpus.Mutation;
import io.github.tlaplus.hardening.corpus.StageMetadata;
import io.github.tlaplus.hardening.corpus.StageRecord;
import io.github.tlaplus.hardening.gen.InputKind;
import io.github.tlaplus.hardening.mutation.MutationOperator;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class EnvelopeReportTest {
    private static final String INPUT = "TRUE";

    @Test
    void omitsGenerationAndStagesWhenTheEntryHasNeither() {
        var report = EnvelopeReport.render(envelope(Optional.empty(), List.of()), INPUT);

        assertEquals("kind: expr" + newline() + "input:" + newline() + "  TRUE" + newline(), report);
    }

    @Test
    void reportsAdmissionMetadataWhenPresent() {
        var report = EnvelopeReport.render(
                envelope(Optional.of(GenerationMetadata.generated(0, 3, 0.25)), List.of()), INPUT);

        assertTrue(report.contains("gen:"), report);
        assertTrue(report.contains("  cohort: 3"), report);
        assertTrue(report.contains("  richness: 0.25"), report);
        assertTrue(report.contains("  generation: 0"), report);
        assertFalse(report.contains("parent"), report);
    }

    @Test
    void reportsTheProvenanceOfAMutant() {
        var parent = "0f".repeat(32);
        var mutant = GenerationMetadata.mutated(4, 1, 0.0, new Mutation(
                parent, List.of(MutationOperator.COPY, MutationOperator.ERASE)));

        var report = EnvelopeReport.render(envelope(Optional.of(mutant), List.of()), INPUT);

        assertTrue(report.contains("  generation: 4"), report);
        assertTrue(report.contains("  parent: " + parent), report);
        assertTrue(report.contains("  operators: copy, erase"), report);
    }

    @Test
    void reportsExplorationMetricsOneFieldPerLine() {
        var metrics = ExplorationMetrics.builder()
                .phase(ExplorationPhase.EXPLORE)
                .count(ExplorationCount.INIT_STATES, 1)
                .count(ExplorationCount.TRACE_LENGTH, 3)
                .saturated(true)
                .build();
        var report = EnvelopeReport.render(
                envelope(
                        Optional.empty(),
                        List.of(new StageMetadata(
                                "tlc",
                                new StageRecord(
                                        CorpusVerdict.COUNTEREXAMPLE,
                                        Instant.ofEpochSecond(10),
                                        Instant.ofEpochSecond(12),
                                        Optional.empty(),
                                        Optional.of(metrics))))),
                INPUT);

        var expected = String.join(
                newline(),
                "    metrics:",
                "      phase: explore",
                "      initStates: 1",
                "      traceLength: 3",
                "      saturated: true",
                "input:");
        assertTrue(report.contains(expected), report);
    }

    /** A passing stage carries no failure, so neither the code nor the detail line is printed. */
    @Test
    void reportsAStageWithoutAFailure() {
        var report = EnvelopeReport.render(
                envelope(
                        Optional.empty(),
                        List.of(new StageMetadata(
                                "parser",
                                CorpusVerdict.PASS,
                                Instant.ofEpochSecond(10),
                                Instant.ofEpochSecond(13)))),
                INPUT);

        assertTrue(report.contains("  parser:"), report);
        assertTrue(report.contains("    verdict: pass"), report);
        assertTrue(report.contains("(duration: 3s)"), report);
        assertFalse(report.contains("code:"), report);
        assertFalse(report.contains("detail:"), report);
    }

    @Test
    void reportsACounterexampleWithoutFailureMetadata() {
        var report = EnvelopeReport.render(
                envelope(
                        Optional.empty(),
                        List.of(new StageMetadata(
                                "tlc",
                                CorpusVerdict.COUNTEREXAMPLE,
                                Instant.ofEpochSecond(10),
                                Instant.ofEpochSecond(13)))),
                INPUT);

        assertTrue(report.contains("    verdict: counterexample"), report);
        assertFalse(report.contains("code:"), report);
        assertFalse(report.contains("detail:"), report);
    }

    /** A failure code prints with its symbol; the detail line appears only when there is one. */
    @Test
    void reportsAFailureCodeWithAndWithoutADetail() {
        var withoutDetail = EnvelopeReport.render(
                envelope(Optional.empty(), List.of(failedStage(Optional.empty()))), INPUT);
        assertTrue(withoutDetail.contains("    code: 75 (spec_eval)"), withoutDetail);
        assertFalse(withoutDetail.contains("detail:"), withoutDetail);

        var withDetail = EnvelopeReport.render(
                envelope(Optional.empty(), List.of(failedStage(Optional.of("boom")))), INPUT);
        assertTrue(withDetail.contains("    code: 75 (spec_eval)"), withDetail);
        assertTrue(withDetail.contains("    detail: boom"), withDetail);
    }

    /** Every line of the rendered input is indented, including a multi-line expression. */
    @Test
    void indentsEveryLineOfTheInput() {
        var report = EnvelopeReport.render(
                envelope(Optional.empty(), List.of()), "IF TRUE" + newline() + "THEN 1");

        assertTrue(report.endsWith("  IF TRUE" + newline() + "  THEN 1" + newline()), report);
    }

    /**
     * The corpus format keeps the stage name open so an entry naming a stage this build never
     * heard of still reads. That name reaches the formatter, so it must never be spliced into a
     * format string.
     */
    @Test
    void rendersAStageWhoseNameContainsFormatSpecifiers() {
        var report = EnvelopeReport.render(
                envelope(
                        Optional.empty(),
                        List.of(new StageMetadata(
                                "%s-%d%n",
                                CorpusVerdict.PASS,
                                Instant.ofEpochSecond(10),
                                Instant.ofEpochSecond(13)))),
                INPUT);

        assertTrue(report.contains("  %s-%d%n:" + newline()), report);
        assertTrue(report.contains("    verdict: pass"), report);
    }

    private static StageMetadata failedStage(Optional<String> detail) {
        return new StageMetadata(
                "tlc",
                CorpusVerdict.FAIL,
                Instant.ofEpochSecond(10),
                Instant.ofEpochSecond(13),
                Optional.of(new CheckerFailure(CheckerFailureCode.SPEC_EVAL, detail)));
    }

    private static CorpusEnvelope envelope(
            Optional<GenerationMetadata> generation, List<StageMetadata> stages) {
        return new CorpusEnvelope(
                new CorpusInput(InputKind.EXPRESSION, new byte[] {1}), generation, stages);
    }

    private static String newline() {
        return System.lineSeparator();
    }
}
