package io.github.tlaplus.hardening.corpus;

import static io.github.tlaplus.hardening.corpus.CborDocuments.cbor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import io.github.tlaplus.hardening.common.GeneratorAggregate;
import io.github.tlaplus.hardening.common.GeneratorAggregate.Richness;
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
                    CorpusStage.APALACHE, 5L,
                    CorpusStage.AGGREGATOR, 6L),
            new GeneratorAggregate(7, 8, 9, 10, new Richness(2, 1.5, 9.5, 5.5)));

    /** Captured from the pre-refactor codec, not produced by the codec under test. */
    @Test
    void readsAndReproducesTheHistoricalFixture() throws Exception {
        try (var fixture = getClass().getResourceAsStream("/corpus/workflow-statistics.cbor")) {
            var encoded = fixture.readAllBytes();
            assertEquals(STATISTICS, CorpusRunStatisticsCodec.decode(encoded));
            assertArrayEquals(encoded, CorpusRunStatisticsCodec.encode(STATISTICS));
        }
    }

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
                        CorpusStage.APALACHE, 15L,
                        CorpusStage.AGGREGATOR, 16L),
                new GeneratorAggregate(17, 18, 19, 20, Richness.empty()));
        corpus.writeRunStatistics(replacement);
        assertEquals(replacement, corpus.readRunStatistics());
        try (var workFiles = Files.list(corpus.resolve(CorpusPath.WORK))) {
            assertTrue(workFiles.noneMatch(path -> path.getFileName()
                    .toString()
                    .startsWith("workflow-stats-")));
        }
    }

    /** The aggregate follows the same shape rules as an input envelope, from the same reader. */
    @Test
    void rejectsDuplicateFieldsAndValuesOfTheWrongType() throws Exception {
        var duplicate = cbor(generator -> {
            generator.writeStartObject(null, 1);
            generator.writeObjectFieldStart("elapsedNs");
            generator.writeNumberField("total", 1);
            generator.writeNumberField("total", 2);
            generator.writeEndObject();
            generator.writeEndObject();
        });
        var textTotal = cbor(generator -> {
            generator.writeStartObject(null, 1);
            generator.writeObjectFieldStart("elapsedNs");
            generator.writeStringField("total", "soon");
            generator.writeEndObject();
            generator.writeEndObject();
        });
        var missingGenerator = cbor(generator -> {
            generator.writeStartObject(null, 1);
            generator.writeObjectFieldStart("elapsedNs");
            generator.writeNumberField("total", 1);
            generator.writeNumberField("generator", 1);
            generator.writeObjectFieldStart("stages");
            generator.writeEndObject();
            generator.writeEndObject();
            generator.writeEndObject();
        });

        assertEquals(
                "duplicate field: elapsedNs.total",
                assertThrows(
                                CorpusFormatException.class,
                                () -> CorpusRunStatisticsCodec.decode(duplicate))
                        .getMessage());
        assertEquals(
                "field 'elapsedNs.total' must be an integer",
                assertThrows(
                                CorpusFormatException.class,
                                () -> CorpusRunStatisticsCodec.decode(textTotal))
                        .getMessage());
        assertEquals(
                "missing field: generator",
                assertThrows(
                                CorpusFormatException.class,
                                () -> CorpusRunStatisticsCodec.decode(missingGenerator))
                        .getMessage());
    }

    @Test
    void rejectsInvalidDecodedAggregates() throws Exception {
        for (var invalid : new double[][] {
                {-1, 1, 1, 1, 1}, {0, 0, 1, 1, 1}, {0, 1, 3, 2, 1},
                {0, 1, 1, 2, Double.NaN}, {0, 1, 1, Double.POSITIVE_INFINITY, 1}}) {
            var encoded = cbor(generator -> new CborMapWriter()
                    .map("elapsedNs", new CborMapWriter().number("total", 0).number("generator", 0)
                            .map("stages", new CborMapWriter()))
                    .map("generator", new CborMapWriter()
                            .number("attempts", (long) invalid[0]).number("rejected", 0)
                            .number("richnessRejected", 0).number("duplicates", 0)
                            .number("richnessSamples", (long) invalid[1])
                            .number("minimumRichness", invalid[2]).number("maximumRichness", invalid[3])
                            .number("averageRichness", invalid[4]))
                    .writeTo(generator));
            assertThrows(CorpusFormatException.class, () -> CorpusRunStatisticsCodec.decode(encoded));
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
                        -1, 0, Map.of(), GeneratorAggregate.empty()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new CorpusRunStatistics(
                        0, -1, Map.of(), GeneratorAggregate.empty()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new CorpusRunStatistics(
                        0,
                        0,
                        Map.of(CorpusStage.TLC, -1L), GeneratorAggregate.empty()));
    }
}
