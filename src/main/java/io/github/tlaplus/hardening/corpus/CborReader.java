package io.github.tlaplus.hardening.corpus;

import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.fasterxml.jackson.dataformat.cbor.CBORParser;
import io.github.tlaplus.hardening.common.Diagnostics;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Reads one CBOR document of a corpus, checking its shape as it goes.
 *
 * <p>Both stored documents -- an input envelope and the workflow-statistics aggregate -- are maps
 * of named fields that must hold a value of an expected type, that ignore fields this build does
 * not know, and that reject a field given twice. This reader owns those rules, so a codec only
 * states which fields it knows and what it does with them.
 *
 * <p>Every diagnostic names the offending value by its dotted path in the document, for example
 * {@code stages.tlc.verdict}. Paths are tracked here rather than concatenated at each field.
 */
final class CborReader implements AutoCloseable {
    /** The path of the document's root map: a field of the root is named by itself alone. */
    static final String ROOT = "";

    private static final CBORFactory FACTORY = new CBORFactory();

    /** The CBOR tag of an epoch-based date/time, RFC 8949 section 3.4.2. */
    private static final int EPOCH_TAG = 1;

    /** One field of the map currently being read. */
    record Field(String name, String path, JsonToken value) {}

    private final CBORParser parser;

    /** The field names already seen in each open map, so a repeated field is rejected. */
    private final Map<String, Set<String>> seenFields = new HashMap<>();

    private CborReader(CBORParser parser) {
        this.parser = parser;
    }

    /** Opens a reader over a complete document. */
    static CborReader of(byte[] encoded) throws CorpusFormatException {
        Objects.requireNonNull(encoded, "encoded");
        try {
            return new CborReader(FACTORY.createParser(encoded));
        } catch (IOException exception) {
            throw invalidCbor(exception);
        }
    }

    /** Requires the document to begin with a map and positions the reader inside it. */
    void startDocument() throws IOException {
        if (parser.nextToken() != JsonToken.START_OBJECT) {
            throw malformed("top-level value must be a map");
        }
    }

    /** Requires the document to end with the top-level map. */
    void endDocument() throws IOException {
        if (parser.nextToken() != null) {
            throw malformed("trailing data after top-level map");
        }
    }

    /**
     * Returns the next field of the open map at {@code mapPath}, or {@code null} at its end.
     *
     * <p>The reader is left positioned on the field's value, which the caller either consumes with
     * one of the value methods or discards with {@link #skipValue()}.
     */
    Field nextField(String mapPath) throws IOException {
        Objects.requireNonNull(mapPath, "mapPath");
        if (parser.nextToken() == JsonToken.END_OBJECT) {
            seenFields.remove(mapPath);
            return null;
        }
        if (!parser.hasToken(JsonToken.FIELD_NAME)) {
            throw malformed(
                    mapPath.isEmpty()
                            ? "map keys must be text strings"
                            : "field '" + mapPath + "' keys must be text strings");
        }
        var name = parser.currentName();
        var path = mapPath.isEmpty() ? name : mapPath + "." + name;
        if (!seenFields.computeIfAbsent(mapPath, map -> new HashSet<>()).add(name)) {
            throw malformed("duplicate field: " + path);
        }
        var value = parser.nextToken();
        if (value == null) {
            throw malformed("field has no value: " + path);
        }
        return new Field(name, path, value);
    }

    /** Discards the current field's value, including a whole map or array. */
    void skipValue() throws IOException {
        parser.skipChildren();
    }

    /** Requires a field to hold a map and positions the reader inside it. */
    void requireMap(Field field) throws IOException {
        if (field.value() != JsonToken.START_OBJECT) {
            throw malformed("field '" + field.path() + "' must be a map");
        }
    }

    String text(Field field) throws IOException {
        if (field.value() != JsonToken.VALUE_STRING) {
            throw malformed("field '" + field.path() + "' must be a text string");
        }
        return parser.getText();
    }

    byte[] binary(Field field) throws IOException {
        if (field.value() != JsonToken.VALUE_EMBEDDED_OBJECT) {
            throw malformed("field '" + field.path() + "' must be a byte string");
        }
        return parser.getBinaryValue();
    }

    long longValue(Field field) throws IOException {
        if (field.value() != JsonToken.VALUE_NUMBER_INT) {
            throw malformed("field '" + field.path() + "' must be an integer");
        }
        return parser.getLongValue();
    }

    /** Reads an integer and narrows it only when it fits in a Java {@code int}. */
    int intValue(Field field) throws IOException {
        try {
            return Math.toIntExact(longValue(field));
        } catch (ArithmeticException exception) {
            throw malformed(
                    "field '" + field.path() + "' is outside the supported integer range");
        }
    }

    double doubleValue(Field field) throws IOException {
        if (field.value() != JsonToken.VALUE_NUMBER_INT
                && field.value() != JsonToken.VALUE_NUMBER_FLOAT) {
            throw malformed("field '" + field.path() + "' must be a number");
        }
        return parser.getDoubleValue();
    }

    /** Reads an epoch-based date/time, which the format requires to carry CBOR tag 1. */
    Instant epoch(Field field) throws IOException {
        if ((field.value() != JsonToken.VALUE_NUMBER_INT
                        && field.value() != JsonToken.VALUE_NUMBER_FLOAT)
                || !parser.getCurrentTags().contains(EPOCH_TAG)) {
            throw malformed("field '" + field.path() + "' must be a tag-1 epoch number");
        }
        try {
            var epoch = parser.getDecimalValue();
            var seconds = epoch.setScale(0, RoundingMode.FLOOR).longValueExact();
            var nanos = epoch.subtract(BigDecimal.valueOf(seconds))
                    .movePointRight(9)
                    .intValueExact();
            return Instant.ofEpochSecond(seconds, nanos);
        } catch (ArithmeticException | DateTimeException | NumberFormatException exception) {
            throw malformed(
                    "field '" + field.path() + "' is outside the supported timestamp range");
        }
    }

    /** Returns a value that the format requires, or reports it as missing. */
    static <T> T required(T value, String path) throws CorpusFormatException {
        if (value == null) {
            throw malformed("missing field: " + path);
        }
        return value;
    }

    /** Reports data that parses as CBOR but does not follow the documented format. */
    static CorpusFormatException malformed(String message) {
        return new CorpusFormatException(message);
    }

    /** Reports data that does not parse as CBOR at all. */
    static CorpusFormatException invalidCbor(Throwable cause) {
        return new CorpusFormatException("invalid CBOR: " + Diagnostics.message(cause), cause);
    }

    @Override
    public void close() throws IOException {
        parser.close();
    }
}
