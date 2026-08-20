package io.github.tlaplus.hardening.corpus;

import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.fasterxml.jackson.dataformat.cbor.CBORGenerator;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;

/**
 * Builds stored CBOR documents for the codec tests, so a test states what a corpus file contains
 * and nothing else.
 */
final class CborDocuments {
    static final CBORFactory FACTORY = new CBORFactory();

    private CborDocuments() {}

    /** Returns the bytes {@code writer} produces. */
    static byte[] cbor(CborWriter writer) throws IOException {
        var output = new ByteArrayOutputStream();
        try (var generator = FACTORY.createGenerator(output)) {
            writer.write(generator);
        }
        return output.toByteArray();
    }

    /** Writes one stage entry with the fields every stage records. */
    static void writeStage(
            CBORGenerator generator,
            String stage,
            String verdict,
            Instant startTime,
            Instant endTime)
            throws IOException {
        generator.writeObjectFieldStart(stage);
        generator.writeStringField("verdict", verdict);
        writeTaggedEpoch(generator, "startTime", startTime);
        writeTaggedEpoch(generator, "endTime", endTime);
        generator.writeEndObject();
    }

    /** Writes a timestamp the way the format requires it: a number carrying CBOR tag 1. */
    static void writeTaggedEpoch(CBORGenerator generator, String field, Instant instant)
            throws IOException {
        generator.writeFieldName(field);
        generator.writeTag(1);
        generator.writeNumber(instant.getEpochSecond());
    }

    @FunctionalInterface
    interface CborWriter {
        void write(CBORGenerator generator) throws IOException;
    }
}
