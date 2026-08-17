package io.github.tlaplus.hardening.corpus;

import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.fasterxml.jackson.dataformat.cbor.CBORGenerator;
import com.fasterxml.jackson.dataformat.cbor.CBORParser;
import io.github.tlaplus.hardening.checker.CheckerFailure;
import io.github.tlaplus.hardening.checker.CheckerFailureCode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Encodes and decodes the extensible CBOR envelope used for corpus inputs. */
public final class CorpusInputCodec {
    private static final String KIND_FIELD = "kind";
    private static final String INPUT_FIELD = "input";
    private static final String GENERATION_FIELD = "gen";
    private static final String STAGES_FIELD = "stages";
    private static final CBORFactory FACTORY = new CBORFactory();
    private static final ObjectMapper MAPPER = new ObjectMapper(FACTORY);

    private CorpusInputCodec() {}

    /** Encodes the required fields of one corpus input as a definite-length CBOR map. */
    public static byte[] encode(CorpusInput corpusInput) throws IOException {
        return encode(corpusInput, Optional.empty());
    }

    /** Encodes the required fields and admission-time PBT metadata. */
    public static byte[] encode(CorpusInput corpusInput, GenerationMetadata generationMetadata)
            throws IOException {
        return encode(
                corpusInput,
                Optional.of(Objects.requireNonNull(generationMetadata, "generationMetadata")));
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private static byte[] encode(
            CorpusInput corpusInput, Optional<GenerationMetadata> generationMetadata)
            throws IOException {
        Objects.requireNonNull(corpusInput, "corpusInput");
        Objects.requireNonNull(generationMetadata, "generationMetadata");
        var output = new ByteArrayOutputStream();
        try (var generator = FACTORY.createGenerator(output)) {
            generator.writeStartObject(null, generationMetadata.isPresent() ? 3 : 2);
            generator.writeStringField(KIND_FIELD, corpusInput.kind().encodedName());
            generator.writeFieldName(INPUT_FIELD);
            generator.writeBinary(corpusInput.input());
            if (generationMetadata.isPresent()) {
                writeGenerationMetadata(generator, generationMetadata.orElseThrow());
            }
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
                    case GENERATION_FIELD -> {
                        if (valueToken != JsonToken.START_OBJECT) {
                            throw format("field 'gen' must be a map");
                        }
                        readGenerationMetadata(parser);
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

    /** Decodes the required input and every supported stage metadata field. */
    public static CorpusEnvelope decodeEnvelope(byte[] encoded)
            throws CorpusInputFormatException {
        var corpusInput = decode(encoded);
        GenerationMetadata generation = null;
        var stages = new ArrayList<StageMetadata>();
        try (var parser = FACTORY.createParser(encoded)) {
            parser.nextToken();
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                var field = parser.currentName();
                var value = parser.nextToken();
                if (GENERATION_FIELD.equals(field)) {
                    generation = readGenerationMetadata(parser);
                } else if (STAGES_FIELD.equals(field)) {
                    if (value != JsonToken.START_OBJECT) {
                        throw format("field 'stages' must be a map");
                    }
                    readStages(parser, stages);
                } else {
                    parser.skipChildren();
                }
            }
            return new CorpusEnvelope(corpusInput, Optional.ofNullable(generation), stages);
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
            for (java.util.Map.Entry<String, JsonNode> field : root.properties()) {
                if (!STAGES_FIELD.equals(field.getKey())) {
                    generator.writeFieldName(field.getKey());
                    generator.writeTree(field.getValue());
                }
            }

            generator.writeFieldName(STAGES_FIELD);
            var stageCount = stages == null ? 0 : stages.size();
            var replacing = stages != null && stages.has(metadata.stage());
            generator.writeStartObject(null, stageCount + (replacing ? 0 : 1));
            var existingMetadata = new HashMap<String, StageMetadata>();
            if (stages != null) {
                for (java.util.Map.Entry<String, JsonNode> field : stages.properties()) {
                    var value = field.getValue();
                    if (value.isObject()
                            && value.has("verdict")
                            && value.has("startTime")
                            && value.has("endTime")) {
                        readStageMetadata(encoded, field.getKey())
                                .ifPresent(stage -> existingMetadata.put(stage.stage(), stage));
                    }
                }
            }
            if (stages != null) {
                for (java.util.Map.Entry<String, JsonNode> field : stages.properties()) {
                    if (!metadata.stage().equals(field.getKey())) {
                        var existing = existingMetadata.get(field.getKey());
                        if (existing == null) {
                            generator.writeFieldName(field.getKey());
                            generator.writeTree(field.getValue());
                        } else {
                            writeStageMetadata(generator, existing, field.getValue());
                        }
                    }
                }
            }
            writeStageMetadata(
                    generator,
                    metadata,
                    stages == null ? null : stages.get(metadata.stage()));
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

    private static GenerationMetadata readGenerationMetadata(CBORParser parser)
            throws IOException {
        Integer cohort = null;
        Double richness = null;
        var fields = new HashSet<String>();
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            if (!parser.hasToken(JsonToken.FIELD_NAME)) {
                throw format("field 'gen' keys must be text strings");
            }
            var field = parser.currentName();
            if (!fields.add(field)) {
                throw format("duplicate field: gen." + field);
            }
            var value = parser.nextToken();
            switch (field) {
                case "cohort" -> {
                    if (value != JsonToken.VALUE_NUMBER_INT) {
                        throw format("field 'gen.cohort' must be an integer");
                    }
                    try {
                        cohort = Math.toIntExact(parser.getLongValue());
                    } catch (ArithmeticException exception) {
                        throw format("field 'gen.cohort' is outside the supported integer range");
                    }
                }
                case "richness" -> {
                    if (value != JsonToken.VALUE_NUMBER_INT
                            && value != JsonToken.VALUE_NUMBER_FLOAT) {
                        throw format("field 'gen.richness' must be a number");
                    }
                    richness = parser.getDoubleValue();
                }
                default -> parser.skipChildren();
            }
        }
        if (cohort == null) {
            throw format("missing field: gen.cohort");
        }
        if (richness == null) {
            throw format("missing field: gen.richness");
        }
        try {
            return new GenerationMetadata(cohort, richness);
        } catch (IllegalArgumentException exception) {
            throw format("invalid gen metadata: " + diagnostic(exception));
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
            return Optional.of(readStageFields(parser, stage).verdict());
        }
        return Optional.empty();
    }

    private static Optional<StageMetadata> readStageMetadata(
            byte[] encoded, String requestedStage) throws CorpusInputFormatException {
        try (var parser = FACTORY.createParser(encoded)) {
            parser.nextToken();
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                var field = parser.currentName();
                var value = parser.nextToken();
                if (!STAGES_FIELD.equals(field)) {
                    parser.skipChildren();
                    continue;
                }
                if (value != JsonToken.START_OBJECT) {
                    throw format("field 'stages' must be a map");
                }
                while (parser.nextToken() != JsonToken.END_OBJECT) {
                    var stage = parser.currentName();
                    var stageValue = parser.nextToken();
                    if (!requestedStage.equals(stage)) {
                        parser.skipChildren();
                        continue;
                    }
                    if (stageValue != JsonToken.START_OBJECT) {
                        throw format("field 'stages." + stage + "' must be a map");
                    }
                    return Optional.of(readStageFields(parser, stage));
                }
            }
            return Optional.empty();
        } catch (CorpusInputFormatException exception) {
            throw exception;
        } catch (IOException | IllegalArgumentException exception) {
            throw new CorpusInputFormatException(
                    "invalid stage metadata: " + diagnostic(exception), exception);
        }
    }

    private static void readStages(CBORParser parser, List<StageMetadata> metadata)
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
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw format("field 'stages." + stage + "' must be a map");
            }
            metadata.add(readStageFields(parser, stage));
        }
    }

    private static StageMetadata readStageFields(CBORParser parser, String stage)
            throws IOException {
        String verdict = null;
        Instant startTime = null;
        Instant endTime = null;
        Integer failureCode = null;
        String failureDetail = null;
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
                    startTime = readTaggedEpoch(parser, value, stage, field);
                }
                case "endTime" -> {
                    endTime = readTaggedEpoch(parser, value, stage, field);
                }
                case "code" -> {
                    if (value != JsonToken.VALUE_NUMBER_INT) {
                        throw format(
                                "field 'stages." + stage + ".code' must be an integer");
                    }
                    try {
                        failureCode = Math.toIntExact(parser.getLongValue());
                    } catch (ArithmeticException exception) {
                        throw format("field 'stages."
                                + stage
                                + ".code' is outside the supported integer range");
                    }
                }
                case "detail" -> {
                    if (value != JsonToken.VALUE_STRING) {
                        throw format(
                                "field 'stages." + stage + ".detail' must be a text string");
                    }
                    failureDetail = parser.getText();
                }
                default -> parser.skipChildren();
            }
        }
        if (verdict == null) {
            throw format("missing field: stages." + stage + ".verdict");
        }
        if (startTime == null) {
            throw format("missing field: stages." + stage + ".startTime");
        }
        if (endTime == null) {
            throw format("missing field: stages." + stage + ".endTime");
        }
        if (failureDetail != null && failureCode == null) {
            throw format("field 'stages." + stage + ".detail' requires field 'code'");
        }
        try {
            var failure = failureCode == null
                    ? Optional.<CheckerFailure>empty()
                    : Optional.of(new CheckerFailure(
                            CheckerFailureCode.fromEncodedCode(failureCode),
                            Optional.ofNullable(failureDetail)));
            return new StageMetadata(stage, verdict, startTime, endTime, failure);
        } catch (IllegalArgumentException exception) {
            throw format("invalid metadata for stage '"
                    + stage
                    + "': "
                    + diagnostic(exception));
        }
    }

    private static Instant readTaggedEpoch(
            CBORParser parser, JsonToken token, String stage, String field)
            throws IOException {
        var path = "stages." + stage + "." + field;
        if ((token != JsonToken.VALUE_NUMBER_INT && token != JsonToken.VALUE_NUMBER_FLOAT)
                || !parser.getCurrentTags().contains(1)) {
            throw format("field '" + path + "' must be a tag-1 epoch number");
        }
        try {
            var epoch = parser.getDecimalValue();
            var seconds = epoch.setScale(0, RoundingMode.FLOOR).longValueExact();
            var nanos = epoch.subtract(java.math.BigDecimal.valueOf(seconds))
                    .movePointRight(9)
                    .intValueExact();
            return Instant.ofEpochSecond(seconds, nanos);
        } catch (ArithmeticException | DateTimeException | NumberFormatException exception) {
            throw format("field '" + path + "' is outside the supported timestamp range");
        }
    }

    private static void writeStageMetadata(CBORGenerator generator, StageMetadata metadata)
            throws IOException {
        writeStageMetadata(generator, metadata, null);
    }

    private static void writeStageMetadata(
            CBORGenerator generator, StageMetadata metadata, JsonNode previous)
            throws IOException {
        generator.writeFieldName(metadata.stage());
        var extraFields = previous == null
                ? 0
                : (int) previous.properties().stream()
                        .filter(field -> !isStageMetadataField(field.getKey()))
                        .count();
        var failureFields = metadata.failure()
                .map(failure -> failure.detail().isPresent() ? 2 : 1)
                .orElse(0);
        generator.writeStartObject(null, 3 + failureFields + extraFields);
        generator.writeStringField("verdict", metadata.verdict());
        if (metadata.failure().isPresent()) {
            var failure = metadata.failure().orElseThrow();
            generator.writeNumberField("code", failure.code().encodedCode());
            if (failure.detail().isPresent()) {
                generator.writeStringField("detail", failure.detail().orElseThrow());
            }
        }
        generator.writeFieldName("startTime");
        writeEpoch(generator, metadata.startTime());
        generator.writeFieldName("endTime");
        writeEpoch(generator, metadata.endTime());
        if (previous != null) {
            for (java.util.Map.Entry<String, JsonNode> field : previous.properties()) {
                if (!isStageMetadataField(field.getKey())) {
                    generator.writeFieldName(field.getKey());
                    generator.writeTree(field.getValue());
                }
            }
        }
        generator.writeEndObject();
    }

    private static boolean isStageMetadataField(String field) {
        return "verdict".equals(field)
                || "code".equals(field)
                || "detail".equals(field)
                || "startTime".equals(field)
                || "endTime".equals(field);
    }

    private static void writeEpoch(CBORGenerator generator, Instant instant) throws IOException {
        generator.writeTag(1);
        generator.writeNumber(instant.getEpochSecond());
    }

    private static void writeGenerationMetadata(
            CBORGenerator generator, GenerationMetadata metadata) throws IOException {
        generator.writeFieldName(GENERATION_FIELD);
        generator.writeStartObject(null, 2);
        generator.writeNumberField("cohort", metadata.cohort());
        generator.writeNumberField("richness", metadata.richness());
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
