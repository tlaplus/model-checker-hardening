package io.github.tlaplus.hardening.corpus;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.fasterxml.jackson.dataformat.cbor.CBORGenerator;
import io.github.tlaplus.hardening.checker.CheckerFailure;
import io.github.tlaplus.hardening.checker.CheckerFailureCode;
import io.github.tlaplus.hardening.common.Diagnostics;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Encodes and decodes the extensible CBOR envelope used for corpus inputs. */
public final class CorpusInputCodec {
    private static final String KIND_FIELD = "kind";
    private static final String INPUT_FIELD = "input";
    private static final String GENERATION_FIELD = "gen";
    private static final String STAGES_FIELD = "stages";
    private static final String COHORT_FIELD = "cohort";
    private static final String RICHNESS_FIELD = "richness";
    private static final String VERDICT_FIELD = "verdict";
    private static final String CODE_FIELD = "code";
    private static final String DETAIL_FIELD = "detail";
    private static final String START_TIME_FIELD = "startTime";
    private static final String END_TIME_FIELD = "endTime";

    private static final CBORFactory FACTORY = new CBORFactory();
    private static final ObjectMapper MAPPER = new ObjectMapper(FACTORY);

    /** The CBOR tag of an epoch-based date/time, RFC 8949 section 3.4.2. */
    private static final int EPOCH_TAG = 1;

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
    public static CorpusInput decode(byte[] encoded) throws CorpusFormatException {
        Objects.requireNonNull(encoded, "encoded");
        try (var reader = CborReader.of(encoded)) {
            reader.startDocument();

            CorpusInput.Kind kind = null;
            byte[] input = null;
            CborReader.Field field;
            while ((field = reader.nextField(CborReader.ROOT)) != null) {
                switch (field.name()) {
                    case KIND_FIELD ->
                        kind = CorpusInput.Kind.fromEncodedName(reader.text(field));
                    case INPUT_FIELD -> input = reader.binary(field);
                    case GENERATION_FIELD -> {
                        reader.requireMap(field);
                        readGenerationMetadata(reader, field.path());
                    }
                    default -> reader.skipValue();
                }
            }

            var requiredKind = CborReader.required(kind, KIND_FIELD);
            var requiredInput = CborReader.required(input, INPUT_FIELD);
            reader.endDocument();
            return new CorpusInput(requiredKind, requiredInput);
        } catch (CorpusFormatException exception) {
            throw exception;
        } catch (IOException exception) {
            throw CborReader.invalidCbor(exception);
        }
    }

    /** Decodes the required input and every supported stage metadata field. */
    public static CorpusEnvelope decodeEnvelope(byte[] encoded) throws CorpusFormatException {
        var corpusInput = decode(encoded);
        GenerationMetadata generation = null;
        var stages = new ArrayList<StageMetadata>();
        try (var reader = CborReader.of(encoded)) {
            reader.startDocument();
            CborReader.Field field;
            while ((field = reader.nextField(CborReader.ROOT)) != null) {
                switch (field.name()) {
                    case GENERATION_FIELD -> {
                        reader.requireMap(field);
                        generation = readGenerationMetadata(reader, field.path());
                    }
                    case STAGES_FIELD -> {
                        reader.requireMap(field);
                        readStages(reader, field.path(), stages);
                    }
                    default -> reader.skipValue();
                }
            }
            return new CorpusEnvelope(corpusInput, Optional.ofNullable(generation), stages);
        } catch (CorpusFormatException exception) {
            throw exception;
        } catch (IOException exception) {
            throw CborReader.invalidCbor(exception);
        }
    }

    /** Returns a stage verdict when the document already contains one. */
    public static Optional<String> stageVerdict(byte[] encoded, String stage)
            throws CorpusFormatException {
        Objects.requireNonNull(stage, "stage");
        decode(encoded);
        return readStageMetadata(encoded, stage).map(StageMetadata::verdict);
    }

    /** Replaces one stage's metadata while preserving all unrelated fields. */
    public static byte[] withStageMetadata(byte[] encoded, StageMetadata metadata)
            throws CorpusFormatException {
        Objects.requireNonNull(metadata, "metadata");
        decode(encoded);
        var root = readTree(encoded);
        var stages = root.get(STAGES_FIELD);
        if (stages != null && !stages.isObject()) {
            throw CborReader.malformed("field '" + STAGES_FIELD + "' must be a map");
        }

        var output = new ByteArrayOutputStream();
        try (var generator = FACTORY.createGenerator(output)) {
            generator.setCodec(MAPPER);
            generator.writeStartObject(null, root.size() + (stages == null ? 1 : 0));
            for (Map.Entry<String, JsonNode> field : root.properties()) {
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
                for (Map.Entry<String, JsonNode> field : stages.properties()) {
                    var value = field.getValue();
                    if (value.isObject()
                            && value.has(VERDICT_FIELD)
                            && value.has(START_TIME_FIELD)
                            && value.has(END_TIME_FIELD)) {
                        readStageMetadata(encoded, field.getKey())
                                .ifPresent(stage -> existingMetadata.put(stage.stage(), stage));
                    }
                }
                for (Map.Entry<String, JsonNode> field : stages.properties()) {
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
            throw new CorpusFormatException(
                    "cannot encode CBOR metadata: " + Diagnostics.message(exception), exception);
        }
        return output.toByteArray();
    }

    private static JsonNode readTree(byte[] encoded) throws CorpusFormatException {
        try {
            return MAPPER.readTree(encoded);
        } catch (IOException exception) {
            throw CborReader.invalidCbor(exception);
        }
    }

    private static GenerationMetadata readGenerationMetadata(CborReader reader, String path)
            throws IOException {
        Integer cohort = null;
        Double richness = null;
        CborReader.Field field;
        while ((field = reader.nextField(path)) != null) {
            switch (field.name()) {
                case COHORT_FIELD -> cohort = reader.intValue(field);
                case RICHNESS_FIELD -> richness = reader.doubleValue(field);
                default -> reader.skipValue();
            }
        }
        try {
            return new GenerationMetadata(
                    CborReader.required(cohort, path + "." + COHORT_FIELD),
                    CborReader.required(richness, path + "." + RICHNESS_FIELD));
        } catch (IllegalArgumentException exception) {
            throw CborReader.malformed(
                    "invalid gen metadata: " + Diagnostics.message(exception));
        }
    }

    /** Returns the metadata one stage recorded, when the document already contains it. */
    private static Optional<StageMetadata> readStageMetadata(
            byte[] encoded, String requestedStage) throws CorpusFormatException {
        try (var reader = CborReader.of(encoded)) {
            reader.startDocument();
            CborReader.Field field;
            while ((field = reader.nextField(CborReader.ROOT)) != null) {
                if (!STAGES_FIELD.equals(field.name())) {
                    reader.skipValue();
                    continue;
                }
                reader.requireMap(field);
                CborReader.Field stage;
                while ((stage = reader.nextField(field.path())) != null) {
                    if (!requestedStage.equals(stage.name())) {
                        reader.skipValue();
                        continue;
                    }
                    reader.requireMap(stage);
                    return Optional.of(readStageFields(reader, stage));
                }
            }
            return Optional.empty();
        } catch (CorpusFormatException exception) {
            throw exception;
        } catch (IOException | IllegalArgumentException exception) {
            throw new CorpusFormatException(
                    "invalid stage metadata: " + Diagnostics.message(exception), exception);
        }
    }

    private static void readStages(CborReader reader, String path, List<StageMetadata> metadata)
            throws IOException {
        CborReader.Field stage;
        while ((stage = reader.nextField(path)) != null) {
            reader.requireMap(stage);
            metadata.add(readStageFields(reader, stage));
        }
    }

    /** Reads one stage's metadata from the map that {@code stage} names. */
    private static StageMetadata readStageFields(CborReader reader, CborReader.Field stage)
            throws IOException {
        String verdict = null;
        Instant startTime = null;
        Instant endTime = null;
        Integer failureCode = null;
        String failureDetail = null;
        CborReader.Field field;
        while ((field = reader.nextField(stage.path())) != null) {
            switch (field.name()) {
                case VERDICT_FIELD -> verdict = reader.text(field);
                case START_TIME_FIELD -> startTime = reader.epoch(field);
                case END_TIME_FIELD -> endTime = reader.epoch(field);
                case CODE_FIELD -> failureCode = reader.intValue(field);
                case DETAIL_FIELD -> failureDetail = reader.text(field);
                default -> reader.skipValue();
            }
        }
        var requiredVerdict = CborReader.required(verdict, stage.path() + "." + VERDICT_FIELD);
        var requiredStart = CborReader.required(startTime, stage.path() + "." + START_TIME_FIELD);
        var requiredEnd = CborReader.required(endTime, stage.path() + "." + END_TIME_FIELD);
        if (failureDetail != null && failureCode == null) {
            throw CborReader.malformed(
                    "field '"
                            + stage.path()
                            + "."
                            + DETAIL_FIELD
                            + "' requires field '"
                            + CODE_FIELD
                            + "'");
        }
        try {
            var failure = failureCode == null
                    ? Optional.<CheckerFailure>empty()
                    : Optional.of(new CheckerFailure(
                            CheckerFailureCode.fromEncodedCode(failureCode),
                            Optional.ofNullable(failureDetail)));
            return new StageMetadata(
                    stage.name(), requiredVerdict, requiredStart, requiredEnd, failure);
        } catch (IllegalArgumentException exception) {
            throw CborReader.malformed(
                    "invalid metadata for stage '"
                            + stage.name()
                            + "': "
                            + Diagnostics.message(exception));
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
        generator.writeStringField(VERDICT_FIELD, metadata.verdict());
        if (metadata.failure().isPresent()) {
            var failure = metadata.failure().orElseThrow();
            generator.writeNumberField(CODE_FIELD, failure.code().encodedCode());
            if (failure.detail().isPresent()) {
                generator.writeStringField(DETAIL_FIELD, failure.detail().orElseThrow());
            }
        }
        generator.writeFieldName(START_TIME_FIELD);
        writeEpoch(generator, metadata.startTime());
        generator.writeFieldName(END_TIME_FIELD);
        writeEpoch(generator, metadata.endTime());
        if (previous != null) {
            for (Map.Entry<String, JsonNode> field : previous.properties()) {
                if (!isStageMetadataField(field.getKey())) {
                    generator.writeFieldName(field.getKey());
                    generator.writeTree(field.getValue());
                }
            }
        }
        generator.writeEndObject();
    }

    private static boolean isStageMetadataField(String field) {
        return VERDICT_FIELD.equals(field)
                || CODE_FIELD.equals(field)
                || DETAIL_FIELD.equals(field)
                || START_TIME_FIELD.equals(field)
                || END_TIME_FIELD.equals(field);
    }

    private static void writeEpoch(CBORGenerator generator, Instant instant) throws IOException {
        generator.writeTag(EPOCH_TAG);
        generator.writeNumber(instant.getEpochSecond());
    }

    private static void writeGenerationMetadata(
            CBORGenerator generator, GenerationMetadata metadata) throws IOException {
        generator.writeFieldName(GENERATION_FIELD);
        generator.writeStartObject(null, 2);
        generator.writeNumberField(COHORT_FIELD, metadata.cohort());
        generator.writeNumberField(RICHNESS_FIELD, metadata.richness());
        generator.writeEndObject();
    }
}
