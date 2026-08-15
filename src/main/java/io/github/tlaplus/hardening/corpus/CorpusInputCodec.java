package io.github.tlaplus.hardening.corpus;

import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.fasterxml.jackson.dataformat.cbor.CBORGenerator;
import com.fasterxml.jackson.dataformat.cbor.CBORParser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;

/** Encodes and decodes the extensible CBOR envelope used for corpus inputs. */
public final class CorpusInputCodec {
    private static final String KIND_FIELD = "kind";
    private static final String INPUT_FIELD = "input";
    private static final String STAGES_FIELD = "stages";
    private static final CBORFactory FACTORY = new CBORFactory();
    private static final ObjectMapper MAPPER = new ObjectMapper(FACTORY);

    private CorpusInputCodec() {}

    /** Encodes the required fields of one corpus input as a definite-length CBOR map. */
    public static byte[] encode(CorpusInput corpusInput) throws IOException {
        Objects.requireNonNull(corpusInput, "corpusInput");
        var output = new ByteArrayOutputStream();
        try (var generator = FACTORY.createGenerator(output)) {
            generator.writeStartObject(null, 2);
            generator.writeStringField(KIND_FIELD, corpusInput.kind().encodedName());
            generator.writeFieldName(INPUT_FIELD);
            generator.writeBinary(corpusInput.input());
            generator.writeEndObject();
        }
        return output.toByteArray();
    }

    /**
     * Decodes the required fields and ignores unknown metadata fields.
     *
     * <p>The complete byte array must contain exactly one CBOR map. Field order is insignificant,
     * but duplicate fields are rejected to avoid ambiguous input identities.
     */
    public static CorpusInput decode(byte[] encoded) throws CorpusInputFormatException {
        Objects.requireNonNull(encoded, "encoded");
        try (var parser = FACTORY.createParser(encoded)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw format("top-level value must be a map");
            }

            CorpusInput.Kind kind = null;
            byte[] input = null;
            var fields = new HashSet<String>();
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                if (!parser.hasToken(JsonToken.FIELD_NAME)) {
                    throw format("map keys must be text strings");
                }
                var field = parser.currentName();
                if (!fields.add(field)) {
                    throw format("duplicate field: " + field);
                }
                var valueToken = parser.nextToken();
                if (valueToken == null) {
                    throw format("field has no value: " + field);
                }

                switch (field) {
                    case KIND_FIELD -> {
                        if (valueToken != JsonToken.VALUE_STRING) {
                            throw format("field 'kind' must be a text string");
                        }
                        kind = CorpusInput.Kind.fromEncodedName(parser.getText());
                    }
                    case INPUT_FIELD -> {
                        if (valueToken != JsonToken.VALUE_EMBEDDED_OBJECT) {
                            throw format("field 'input' must be a byte string");
                        }
                        input = parser.getBinaryValue();
                    }
                    default -> parser.skipChildren();
                }
            }

            if (kind == null) {
                throw format("missing field: kind");
            }
            if (input == null) {
                throw format("missing field: input");
            }
            if (parser.nextToken() != null) {
                throw format("trailing data after top-level map");
            }
            return new CorpusInput(kind, input);
        } catch (CorpusInputFormatException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new CorpusInputFormatException(
                    "invalid CBOR: " + diagnostic(exception), exception);
        }
    }

    /** Returns a stage verdict when the document already contains one. */
    public static Optional<String> stageVerdict(byte[] encoded, String stage)
            throws CorpusInputFormatException {
        Objects.requireNonNull(stage, "stage");
        decode(encoded);
        try (var parser = FACTORY.createParser(encoded)) {
            parser.nextToken();
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                var field = parser.currentName();
                var value = parser.nextToken();
                if (STAGES_FIELD.equals(field)) {
                    if (value != JsonToken.START_OBJECT) {
                        throw format("field 'stages' must be a map");
                    }
                    return readStageVerdict(parser, stage);
                }
                parser.skipChildren();
            }
            return Optional.empty();
        } catch (CorpusInputFormatException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new CorpusInputFormatException(
                    "invalid CBOR: " + diagnostic(exception), exception);
        }
    }

    /** Replaces one stage's metadata while preserving all unrelated fields. */
    public static byte[] withStageMetadata(byte[] encoded, StageMetadata metadata)
            throws CorpusInputFormatException {
        Objects.requireNonNull(metadata, "metadata");
        decode(encoded);
        var root = readTree(encoded);
        var stages = root.get(STAGES_FIELD);
        if (stages != null && !stages.isObject()) {
            throw format("field 'stages' must be a map");
        }

        var output = new ByteArrayOutputStream();
        try (var generator = FACTORY.createGenerator(output)) {
            generator.setCodec(MAPPER);
            generator.writeStartObject(null, root.size() + (stages == null ? 1 : 0));
            var rootFields = root.properties().iterator();
            while (rootFields.hasNext()) {
                var field = rootFields.next();
                if (!STAGES_FIELD.equals(field.getKey())) {
                    generator.writeFieldName(field.getKey());
                    generator.writeTree(field.getValue());
                }
            }

            generator.writeFieldName(STAGES_FIELD);
            var stageCount = stages == null ? 0 : stages.size();
            var replacing = stages != null && stages.has(metadata.stage());
            generator.writeStartObject(null, stageCount + (replacing ? 0 : 1));
            if (stages != null) {
                var stageFields = stages.properties().iterator();
                while (stageFields.hasNext()) {
                    var field = stageFields.next();
                    if (!metadata.stage().equals(field.getKey())) {
                        generator.writeFieldName(field.getKey());
                        generator.writeTree(field.getValue());
                    }
                }
            }
            writeStageMetadata(generator, metadata);
            generator.writeEndObject();
            generator.writeEndObject();
        } catch (IOException exception) {
            throw new CorpusInputFormatException(
                    "cannot encode CBOR metadata: " + diagnostic(exception), exception);
        }
        return output.toByteArray();
    }

    private static JsonNode readTree(byte[] encoded) throws CorpusInputFormatException {
        try {
            return MAPPER.readTree(encoded);
        } catch (IOException exception) {
            throw new CorpusInputFormatException(
                    "invalid CBOR: " + diagnostic(exception), exception);
        }
    }

    private static Optional<String> readStageVerdict(CBORParser parser, String requestedStage)
            throws IOException {
        var stageNames = new HashSet<String>();
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            if (!parser.hasToken(JsonToken.FIELD_NAME)) {
                throw format("field 'stages' keys must be text strings");
            }
            var stage = parser.currentName();
            if (!stageNames.add(stage)) {
                throw format("duplicate stage field: " + stage);
            }
            var value = parser.nextToken();
            if (!requestedStage.equals(stage)) {
                parser.skipChildren();
                continue;
            }
            if (value != JsonToken.START_OBJECT) {
                throw format("field 'stages." + stage + "' must be a map");
            }
            return Optional.of(readVerdictMetadata(parser, stage));
        }
        return Optional.empty();
    }

    private static String readVerdictMetadata(CBORParser parser, String stage)
            throws IOException {
        String verdict = null;
        var startTime = false;
        var endTime = false;
        var fields = new HashSet<String>();
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            if (!parser.hasToken(JsonToken.FIELD_NAME)) {
                throw format("field 'stages." + stage + "' keys must be text strings");
            }
            var field = parser.currentName();
            if (!fields.add(field)) {
                throw format("duplicate field: stages." + stage + "." + field);
            }
            var value = parser.nextToken();
            switch (field) {
                case "verdict" -> {
                    if (value != JsonToken.VALUE_STRING) {
                        throw format(
                                "field 'stages." + stage + ".verdict' must be a text string");
                    }
                    verdict = parser.getText();
                }
                case "startTime" -> {
                    requireTaggedEpoch(parser, value, stage, field);
                    startTime = true;
                }
                case "endTime" -> {
                    requireTaggedEpoch(parser, value, stage, field);
                    endTime = true;
                }
                default -> parser.skipChildren();
            }
        }
        if (verdict == null) {
            throw format("missing field: stages." + stage + ".verdict");
        }
        if (!startTime) {
            throw format("missing field: stages." + stage + ".startTime");
        }
        if (!endTime) {
            throw format("missing field: stages." + stage + ".endTime");
        }
        return verdict;
    }

    private static void requireTaggedEpoch(
            CBORParser parser, JsonToken token, String stage, String field)
            throws CorpusInputFormatException {
        if ((token != JsonToken.VALUE_NUMBER_INT && token != JsonToken.VALUE_NUMBER_FLOAT)
                || !parser.getCurrentTags().contains(1)) {
            throw format(
                    "field 'stages."
                            + stage
                            + "."
                            + field
                            + "' must be a tag-1 epoch number");
        }
    }

    private static void writeStageMetadata(CBORGenerator generator, StageMetadata metadata)
            throws IOException {
        generator.writeFieldName(metadata.stage());
        generator.writeStartObject(null, 3);
        generator.writeStringField("verdict", metadata.verdict());
        generator.writeFieldName("startTime");
        generator.writeTag(1);
        generator.writeNumber(metadata.startTime().getEpochSecond());
        generator.writeFieldName("endTime");
        generator.writeTag(1);
        generator.writeNumber(metadata.endTime().getEpochSecond());
        generator.writeEndObject();
    }

    private static CorpusInputFormatException format(String message) {
        return new CorpusInputFormatException(message);
    }

    private static String diagnostic(Throwable exception) {
        var message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message;
    }
}
