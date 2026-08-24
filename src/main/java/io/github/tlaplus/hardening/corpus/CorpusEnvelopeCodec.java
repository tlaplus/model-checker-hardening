package io.github.tlaplus.hardening.corpus;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.tlaplus.hardening.checker.CheckerFailure;
import io.github.tlaplus.hardening.checker.CheckerFailureCode;
import io.github.tlaplus.hardening.common.Diagnostics;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Reads and rewrites the {@code stages} metadata of a corpus envelope.
 *
 * <p>A stage may add fields this build does not know, and a document may record a stage this build
 * has never heard of. Both survive a rewrite: {@link #withStageMetadata} preserves the fields it
 * does not model and passes an unrecognized stage entry through unchanged.
 */
public final class CorpusEnvelopeCodec {
    private static final String STAGES_FIELD = CorpusInputCodec.STAGES_FIELD;
    private static final String VERDICT_FIELD = "verdict";
    private static final String CODE_FIELD = "code";
    private static final String DETAIL_FIELD = "detail";
    private static final String START_TIME_FIELD = "startTime";
    private static final String END_TIME_FIELD = "endTime";

    private static final ObjectMapper MAPPER = new ObjectMapper(CorpusCbor.FACTORY);

    private CorpusEnvelopeCodec() {}

    /** Decodes the required input and every stage's metadata in one pass. */
    public static CorpusEnvelope decodeEnvelope(byte[] encoded) throws CorpusFormatException {
        var stages = new ArrayList<StageMetadata>();
        var document = CorpusInputCodec.read(
                encoded, (reader, field) -> readStages(reader, field, everyStage(), stages));
        return new CorpusEnvelope(document.corpusInput(), document.generation(), stages);
    }

    /**
     * Replaces one stage's metadata while preserving all unrelated fields.
     *
     * <p>The document is read twice and written once. The first read is the strict one: it decides
     * that the document is a well-formed corpus input and parses every stage entry shaped like
     * stage metadata, so a stage keeps its tagged timestamps when it is written back. The second is
     * a tree, which supplies the raw form of everything this build does not model.
     */
    public static byte[] withStageMetadata(byte[] encoded, StageMetadata metadata)
            throws CorpusFormatException {
        Objects.requireNonNull(metadata, "metadata");
        var document = readDocument(encoded);
        return writeDocument(document.root(), document.stageNodes(), document.parsedStages(), metadata);
    }

    /**
     * Merges the stage maps of equivalent corpus envelopes and records one additional stage.
     * Non-stage fields and overlapping stage entries must agree. Disjoint stage entries retain
     * their unknown fields, which makes this suitable for durable fan-in transitions.
     */
    static byte[] mergeWithStageMetadata(List<byte[]> encoded, StageMetadata metadata)
            throws CorpusFormatException {
        Objects.requireNonNull(encoded, "encoded");
        Objects.requireNonNull(metadata, "metadata");
        if (encoded.isEmpty()) {
            throw new IllegalArgumentException("at least one envelope is required");
        }

        var reference = readDocument(encoded.getFirst());
        var stages = new LinkedHashMap<>(reference.stageNodes());
        var parsed = new HashMap<>(reference.parsedStages());
        for (var index = 1; index < encoded.size(); index++) {
            var candidate = readDocument(encoded.get(index));
            requireSameDocumentFields(reference.root(), candidate.root());
            for (var entry : candidate.stageNodes().entrySet()) {
                var previous = stages.putIfAbsent(entry.getKey(), entry.getValue());
                if (previous != null && !previous.equals(entry.getValue())) {
                    throw CborReader.malformed(
                            "conflicting metadata for stage '" + entry.getKey() + "'");
                }
            }
            for (var entry : candidate.parsedStages().entrySet()) {
                var previous = parsed.putIfAbsent(entry.getKey(), entry.getValue());
                if (previous != null && !previous.equals(entry.getValue())) {
                    throw CborReader.malformed(
                            "conflicting metadata for stage '" + entry.getKey() + "'");
                }
            }
        }
        return writeDocument(reference.root(), stages, parsed, metadata);
    }

    private static Document readDocument(byte[] encoded) throws CorpusFormatException {
        var root = readTree(encoded);
        var stages = root.get(STAGES_FIELD);
        if (stages != null && !stages.isObject()) {
            throw CborReader.malformed("field '" + STAGES_FIELD + "' must be a map");
        }
        var nodes = new LinkedHashMap<String, JsonNode>();
        if (stages != null) {
            for (var field : stages.properties()) {
                nodes.put(field.getKey(), field.getValue());
            }
        }
        return new Document(root, nodes, readRecognizedStages(encoded, stages));
    }

    private static void requireSameDocumentFields(JsonNode reference, JsonNode candidate)
            throws CorpusFormatException {
        var referenceFields = new LinkedHashMap<String, JsonNode>();
        for (var field : reference.properties()) {
            if (!STAGES_FIELD.equals(field.getKey())) {
                referenceFields.put(field.getKey(), field.getValue());
            }
        }
        var candidateFields = new LinkedHashMap<String, JsonNode>();
        for (var field : candidate.properties()) {
            if (!STAGES_FIELD.equals(field.getKey())) {
                candidateFields.put(field.getKey(), field.getValue());
            }
        }
        if (!referenceFields.equals(candidateFields)) {
            throw CborReader.malformed("corpus envelopes disagree outside stage metadata");
        }
    }

    private static byte[] writeDocument(
            JsonNode root,
            Map<String, JsonNode> stageNodes,
            Map<String, StageMetadata> parsedStages,
            StageMetadata metadata)
            throws CorpusFormatException {

        var stagesMap = new CborMapWriter();
        for (var field : stageNodes.entrySet()) {
            if (metadata.stage().equals(field.getKey())) {
                continue;
            }
            var parsed = parsedStages.get(field.getKey());
            if (parsed == null) {
                // Not stage metadata this build recognizes: keep exactly what is stored.
                stagesMap.tree(field.getKey(), field.getValue());
            } else {
                stagesMap.map(field.getKey(), stageMetadata(parsed, field.getValue()));
            }
        }
        stagesMap.map(
                metadata.stage(),
                stageMetadata(metadata, stageNodes.get(metadata.stage())));

        var document = new CborMapWriter();
        for (Map.Entry<String, JsonNode> field : root.properties()) {
            if (!STAGES_FIELD.equals(field.getKey())) {
                document.tree(field.getKey(), field.getValue());
            }
        }
        document.map(STAGES_FIELD, stagesMap);

        var output = new ByteArrayOutputStream();
        try (var generator = CorpusCbor.FACTORY.createGenerator(output)) {
            generator.setCodec(MAPPER);
            document.writeTo(generator);
        } catch (IOException exception) {
            throw new CorpusFormatException(
                    "cannot encode CBOR metadata: " + Diagnostics.message(exception), exception);
        }
        return output.toByteArray();
    }

    private record Document(
            JsonNode root,
            Map<String, JsonNode> stageNodes,
            Map<String, StageMetadata> parsedStages) {}

    /**
     * Parses every stage entry that carries the fields this build requires of stage metadata, and
     * validates the rest of the document on the way. An entry without them is left to be copied
     * verbatim: this build cannot rewrite what it cannot read.
     */
    private static Map<String, StageMetadata> readRecognizedStages(byte[] encoded, JsonNode stages)
            throws CorpusFormatException {
        var recognized = new HashSet<String>();
        if (stages != null) {
            for (Map.Entry<String, JsonNode> field : stages.properties()) {
                var value = field.getValue();
                if (value.isObject()
                        && value.has(VERDICT_FIELD)
                        && value.has(START_TIME_FIELD)
                        && value.has(END_TIME_FIELD)) {
                    recognized.add(field.getKey());
                }
            }
        }

        var parsed = new ArrayList<StageMetadata>();
        CorpusInputCodec.read(
                encoded,
                (reader, field) -> readStages(reader, field, recognized::contains, parsed));
        var byStage = new HashMap<String, StageMetadata>();
        parsed.forEach(stage -> byStage.put(stage.stage(), stage));
        return byStage;
    }

    private static JsonNode readTree(byte[] encoded) throws CorpusFormatException {
        Objects.requireNonNull(encoded, "encoded");
        try {
            return MAPPER.readTree(encoded);
        } catch (IOException exception) {
            throw CborReader.invalidCbor(exception);
        }
    }

    /** Reads the wanted stages of the map that {@code stages} names, passing over the rest. */
    private static void readStages(
            CborReader reader,
            CborReader.Field stages,
            Predicate<String> wanted,
            List<StageMetadata> metadata)
            throws IOException {
        reader.requireMap(stages);
        CborReader.Field stage;
        while ((stage = reader.nextField(stages.path())) != null) {
            if (!wanted.test(stage.name())) {
                reader.skipValue();
                continue;
            }
            reader.requireMap(stage);
            metadata.add(readStageFields(reader, stage));
        }
    }

    /** Takes every stage the document records. */
    private static Predicate<String> everyStage() {
        return stage -> true;
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
                    stage.name(),
                    CorpusVerdict.fromEncodedName(requiredVerdict),
                    requiredStart,
                    requiredEnd,
                    failure);
        } catch (IllegalArgumentException exception) {
            throw CborReader.malformed(
                    "invalid metadata for stage '"
                            + stage.name()
                            + "': "
                            + Diagnostics.message(exception));
        }
    }

    /**
     * Writes one stage's metadata, keeping any field of {@code previous} that this build does not
     * model. Timestamps are written back tagged: a CBOR tag does not survive in a tree, so a stage
     * that is copied rather than written here would lose it.
     */
    private static CborMapWriter stageMetadata(StageMetadata metadata, JsonNode previous) {
        var map = new CborMapWriter().string(VERDICT_FIELD, metadata.verdict().encodedName());
        metadata.failure().ifPresent(failure -> {
            map.number(CODE_FIELD, failure.code().encodedCode());
            failure.detail().ifPresent(detail -> map.string(DETAIL_FIELD, detail));
        });
        map.epoch(START_TIME_FIELD, metadata.startTime());
        map.epoch(END_TIME_FIELD, metadata.endTime());
        if (previous != null) {
            for (Map.Entry<String, JsonNode> field : previous.properties()) {
                if (!isStageMetadataField(field.getKey())) {
                    map.tree(field.getKey(), field.getValue());
                }
            }
        }
        return map;
    }

    private static boolean isStageMetadataField(String field) {
        return VERDICT_FIELD.equals(field)
                || CODE_FIELD.equals(field)
                || DETAIL_FIELD.equals(field)
                || START_TIME_FIELD.equals(field)
                || END_TIME_FIELD.equals(field);
    }
}
