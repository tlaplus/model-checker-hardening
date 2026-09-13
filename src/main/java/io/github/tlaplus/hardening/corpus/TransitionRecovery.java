package io.github.tlaplus.hardening.corpus;

import static io.github.tlaplus.hardening.corpus.CorpusLayout.NO_FOLLOW_LINKS;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

/**
 * The first pass of startup recovery: finishes the durable transitions an interrupted run left
 * behind.
 *
 * <p>An entry whose stage metadata is committed but which never reached its result directory is
 * moved there, completing its crash sidecar on the way, and a parser pass that was not fully fanned
 * out is copied to both checker branches. Aggregate commits are finished by {@link
 * AggregationRecovery}; {@link InventoryScan} then validates and counts the corpus.
 */
final class TransitionRecovery {
    private final CorpusLayout layout;
    private final CorpusEntries entries;
    private final StageTransition transitions;

    TransitionRecovery(CorpusLayout layout, CorpusEntries entries, StageTransition transitions) {
        this.layout = Objects.requireNonNull(layout, "layout");
        this.entries = Objects.requireNonNull(entries, "entries");
        this.transitions = Objects.requireNonNull(transitions, "transitions");
    }

    /** Completes every half-finished stage transition and parser fan-out. */
    void recover() throws IOException, CorpusException {
        for (var stage : CorpusStage.inputStages()) {
            recoverTransitions(stage);
        }
        for (var path : CorpusLayout.entryPaths(layout.resolve(CorpusPath.PARSER_PASS))) {
            transitions.fanOutParserPass(path);
        }
    }

    /**
     * Moves entries whose stage metadata was committed before an interruption into the result
     * directory their verdict names, completing the crash sidecar on the way.
     */
    private void recoverTransitions(CorpusStage stage) throws IOException, CorpusException {
        for (var path : CorpusLayout.entryPaths(layout.resolve(stage.input()))) {
            var entry = entries.verify(path);
            var recorded = entry.envelope().stage(stage);
            if (recorded.isEmpty()) {
                continue;
            }
            var verdict = recorded.orElseThrow().verdict();
            if (!stage.resultVerdicts().contains(verdict)) {
                throw new CorpusException(
                        stage.displayName() + " cannot record " + verdict.encodedName());
            }
            var destination =
                    layout.resolve(stage.result(verdict)).resolve(entry.path().getFileName());
            if (Files.exists(destination, NO_FOLLOW_LINKS)) {
                throw new CorpusException(
                        "cannot recover duplicate "
                                + stage.metadataName()
                                + " entry: "
                                + destination);
            }
            if (verdict == CorpusVerdict.CRASH) {
                transitions.recoverCrashReport(entry.path(), stage);
            }
            Files.move(entry.path(), destination, StandardCopyOption.ATOMIC_MOVE);
        }
    }
}
