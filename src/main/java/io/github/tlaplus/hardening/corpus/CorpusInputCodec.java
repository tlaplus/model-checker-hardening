package io.github.tlaplus.hardening.corpus;

import io.github.tlaplus.hardening.common.Diagnostics;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

/**
 * Encodes and decodes the fields that decide a corpus input's identity: its kind, its generator
 * payload, and the admission metadata recorded when it was generated.
 *
 * <p>Stage metadata is deliberately not read here. A stage is free to add its own fields, and this
 * level must keep working when it does, so {@link #read} hands the {@code stages} map to a caller
 * that knows what to do with it. {@link CorpusEnvelopeCodec} is that caller.
 */
public final class CorpusInputCodec {
    static final String KIND_FIELD = "kind";
    static final String INPUT_FIELD = "input";
    static final String GENERATION_FIELD = "gen";
    static final String STAGES_FIELD = "stages";
    static final String COHORT_FIELD = "cohort";
    static final String RICHNESS_FIELD = "richness";

    private CorpusInputCodec() {}

    /** The identity level of one document: its required fields and its admission metadata. */
    record Document(CorpusInput corpusInput, Optional<GenerationMetadata> generation) {}

    /** Reads the {@code stages} map of a document, or passes over it. */
    @FunctionalInterface
    interface StageReader {
        /**
         * Reads the stages map that {@code stages} names. The reader is positioned on its value,
         * which an implementation either consumes completely or skips.
         */
        void read(CborReader reader, CborReader.Field stages) throws IOException;

        /** Returns a reader that ignores stage metadata, whatever shape it has. */
        static StageReader ignoring() {
            return (reader, stages) -> reader.skipValue();
        }
    }

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
        var document = new CborMapWriter()
                .string(KIND_FIELD, corpusInput.kind().encodedName())
                .binary(INPUT_FIELD, corpusInput.input());
        generationMetadata.ifPresent(
                metadata -> document.map(GENERATION_FIELD, generationMetadata(metadata)));
        var output = new ByteArrayOutputStream();
        try (var generator = CorpusCbor.FACTORY.createGenerator(output)) {
            document.writeTo(generator);
        }
        return output.toByteArray();
    }

    /**
     * Decodes the required fields and ignores stage and unknown metadata fields.
     *
     * <p>The complete byte array must contain exactly one CBOR map. Field order is insignificant,
     * but duplicate fields are rejected to avoid ambiguous input identities.
     */
    public static CorpusInput decode(byte[] encoded) throws CorpusFormatException {
        return read(encoded, StageReader.ignoring()).corpusInput();
    }

    /**
     * Reads one document in a single pass, checking everything that decides its identity and
     * letting {@code stages} take what it needs from the stages map.
     */
    static Document read(byte[] encoded, StageReader stages) throws CorpusFormatException {
        Objects.requireNonNull(encoded, "encoded");
        Objects.requireNonNull(stages, "stages");
        try (var reader = CborReader.of(encoded)) {
            reader.startDocument();

            CorpusInput.Kind kind = null;
            byte[] input = null;
            GenerationMetadata generation = null;
            CborReader.Field field;
            while ((field = reader.nextField(CborReader.ROOT)) != null) {
                switch (field.name()) {
                    case KIND_FIELD ->
                        kind = CorpusInput.Kind.fromEncodedName(reader.text(field));
                    case INPUT_FIELD -> input = reader.binary(field);
                    case GENERATION_FIELD -> {
                        reader.requireMap(field);
                        generation = readGenerationMetadata(reader, field.path());
                    }
                    case STAGES_FIELD -> stages.read(reader, field);
                    default -> reader.skipValue();
                }
            }

            var requiredKind = CborReader.required(kind, KIND_FIELD);
            var requiredInput = CborReader.required(input, INPUT_FIELD);
            reader.endDocument();
            return new Document(
                    new CorpusInput(requiredKind, requiredInput),
                    Optional.ofNullable(generation));
        } catch (CorpusFormatException exception) {
            throw exception;
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

    /** Returns the admission-time PBT metadata as the {@code gen} submap. */
    private static CborMapWriter generationMetadata(GenerationMetadata metadata) {
        return new CborMapWriter()
                .number(COHORT_FIELD, metadata.cohort())
                .number(RICHNESS_FIELD, metadata.richness());
    }
}
