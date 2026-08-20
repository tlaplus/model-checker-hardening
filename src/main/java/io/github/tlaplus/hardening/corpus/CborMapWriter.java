package io.github.tlaplus.hardening.corpus;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.cbor.CBORGenerator;
import io.github.tlaplus.hardening.common.ThrowingConsumer;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * Collects the fields of one CBOR map so that its definite length is derived from what is written
 * rather than maintained beside it.
 *
 * <p>A definite-length CBOR map declares its size before its entries. Counting those entries by
 * hand produces a corrupt document rather than an exception when the count and the writes drift
 * apart, so no codec of the corpus formats states a size directly: a field is appended here and
 * {@link #writeTo} emits the count it actually holds.
 *
 * <p>Field order is insertion order, which the corpus formats preserve because a rewrite copies
 * fields it does not model. Duplicate field names are rejected: they would make the decoded
 * document ambiguous, and the readers of these formats refuse them.
 */
final class CborMapWriter {
    private final List<Field> fields = new ArrayList<>();
    private final LinkedHashSet<String> names = new LinkedHashSet<>();

    private record Field(String name, ThrowingConsumer<CBORGenerator, IOException> write) {}

    /** Appends a field whose value is written by {@code write} when this map is emitted. */
    CborMapWriter field(String name, ThrowingConsumer<CBORGenerator, IOException> write) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(write, "write");
        if (!names.add(name)) {
            throw new IllegalStateException("duplicate CBOR field: " + name);
        }
        fields.add(new Field(name, write));
        return this;
    }

    CborMapWriter string(String name, String value) {
        Objects.requireNonNull(value, "value");
        return field(name, generator -> generator.writeString(value));
    }

    CborMapWriter number(String name, long value) {
        return field(name, generator -> generator.writeNumber(value));
    }

    CborMapWriter number(String name, double value) {
        return field(name, generator -> generator.writeNumber(value));
    }

    /** Appends a field holding a nested map. */
    CborMapWriter map(String name, CborMapWriter nested) {
        Objects.requireNonNull(nested, "nested");
        return field(name, nested::writeTo);
    }

    /** Appends a field holding a binary payload. */
    CborMapWriter binary(String name, byte[] value) {
        Objects.requireNonNull(value, "value");
        return field(name, generator -> generator.writeBinary(value));
    }

    /**
     * Appends a field holding an epoch-based date/time, tagged per RFC 8949 section 3.4.2. A CBOR
     * tag does not survive in a parsed tree, so a timestamp written here keeps its tag while one
     * copied as a tree would lose it.
     */
    CborMapWriter epoch(String name, Instant value) {
        Objects.requireNonNull(value, "value");
        return field(name, generator -> {
            generator.writeTag(CborReader.EPOCH_TAG);
            generator.writeNumber(value.getEpochSecond());
        });
    }

    /** Appends a field holding a value this build does not model, copied verbatim. */
    CborMapWriter tree(String name, JsonNode value) {
        Objects.requireNonNull(value, "value");
        return field(name, generator -> generator.writeTree(value));
    }

    /** Reports whether a field of this name has already been appended. */
    boolean has(String name) {
        return names.contains(Objects.requireNonNull(name, "name"));
    }

    /** Writes this map as a definite-length CBOR map of exactly the fields it holds. */
    void writeTo(CBORGenerator generator) throws IOException {
        generator.writeStartObject(null, fields.size());
        for (var field : fields) {
            generator.writeFieldName(field.name());
            field.write().accept(generator);
        }
        generator.writeEndObject();
    }
}
