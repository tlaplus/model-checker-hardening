package io.github.tlaplus.hardening.corpus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.fasterxml.jackson.dataformat.cbor.CBORGenerator;
import io.github.tlaplus.hardening.config.FuzzTlaConfig;
import io.github.tlaplus.hardening.config.TomlConfig;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CorpusRunStatisticsCodecTest {
    private static final CBORFactory FACTORY = new CBORFactory();

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

    private byte[] cbor(CborWriter writer) throws Exception {
        var output = new ByteArrayOutputStream();
        try (var generator = FACTORY.createGenerator(output)) {
            writer.write(generator);
        }
        return output.toByteArray();
    }

    @FunctionalInterface
    private interface CborWriter {
        void write(CBORGenerator generator) throws IOException;
    }
}
