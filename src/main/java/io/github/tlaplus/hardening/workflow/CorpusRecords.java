package io.github.tlaplus.hardening.workflow;

import io.github.tlaplus.hardening.corpus.CheckerSet;
import io.github.tlaplus.hardening.corpus.CorpusRecord;
import io.github.tlaplus.hardening.corpus.CorpusStage;
import io.github.tlaplus.hardening.corpus.Technique;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The corpus records a run repeats on every later run (ADR 0016 §1, §5): the technique and the
 * checker set. Each decides how every entry is decoded, fanned out and judged.
 */
public final class CorpusRecords {
    /** The technique of `fuzztla run --how`; a corpus that records none runs {@code pbt}. */
    public static final ReplayRecord<Technique> TECHNIQUE = new ReplayRecord<>(
            CorpusRecord.TECHNIQUE,
            Technique.UNRECORDED,
            new ReplayRecord.Codec<>(Technique::encodedName, Technique::fromEncodedName),
            (saved, expected) -> "this corpus runs --how=" + saved + ", not --how=" + expected
                    + "; initialize a new corpus for --how=" + expected);

    /** The checkers of `[workflow] checkers`; a corpus that records none runs every checker. */
    public static final ReplayRecord<CheckerSet> CHECKERS = new ReplayRecord<>(
            CorpusRecord.CHECKERS,
            CheckerSet.ALL,
            new ReplayRecord.Codec<>(CorpusRecords::encode, CorpusRecords::decode),
            (saved, expected) -> "this corpus runs the checkers " + saved + ", not " + expected
                    + "; restore [workflow] checkers or initialize a new corpus");

    private static final String SEPARATOR = ",";

    private CorpusRecords() {}

    private static String encode(CheckerSet checkers) {
        return checkers.stages().stream().map(CorpusStage::metadataName).collect(Collectors.joining(SEPARATOR));
    }

    private static Optional<CheckerSet> decode(String text) {
        var stages = new ArrayList<CorpusStage>();
        for (var name : text.split(SEPARATOR, -1)) {
            var stage = CorpusStage.checkerBranches().stream()
                    .filter(checker -> checker.metadataName().equals(name))
                    .findFirst();
            if (stage.isEmpty() || stages.contains(stage.get())) {
                return Optional.empty();
            }
            stages.add(stage.get());
        }
        return Optional.of(new CheckerSet(stages));
    }
}
