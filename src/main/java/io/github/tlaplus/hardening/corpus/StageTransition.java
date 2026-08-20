package io.github.tlaplus.hardening.corpus;

import static io.github.tlaplus.hardening.corpus.CorpusLayout.NO_FOLLOW_LINKS;

import io.github.tlaplus.hardening.common.Diagnostics;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

/**
 * Moves one entry from a stage's input directory to the directory its verdict names.
 *
 * <p>The corpus directories are the durable source of truth, so a transition is committed in an
 * order that recovery can always finish: the crash sidecar is staged under {@code .work} first, the
 * updated envelope is committed onto the source path second, and only then do the sidecar and the
 * entry move into the result directory. A failure before the metadata commit leaves the entry
 * untouched and discards the staged sidecar; a failure after it leaves work that {@link
 * CorpusRecovery} completes on the next run.
 */
final class StageTransition {
    private final CorpusLayout layout;
    private final CorpusEntries entries;

    StageTransition(CorpusLayout layout, CorpusEntries entries) {
        this.layout = Objects.requireNonNull(layout, "layout");
        this.entries = Objects.requireNonNull(entries, "entries");
    }

    /** Records a stage result on an entry and moves the entry to its result directory. */
    Path complete(Path source, CorpusStage stage, StageResult result)
            throws IOException, CorpusException {
        // Validate the requested transition and resolve its result path before changing the corpus.
        entries.requireOwnedPath(source, stage.input(), stage.displayName() + " input");
        Objects.requireNonNull(result, "result");
        var verdict = result.verdict();
        var encoded = Files.readAllBytes(source);
        var entry = CorpusEntries.decode(source, encoded);
        CorpusEntries.requireMissingStage(entry.path(), entry.encoded(), stage);
        var destination = layout.resolve(stage.result(verdict)).resolve(source.getFileName());
        if (Files.exists(destination, NO_FOLLOW_LINKS)) {
            throw new CorpusException(
                    stage.displayName() + " destination already exists: " + destination);
        }

        // A crash transition also owns a sidecar that must move with the corpus entry.
        Path stagedCrashReport = null;
        Path crashReportDestination = null;
        if (verdict == CorpusVerdict.CRASH) {
            var reportName = CorpusLayout.crashReportName(source);
            stagedCrashReport = layout.stagedCrashReport(stage, reportName);
            crashReportDestination =
                    layout.resolve(stage.result(CorpusVerdict.CRASH)).resolve(reportName);
            if (Files.exists(crashReportDestination, NO_FOLLOW_LINKS)) {
                throw new CorpusException(
                        stage.displayName()
                                + " crash report already exists: "
                                + crashReportDestination);
            }
        }

        // Prepare the updated envelope before committing either artifact.
        final byte[] updated;
        try {
            updated = CorpusEnvelopeCodec.withStageMetadata(
                    encoded,
                    new StageMetadata(
                            stage.metadataName(),
                            verdict.encodedName(),
                            result.startTime(),
                            result.endTime(),
                            result.failure()));
        } catch (CorpusFormatException exception) {
            throw new CorpusException(
                    "invalid CBOR corpus entry: " + source + ": " + Diagnostics.message(exception),
                    exception);
        }

        // Commit metadata first; recovery can then finish the sidecar and directory moves.
        var metadataCommitted = false;
        try {
            if (stagedCrashReport != null) {
                stageCrashReport(stagedCrashReport, result.diagnostic(), stage.displayName());
            }
            layout.replaceAtomically(source, stage.metadataName() + "-", updated);
            metadataCommitted = true;
            if (stagedCrashReport != null) {
                Files.move(
                        stagedCrashReport,
                        crashReportDestination,
                        StandardCopyOption.ATOMIC_MOVE);
            }
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
            return destination;
        } finally {
            // Preserve a staged sidecar only when committed metadata makes it recoverable.
            if (!metadataCommitted && stagedCrashReport != null) {
                Files.deleteIfExists(stagedCrashReport);
            }
        }
    }

    /**
     * Copies one parser pass into both checker branches, then removes the fan-out source. The two
     * copies keep one logical identity, so recovery requires them to stay identical.
     */
    void fanOutParserPass(Path source) throws IOException, CorpusException {
        entries.requireOwnedPath(source, CorpusPath.PARSER_PASS, "parser pass");
        var encoded = Files.readAllBytes(source);
        var entry = CorpusEntries.decode(source, encoded);
        CorpusEntries.requireStageVerdict(entry, CorpusStage.PARSER, CorpusVerdict.PASS);
        for (var checker : CorpusStage.checkerBranches()) {
            CorpusEntries.requireMissingStage(entry.path(), entry.encoded(), checker);
        }

        for (var checker : CorpusStage.checkerBranches()) {
            var destination =
                    layout.resolve(checker.input()).resolve(source.getFileName());
            installBranchCopy(source, encoded, destination);
        }
        Files.delete(source);
    }

    /** Writes one checker-branch copy, tolerating a copy an interrupted fan-out already wrote. */
    private void installBranchCopy(Path source, byte[] encoded, Path destination)
            throws IOException, CorpusException {
        if (Files.exists(destination, NO_FOLLOW_LINKS)) {
            if (!Files.isRegularFile(destination, NO_FOLLOW_LINKS)) {
                throw new CorpusException(
                        "checker branch destination is not a file: " + destination);
            }
            var existing = CorpusEntries.decode(destination, Files.readAllBytes(destination));
            var candidate = CorpusEntries.decode(source, encoded);
            CorpusEntries.requireSameParserOutput(
                    source.getFileName().toString(), existing, candidate);
            return;
        }
        layout.createAtomically(destination, "fanout-", encoded);
    }

    /** Stages the stack-trace sidecar of a crash verdict under {@code .work}. */
    private void stageCrashReport(Path stagedReport, String diagnostic, String displayName)
            throws IOException {
        var report = diagnostic.isBlank()
                ? displayName + " crashed without a diagnostic."
                : diagnostic;
        if (!report.endsWith("\n")) {
            report += System.lineSeparator();
        }
        layout.replaceAtomically(stagedReport, "crash-", report.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Completes a crash transition whose metadata was committed before an interruption: moves a
     * staged sidecar into the result directory, and rejects a corpus that holds both copies.
     */
    void recoverCrashReport(Path source, CorpusStage stage)
            throws IOException, CorpusException {
        var reportName = CorpusLayout.crashReportName(source);
        var stagedReport = layout.stagedCrashReport(stage, reportName);
        var destination = layout.resolve(stage.result(CorpusVerdict.CRASH)).resolve(reportName);
        if (Files.exists(destination, NO_FOLLOW_LINKS)) {
            if (!Files.isRegularFile(destination, NO_FOLLOW_LINKS)) {
                throw new CorpusException(
                        stage.metadataName()
                                + " crash report is not a regular file: "
                                + destination);
            }
            if (Files.exists(stagedReport, NO_FOLLOW_LINKS)) {
                throw new CorpusException(
                        stage.metadataName()
                                + " crash report exists in both work and result directories: "
                                + reportName);
            }
            return;
        }
        if (Files.exists(stagedReport, NO_FOLLOW_LINKS)) {
            if (!Files.isRegularFile(stagedReport, NO_FOLLOW_LINKS)) {
                throw new CorpusException(
                        "staged "
                                + stage.metadataName()
                                + " crash report is not a regular file: "
                                + stagedReport);
            }
            Files.move(stagedReport, destination, StandardCopyOption.ATOMIC_MOVE);
        }
    }
}
