package io.github.tlaplus.hardening.corpus;

import io.github.tlaplus.hardening.common.Diagnostics;
import io.github.tlaplus.hardening.gen.InputKind;
import java.io.IOException;
import io.github.tlaplus.hardening.mutation.MutationOperator;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Encodes and decodes an input's kind, generator payload, and admission metadata.
 *
 * <p>The raw payload alone determines the corpus filename and storage identity. The kind says how
 * to decode that payload, while admission metadata describes why it entered the corpus.
 *
 * <p>Stage metadata is deliberately not read here. A stage is free to add its own fields, and this
 * level must keep working when it does, so {@link #read} hands the {@code stages} map to a caller
 * that knows what to do with it. {@link CorpusEnvelopeCodec} is that caller.
 */
public final class CorpusInputCodec {
    static final String KIND_FIELD = "kind";
    static final String INPUT_FIELD = "input";
    static final String GEN_FIELD = "gen";
    static final String STAGES_FIELD = "stages";
    static final String COHORT_FIELD = "cohort";
    static final String RICHNESS_FIELD = "richness";
    static final String KNOWN_DEFECTS_FIELD = "knownDefects";
    static final String GENERATION_FIELD = "generation";
    static final String PARENT_FIELD = "parent";
    static final String OPERATORS_FIELD = "operators";

    private CorpusInputCodec() {}

    /** The input-level fields of one document, separate from stage metadata. */
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
                metadata -> document.map(GEN_FIELD, generationMetadata(metadata)));
        return document.encode();
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
     * Reads one document in a single pass, checking its input fields and letting {@code stages}
     * take what it needs from the stages map.
     */
    static Document read(byte[] encoded, StageReader stages) throws CorpusFormatException {
        Objects.requireNonNull(encoded, "encoded");
        Objects.requireNonNull(stages, "stages");
        try (var reader = CborReader.of(encoded)) {
            reader.startDocument();

            InputKind kind = null;
            byte[] input = null;
            GenerationMetadata generation = null;
            CborReader.Field field;
            while ((field = reader.nextField(CborReader.ROOT)) != null) {
                switch (field.name()) {
                    case KIND_FIELD ->
                        kind = CorpusInput.kindFromEncodedName(reader.text(field));
                    case INPUT_FIELD -> input = reader.binary(field);
                    case GEN_FIELD -> {
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
        Integer generation = null;
        Integer cohort = null;
        Double richness = null;
        List<String> knownDefects = List.of();
        String parent = null;
        List<String> operators = null;
        CborReader.Field field;
        while ((field = reader.nextField(path)) != null) {
            switch (field.name()) {
                case GENERATION_FIELD -> generation = reader.intValue(field);
                case COHORT_FIELD -> cohort = reader.intValue(field);
                case RICHNESS_FIELD -> richness = reader.doubleValue(field);
                case KNOWN_DEFECTS_FIELD -> knownDefects = reader.texts(field);
                case PARENT_FIELD -> parent = reader.text(field);
                case OPERATORS_FIELD -> operators = reader.texts(field);
                default -> reader.skipValue();
            }
        }
        if ((parent == null) != (operators == null)) {
            throw CborReader.malformed("fields '" + path + "." + PARENT_FIELD + "' and '" + path
                    + "." + OPERATORS_FIELD + "' must appear together");
        }
        try {
            return new GenerationMetadata(
                    generation == null ? OptionalInt.empty() : OptionalInt.of(generation),
                    CborReader.required(cohort, path + "." + COHORT_FIELD),
                    CborReader.required(richness, path + "." + RICHNESS_FIELD),
                    knownDefects,
                    parent == null
                            ? Optional.empty()
                            : Optional.of(new Mutation(parent, mutationOperators(operators, path))));
        } catch (IllegalArgumentException exception) {
            throw CborReader.malformed(
                    "invalid gen metadata: " + Diagnostics.message(exception));
        }
    }

    private static List<MutationOperator> mutationOperators(List<String> names, String path)
            throws CorpusFormatException {
        var operators = new ArrayList<MutationOperator>(names.size());
        for (var name : names) {
            operators.add(MutationOperator.fromEncodedName(name).orElseThrow(() -> CborReader.malformed(
                    "unknown mutation operator '" + name + "' in '" + path + "." + OPERATORS_FIELD + "'")));
        }
        return operators;
    }

    /**
     * Returns the admission metadata as the {@code gen} submap. Only a quarantined entry has
     * known-defect signatures, and only a mutant has a parent, so other entries omit those fields.
     */
    private static CborMapWriter generationMetadata(GenerationMetadata metadata) {
        var result = new CborMapWriter();
        metadata.generation().ifPresent(generation -> result.number(GENERATION_FIELD, generation));
        result.number(COHORT_FIELD, metadata.cohort()).number(RICHNESS_FIELD, metadata.richness());
        if (!metadata.knownDefects().isEmpty()) {
            result.texts(KNOWN_DEFECTS_FIELD, metadata.knownDefects());
        }
        metadata.mutation().ifPresent(mutation -> result
                .string(PARENT_FIELD, mutation.parent())
                .texts(OPERATORS_FIELD, mutation.operators().stream()
                        .map(MutationOperator::encodedName)
                        .toList()));
        return result;
    }
}
