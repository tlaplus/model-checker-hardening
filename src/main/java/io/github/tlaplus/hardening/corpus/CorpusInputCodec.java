package io.github.tlaplus.hardening.corpus;

import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Objects;

/** Encodes and decodes the extensible CBOR envelope used for corpus inputs. */
public final class CorpusInputCodec {
    private static final String KIND_FIELD = "kind";
    private static final String INPUT_FIELD = "input";
    private static final CBORFactory FACTORY = new CBORFactory();

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
