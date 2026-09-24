package io.github.tlaplus.hardening.corpus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tlaplus.hardening.gen.InputKind;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A corpus that runs one checker fans out to, aggregates and inventories that branch alone. */
class SingleCheckerCorpusTest {
    private static final CheckingPolicy TLC_ONLY =
            new CheckingPolicy(CheckerSet.of(CorpusStage.TLC), Oracle.CONFORMANCE);
    private static final byte[] INPUT = {7, 1};

    @TempDir Path directory;

    @Test
    void aggregatesTheVerdictOfTheOnlyChecker() throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"), "");
        corpus.recoverAndValidate(CorpusEntryValidator.NONE, TLC_ONLY);
        var tlc = checkOnTlc(corpus);
        assertTrue(Files.notExists(corpus.checkerInputPath(CorpusStage.APALACHE).resolve(tlc.getFileName())));

        var input = corpus.aggregationInput(tlc).orElseThrow();
        assertEquals(Map.of(CorpusStage.TLC, CorpusVerdict.PASS), input.checkerVerdicts());
        corpus.completeAggregation(input, new StageResult(
                CorpusVerdict.PASS, Instant.ofEpochSecond(4), Instant.ofEpochSecond(5)));

        var inventory = corpus.recoverAndValidate(CorpusEntryValidator.NONE, TLC_ONLY);
        assertEquals(1, inventory.counts(CorpusStage.AGGREGATOR).count(CorpusVerdict.PASS));
        assertEquals(1, inventory.counts(CorpusStage.TLC).count(CorpusVerdict.PASS));
        assertEquals(0, inventory.processedEntries(CorpusStage.APALACHE));
    }

    @Test
    void refusesACorpusThatHoldsEntriesOfAnotherChecker() throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"), "");
        corpus.recoverAndValidate(CorpusEntryValidator.NONE, CheckingPolicy.DEFAULT);
        checkOnTlc(corpus);
        var refused = assertThrows(CorpusException.class,
                () -> corpus.recoverAndValidate(CorpusEntryValidator.NONE, TLC_ONLY));
        assertTrue(refused.getMessage().contains("does not use Apalache"), refused.getMessage());
    }

    /** Stores the input, passes it through the parser and records a TLC pass. */
    private static Path checkOnTlc(CorpusDirectory corpus) throws Exception {
        corpus.store(InputKind.EXPRESSION, INPUT);
        var parserPass = corpus.completeParser(corpus.inputPath(INPUT),
                new StageResult(CorpusVerdict.PASS, Instant.ofEpochSecond(1), Instant.ofEpochSecond(2)));
        corpus.fanOutParserPass(parserPass);
        return corpus.completeChecker(
                corpus.checkerInputPath(CorpusStage.TLC).resolve(parserPass.getFileName()),
                new StageResult(CorpusVerdict.PASS, Instant.ofEpochSecond(3), Instant.ofEpochSecond(4)));
    }
}
