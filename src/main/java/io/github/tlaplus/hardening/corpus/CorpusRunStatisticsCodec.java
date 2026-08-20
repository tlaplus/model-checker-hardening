package io.github.tlaplus.hardening.corpus;

import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import io.github.tlaplus.hardening.common.Diagnostics;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Encodes and decodes the aggregate workflow-statistics CBOR document. */
final class CorpusRunStatisticsCodec {
    private static final String ELAPSED_FIELD = "elapsedNs";
    private static final String GENERATOR_FIELD = "generator";
    private static final String STAGES_FIELD = "stages";
    private static final String TOTAL_FIELD = "total";
    private static final String ATTEMPTS_FIELD = "attempts";
    private static final String REJECTED_FIELD = "rejected";
    private static final String RICHNESS_REJECTED_FIELD = "richnessRejected";
    private static final String DUPLICATES_FIELD = "duplicates";
    private static final String RICHNESS_SAMPLES_FIELD = "richnessSamples";
    private static final String MINIMUM_RICHNESS_FIELD = "minimumRichness";
    private static final String MAXIMUM_RICHNESS_FIELD = "maximumRichness";
    private static final String AVERAGE_RICHNESS_FIELD = "averageRichness";

    private static final CBORFactory FACTORY = new CBORFactory();

    private CorpusRunStatisticsCodec() {}

    static byte[] encode(CorpusRunStatistics statistics) throws IOException {
        Objects.requireNonNull(statistics, "statistics");
        var output = new ByteArrayOutputStream();
        try (var generator = FACTORY.createGenerator(output)) {
            generator.writeStartObject(null, 2);
            generator.writeFieldName(ELAPSED_FIELD);
            generator.writeStartObject(null, 3);
            generator.writeNumberField(TOTAL_FIELD, statistics.totalElapsedNanos());
            generator.writeNumberField(GENERATOR_FIELD, statistics.generatorElapsedNanos());
            generator.writeFieldName(STAGES_FIELD);
            generator.writeStartObject(null, CorpusStage.values().length);
            for (var stage : CorpusStage.values()) {
                generator.writeNumberField(
                        stage.metadataName(), statistics.stageElapsedNanos(stage));
            }
            generator.writeEndObject();
            generator.writeEndObject();
            generator.writeFieldName(GENERATOR_FIELD);
            generator.writeStartObject(null, 8);
            generator.writeNumberField(ATTEMPTS_FIELD, statistics.generatorAttempts());
            generator.writeNumberField(REJECTED_FIELD, statistics.generatorRejected());
            generator.writeNumberField(
                    RICHNESS_REJECTED_FIELD, statistics.generatorRichnessRejected());
            generator.writeNumberField(DUPLICATES_FIELD, statistics.generatorDuplicates());
            generator.writeNumberField(RICHNESS_SAMPLES_FIELD, statistics.richnessSamples());
            generator.writeNumberField(MINIMUM_RICHNESS_FIELD, statistics.minimumRichness());
            generator.writeNumberField(MAXIMUM_RICHNESS_FIELD, statistics.maximumRichness());
            generator.writeNumberField(AVERAGE_RICHNESS_FIELD, statistics.averageRichness());
            generator.writeEndObject();
            generator.writeEndObject();
        }
        return output.toByteArray();
    }

    static CorpusRunStatistics decode(byte[] encoded) throws CorpusFormatException {
        Objects.requireNonNull(encoded, "encoded");
        try (var reader = CborReader.of(encoded)) {
            reader.startDocument();
            Elapsed elapsed = null;
            Generator generator = null;
            CborReader.Field field;
            while ((field = reader.nextField(CborReader.ROOT)) != null) {
                switch (field.name()) {
                    case ELAPSED_FIELD -> {
                        reader.requireMap(field);
                        elapsed = readElapsed(reader);
                    }
                    case GENERATOR_FIELD -> {
                        reader.requireMap(field);
                        generator = readGenerator(reader);
                    }
                    default -> reader.skipValue();
                }
            }
            var elapsedNanos = CborReader.required(elapsed, ELAPSED_FIELD);
            var generatorStatistics = CborReader.required(generator, GENERATOR_FIELD);
            reader.endDocument();
            try {
                return new CorpusRunStatistics(
                        elapsedNanos.total(),
                        elapsedNanos.generator(),
                        elapsedNanos.stages(),
                        generatorStatistics.attempts(),
                        generatorStatistics.rejected(),
                        generatorStatistics.richnessRejected(),
                        generatorStatistics.duplicates(),
                        generatorStatistics.richnessSamples(),
                        generatorStatistics.minimumRichness(),
                        generatorStatistics.maximumRichness(),
                        generatorStatistics.averageRichness());
            } catch (IllegalArgumentException exception) {
                throw CborReader.malformed(
                        "invalid workflow statistics: " + Diagnostics.message(exception));
            }
        } catch (CorpusFormatException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw CborReader.invalidCbor(exception);
        }
    }

    private static Elapsed readElapsed(CborReader reader) throws IOException {
        Long total = null;
        Long generator = null;
        Map<CorpusStage, Long> stages = null;
        CborReader.Field field;
        while ((field = reader.nextField(ELAPSED_FIELD)) != null) {
            switch (field.name()) {
                case TOTAL_FIELD -> total = reader.longValue(field);
                case GENERATOR_FIELD -> generator = reader.longValue(field);
                case STAGES_FIELD -> {
                    reader.requireMap(field);
                    stages = readStageElapsed(reader, field.path());
                }
                default -> reader.skipValue();
            }
        }
        return new Elapsed(
                CborReader.required(total, path(ELAPSED_FIELD, TOTAL_FIELD)),
                CborReader.required(generator, path(ELAPSED_FIELD, GENERATOR_FIELD)),
                CborReader.required(stages, path(ELAPSED_FIELD, STAGES_FIELD)));
    }

    /**
     * Reads per-stage elapsed time. A stage this build does not know is skipped, and a stage the
     * document omits contributes nothing, so a stage may be added or removed without a migration.
     */
    private static Map<CorpusStage, Long> readStageElapsed(CborReader reader, String path)
            throws IOException {
        var stages = new EnumMap<CorpusStage, Long>(CorpusStage.class);
        CborReader.Field field;
        while ((field = reader.nextField(path)) != null) {
            var stage = CorpusStage.fromMetadataName(field.name());
            if (stage.isPresent()) {
                stages.put(stage.orElseThrow(), reader.longValue(field));
            } else {
                reader.skipValue();
            }
        }
        return stages;
    }

    private static Generator readGenerator(CborReader reader) throws IOException {
        Long attempts = null;
        Long rejected = null;
        Long richnessRejected = null;
        Long duplicates = null;
        Long richnessSamples = null;
        Double minimumRichness = null;
        Double maximumRichness = null;
        Double averageRichness = null;
        CborReader.Field field;
        while ((field = reader.nextField(GENERATOR_FIELD)) != null) {
            switch (field.name()) {
                case ATTEMPTS_FIELD -> attempts = reader.longValue(field);
                case REJECTED_FIELD -> rejected = reader.longValue(field);
                case RICHNESS_REJECTED_FIELD -> richnessRejected = reader.longValue(field);
                case DUPLICATES_FIELD -> duplicates = reader.longValue(field);
                case RICHNESS_SAMPLES_FIELD -> richnessSamples = reader.longValue(field);
                case MINIMUM_RICHNESS_FIELD -> minimumRichness = reader.doubleValue(field);
                case MAXIMUM_RICHNESS_FIELD -> maximumRichness = reader.doubleValue(field);
                case AVERAGE_RICHNESS_FIELD -> averageRichness = reader.doubleValue(field);
                default -> reader.skipValue();
            }
        }
        return new Generator(
                CborReader.required(attempts, path(GENERATOR_FIELD, ATTEMPTS_FIELD)),
                CborReader.required(rejected, path(GENERATOR_FIELD, REJECTED_FIELD)),
                CborReader.required(
                        richnessRejected, path(GENERATOR_FIELD, RICHNESS_REJECTED_FIELD)),
                CborReader.required(duplicates, path(GENERATOR_FIELD, DUPLICATES_FIELD)),
                CborReader.required(
                        richnessSamples, path(GENERATOR_FIELD, RICHNESS_SAMPLES_FIELD)),
                CborReader.required(
                        minimumRichness, path(GENERATOR_FIELD, MINIMUM_RICHNESS_FIELD)),
                CborReader.required(
                        maximumRichness, path(GENERATOR_FIELD, MAXIMUM_RICHNESS_FIELD)),
                CborReader.required(
                        averageRichness, path(GENERATOR_FIELD, AVERAGE_RICHNESS_FIELD)));
    }

    private static String path(String map, String field) {
        return map + "." + field;
    }

    private record Elapsed(long total, long generator, Map<CorpusStage, Long> stages) {}

    private record Generator(
            long attempts,
            long rejected,
            long richnessRejected,
            long duplicates,
            long richnessSamples,
            double minimumRichness,
            double maximumRichness,
            double averageRichness) {}
}
