package io.github.tlaplus.hardening.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tlaplus.hardening.checker.CheckerFailure;
import io.github.tlaplus.hardening.checker.CheckerFailureCode;
import io.github.tlaplus.hardening.corpus.CorpusEnvelope;
import io.github.tlaplus.hardening.corpus.CorpusInput;
import io.github.tlaplus.hardening.corpus.CorpusVerdict;
import io.github.tlaplus.hardening.corpus.GenerationMetadata;
import io.github.tlaplus.hardening.corpus.StageMetadata;
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
                envelope(Optional.of(new GenerationMetadata(3, 0.25)), List.of()), INPUT);

        assertTrue(report.contains("gen:"), report);
        assertTrue(report.contains("  cohort: 3"), report);
        assertTrue(report.contains("  richness: 0.25"), report);
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

    /** A failure code prints with its symbol; the detail line appears only when there is one. */
    @Test
    void reportsAFailureCodeWithAndWithoutADetail() {
        var withoutDetail = EnvelopeReport.render(
                envelope(Optional.empty(), List.of(failedStage(Optional.empty()))), INPUT);
        assertTrue(withoutDetail.contains("    code: 12 (counterexample)"), withoutDetail);
        assertFalse(withoutDetail.contains("detail:"), withoutDetail);

        var withDetail = EnvelopeReport.render(
                envelope(Optional.empty(), List.of(failedStage(Optional.of("boom")))), INPUT);
        assertTrue(withDetail.contains("    code: 12 (counterexample)"), withDetail);
        assertTrue(withDetail.contains("    detail: boom"), withDetail);
    }

    /** Every line of the rendered input is indented, including a multi-line expression. */
    @Test
    void indentsEveryLineOfTheInput() {
        var report = EnvelopeReport.render(
                envelope(Optional.empty(), List.of()), "IF TRUE" + newline() + "THEN 1");

        assertTrue(report.endsWith("  IF TRUE" + newline() + "  THEN 1" + newline()), report);
    }

    private static StageMetadata failedStage(Optional<String> detail) {
        return new StageMetadata(
                "tlc",
                CorpusVerdict.FAIL,
                Instant.ofEpochSecond(10),
                Instant.ofEpochSecond(13),
                Optional.of(new CheckerFailure(CheckerFailureCode.COUNTEREXAMPLE, detail)));
    }

    private static CorpusEnvelope envelope(
            Optional<GenerationMetadata> generation, List<StageMetadata> stages) {
        return new CorpusEnvelope(
                CorpusInput.expression(new byte[] {1}), generation, stages);
    }

    private static String newline() {
        return System.lineSeparator();
    }
}
