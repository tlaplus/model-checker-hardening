package io.github.tlaplus.hardening.corpus;

import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.fasterxml.jackson.dataformat.cbor.CBORParser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Objects;

/** Encodes and decodes the aggregate workflow-statistics CBOR document. */
final class CorpusRunStatisticsCodec {
    private static final String ELAPSED_FIELD = "elapsedNs";
    private static final String GENERATOR_FIELD = "generator";
    private static final CBORFactory FACTORY = new CBORFactory();

    private CorpusRunStatisticsCodec() {}

    static byte[] encode(CorpusRunStatistics statistics) throws IOException {
        Objects.requireNonNull(statistics, "statistics");
        var output = new ByteArrayOutputStream();
        try (var generator = FACTORY.createGenerator(output)) {
            generator.writeStartObject(null, 2);
            generator.writeFieldName(ELAPSED_FIELD);
            generator.writeStartObject(null, 5);
            generator.writeNumberField("total", statistics.totalElapsedNanos());
            generator.writeNumberField("generator", statistics.generatorElapsedNanos());
            generator.writeNumberField("parser", statistics.parserElapsedNanos());
            generator.writeNumberField("tlc", statistics.tlcElapsedNanos());
            generator.writeNumberField("apalache", statistics.apalacheElapsedNanos());
            generator.writeEndObject();
            generator.writeFieldName(GENERATOR_FIELD);
            generator.writeStartObject(null, 8);
            generator.writeNumberField("attempts", statistics.generatorAttempts());
            generator.writeNumberField("rejected", statistics.generatorRejected());
            generator.writeNumberField(
                    "richnessRejected", statistics.generatorRichnessRejected());
            generator.writeNumberField("duplicates", statistics.generatorDuplicates());
            generator.writeNumberField("richnessSamples", statistics.richnessSamples());
            generator.writeNumberField("minimumRichness", statistics.minimumRichness());
            generator.writeNumberField("maximumRichness", statistics.maximumRichness());
            generator.writeNumberField("averageRichness", statistics.averageRichness());
            generator.writeEndObject();
            generator.writeEndObject();
        }
        return output.toByteArray();
    }

    static CorpusRunStatistics decode(byte[] encoded) throws CorpusStatisticsFormatException {
        Objects.requireNonNull(encoded, "encoded");
        try (var parser = FACTORY.createParser(encoded)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw format("top-level value must be a map");
            }
            Elapsed elapsed = null;
            Generator generator = null;
            var fields = new HashSet<String>();
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                var field = readFieldName(parser, fields, "");
                var value = parser.nextToken();
                if (value == null) {
                    throw format("field has no value: " + field);
                }
                switch (field) {
                    case ELAPSED_FIELD -> {
                        requireMap(value, ELAPSED_FIELD);
                        elapsed = readElapsed(parser);
                    }
                    case GENERATOR_FIELD -> {
                        requireMap(value, GENERATOR_FIELD);
                        generator = readGenerator(parser);
                    }
                    default -> parser.skipChildren();
                }
            }
            if (elapsed == null) {
                throw format("missing field: " + ELAPSED_FIELD);
            }
            if (generator == null) {
                throw format("missing field: " + GENERATOR_FIELD);
            }
            if (parser.nextToken() != null) {
                throw format("trailing data after top-level map");
            }
            try {
                return new CorpusRunStatistics(
                        elapsed.total(),
                        elapsed.generator(),
                        elapsed.parser(),
                        elapsed.tlc(),
                        elapsed.apalache(),
                        generator.attempts(),
                        generator.rejected(),
                        generator.richnessRejected(),
                        generator.duplicates(),
                        generator.richnessSamples(),
                        generator.minimumRichness(),
                        generator.maximumRichness(),
                        generator.averageRichness());
            } catch (IllegalArgumentException exception) {
                throw format("invalid workflow statistics: " + diagnostic(exception));
            }
        } catch (CorpusStatisticsFormatException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new CorpusStatisticsFormatException(
                    "invalid CBOR: " + diagnostic(exception), exception);
        }
    }

    private static Elapsed readElapsed(CBORParser parser) throws IOException {
        Long total = null;
        Long generator = null;
        Long parserElapsed = null;
        Long tlc = null;
        Long apalache = null;
        var fields = new HashSet<String>();
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            var field = readFieldName(parser, fields, ELAPSED_FIELD + ".");
            var value = parser.nextToken();
            switch (field) {
                case "total" -> total = readLong(parser, value, "elapsedNs.total");
                case "generator" ->
                    generator = readLong(parser, value, "elapsedNs.generator");
                case "parser" -> parserElapsed = readLong(parser, value, "elapsedNs.parser");
                case "tlc" -> tlc = readLong(parser, value, "elapsedNs.tlc");
                case "apalache" -> apalache = readLong(parser, value, "elapsedNs.apalache");
                default -> parser.skipChildren();
            }
        }
        return new Elapsed(
                required(total, "elapsedNs.total"),
                required(generator, "elapsedNs.generator"),
                required(parserElapsed, "elapsedNs.parser"),
                required(tlc, "elapsedNs.tlc"),
                required(apalache, "elapsedNs.apalache"));
    }

    private static Generator readGenerator(CBORParser parser) throws IOException {
        Long attempts = null;
        Long rejected = null;
        Long richnessRejected = null;
        Long duplicates = null;
        Long richnessSamples = null;
        Double minimumRichness = null;
        Double maximumRichness = null;
        Double averageRichness = null;
        var fields = new HashSet<String>();
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            var field = readFieldName(parser, fields, GENERATOR_FIELD + ".");
            var value = parser.nextToken();
            switch (field) {
                case "attempts" -> attempts = readLong(parser, value, "generator.attempts");
                case "rejected" -> rejected = readLong(parser, value, "generator.rejected");
                case "richnessRejected" ->
                    richnessRejected =
                            readLong(parser, value, "generator.richnessRejected");
                case "duplicates" -> duplicates = readLong(parser, value, "generator.duplicates");
                case "richnessSamples" ->
                    richnessSamples = readLong(parser, value, "generator.richnessSamples");
                case "minimumRichness" ->
                    minimumRichness = readDouble(parser, value, "generator.minimumRichness");
                case "maximumRichness" ->
                    maximumRichness = readDouble(parser, value, "generator.maximumRichness");
                case "averageRichness" ->
                    averageRichness = readDouble(parser, value, "generator.averageRichness");
                default -> parser.skipChildren();
            }
        }
        return new Generator(
                required(attempts, "generator.attempts"),
                required(rejected, "generator.rejected"),
                required(richnessRejected, "generator.richnessRejected"),
                required(duplicates, "generator.duplicates"),
                required(richnessSamples, "generator.richnessSamples"),
                required(minimumRichness, "generator.minimumRichness"),
                required(maximumRichness, "generator.maximumRichness"),
                required(averageRichness, "generator.averageRichness"));
    }

    private static String readFieldName(
            CBORParser parser, HashSet<String> fields, String prefix)
            throws CorpusStatisticsFormatException, IOException {
        if (!parser.hasToken(JsonToken.FIELD_NAME)) {
            throw format("map keys must be text strings");
        }
        var field = parser.currentName();
        if (!fields.add(field)) {
            throw format("duplicate field: " + prefix + field);
        }
        return field;
    }

    private static long readLong(CBORParser parser, JsonToken token, String path)
            throws IOException {
        if (token != JsonToken.VALUE_NUMBER_INT) {
            throw format("field '" + path + "' must be an integer");
        }
        return parser.getLongValue();
    }

    private static double readDouble(CBORParser parser, JsonToken token, String path)
            throws IOException {
        if (token != JsonToken.VALUE_NUMBER_INT && token != JsonToken.VALUE_NUMBER_FLOAT) {
            throw format("field '" + path + "' must be a number");
        }
        return parser.getDoubleValue();
    }

    private static <T> T required(T value, String path) throws CorpusStatisticsFormatException {
        if (value == null) {
            throw format("missing field: " + path);
        }
        return value;
    }

    private static void requireMap(JsonToken token, String path)
            throws CorpusStatisticsFormatException {
        if (token != JsonToken.START_OBJECT) {
            throw format("field '" + path + "' must be a map");
        }
    }

    private static CorpusStatisticsFormatException format(String message) {
        return new CorpusStatisticsFormatException(message);
    }

    private static String diagnostic(Throwable exception) {
        var message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message;
    }

    private record Elapsed(long total, long generator, long parser, long tlc, long apalache) {}

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
