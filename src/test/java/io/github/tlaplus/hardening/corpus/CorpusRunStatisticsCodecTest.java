package io.github.tlaplus.hardening.corpus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tlaplus.hardening.config.FuzzTlaConfig;
import io.github.tlaplus.hardening.config.TomlConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CorpusRunStatisticsCodecTest {
    private static final CorpusRunStatistics STATISTICS = new CorpusRunStatistics(
            1,
            2,
            Map.of(
                    CorpusStage.PARSER, 3L,
                    CorpusStage.TLC, 4L,
                    CorpusStage.APALACHE, 5L),
            6,
            7,
            8,
            9,
            2,
            1.5,
            9.5,
            5.5);

    @Test
    void roundTripsTheAggregateDocument() throws Exception {
        assertEquals(STATISTICS, CorpusRunStatisticsCodec.decode(
                CorpusRunStatisticsCodec.encode(STATISTICS)));
    }

    @Test
    void readsZeroBeforeTheFirstSaveAndAtomicallyReplacesStatistics(@TempDir Path directory)
            throws Exception {
        var corpus = CorpusDirectory.initialize(
                directory.resolve("corpus"), TomlConfig.render(FuzzTlaConfig.defaults()));

        assertEquals(CorpusRunStatistics.empty(), corpus.readRunStatistics());

        corpus.writeRunStatistics(STATISTICS);
        assertEquals(STATISTICS, corpus.readRunStatistics());

        var replacement = new CorpusRunStatistics(
                11,
                12,
                Map.of(
                        CorpusStage.PARSER, 13L,
                        CorpusStage.TLC, 14L,
                        CorpusStage.APALACHE, 15L),
                16,
                17,
                18,
                19,
                0,
                0.0,
                0.0,
                0.0);
        corpus.writeRunStatistics(replacement);
        assertEquals(replacement, corpus.readRunStatistics());
        try (var workFiles = Files.list(corpus.resolve(CorpusPath.WORK))) {
            assertTrue(workFiles.noneMatch(path -> path.getFileName()
                    .toString()
                    .startsWith("workflow-stats-")));
        }
    }

    @Test
    void rejectsMalformedStatistics(@TempDir Path directory) throws Exception {
        var corpus = CorpusDirectory.initialize(
                directory.resolve("corpus"), TomlConfig.render(FuzzTlaConfig.defaults()));
        Files.write(corpus.resolve(CorpusPath.WORKFLOW_STATISTICS), new byte[] {1});

        var failure = assertThrows(CorpusException.class, corpus::readRunStatistics);

        assertTrue(failure.getMessage().contains("invalid workflow statistics file"));
    }

    @Test
    void rejectsInvalidAggregateValues() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new CorpusRunStatistics(
                        -1, 0, Map.of(), 0, 0, 0, 0, 0, 0.0, 0.0, 0.0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new CorpusRunStatistics(
                        0, 0, Map.of(), 0, 0, 0, 0, 1, 5.0, 1.0, 2.0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new CorpusRunStatistics(
                        0,
                        0,
                        Map.of(CorpusStage.TLC, -1L),
                        0,
                        0,
                        0,
                        0,
                        0,
                        0.0,
                        0.0,
                        0.0));
    }
}
