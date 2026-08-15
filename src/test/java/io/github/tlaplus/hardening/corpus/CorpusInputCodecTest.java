package io.github.tlaplus.hardening.corpus;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.fasterxml.jackson.dataformat.cbor.CBORParser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class CorpusInputCodecTest {
    private static final CBORFactory FACTORY = new CBORFactory();

    @Test
    void encodesTheDocumentedMinimalExpressionInput() throws Exception {
        var corpusInput = CorpusInput.expression(new byte[] {0x01, 0x23, (byte) 0xaf});

        var encoded = CorpusInputCodec.encode(corpusInput);

        assertArrayEquals(
                HexFormat.of().parseHex("a2646b696e64646578707265696e707574430123af"),
                encoded);
        assertEquals(corpusInput, CorpusInputCodec.decode(encoded));
    }

    @Test
    void decodesRequiredFieldsInAnyOrderAndIgnoresStageMetadata() throws Exception {
        var encoded = cbor(generator -> {
            generator.writeStartObject(null, 3);
            generator.writeFieldName("input");
            generator.writeBinary(new byte[] {4, 5, 6});
            generator.writeObjectFieldStart("stages");
            generator.writeObjectFieldStart("parser");
            generator.writeStringField("verdict", "pass");
            generator.writeEndObject();
            generator.writeEndObject();
            generator.writeStringField("kind", "expr");
            generator.writeEndObject();
        });

        assertEquals(
                CorpusInput.expression(new byte[] {4, 5, 6}),
                CorpusInputCodec.decode(encoded));
    }

    @Test
    void recognizesModuleInputs() throws Exception {
        var corpusInput = new CorpusInput(CorpusInput.Kind.MODULE, new byte[] {9});

        assertEquals(corpusInput, CorpusInputCodec.decode(CorpusInputCodec.encode(corpusInput)));
    }

    @Test
    void corpusInputOwnsItsByteArray() {
        var source = new byte[] {1, 2};
        var corpusInput = CorpusInput.expression(source);
        source[0] = 9;
        var returned = corpusInput.input();
        returned[1] = 9;

        assertArrayEquals(new byte[] {1, 2}, corpusInput.input());
    }

    @Test
    void rejectsMalformedOrNonMapCbor() {
        var truncated = assertThrows(
                CorpusInputFormatException.class,
                () -> CorpusInputCodec.decode(new byte[] {(byte) 0xa1}));
        var array = assertThrows(
                CorpusInputFormatException.class,
                () -> CorpusInputCodec.decode(new byte[] {(byte) 0x80}));

        assertTrue(truncated.getMessage().contains("invalid CBOR"));
        assertTrue(array.getMessage().contains("top-level value must be a map"));
    }

    @Test
    void rejectsMissingAndIncorrectlyTypedRequiredFields() throws Exception {
        var missingInput = cbor(generator -> {
            generator.writeStartObject(null, 1);
            generator.writeStringField("kind", "expr");
            generator.writeEndObject();
        });
        var textInput = cbor(generator -> {
            generator.writeStartObject(null, 2);
            generator.writeStringField("kind", "expr");
            generator.writeStringField("input", "not bytes");
            generator.writeEndObject();
        });

        var missing = assertThrows(
                CorpusInputFormatException.class,
                () -> CorpusInputCodec.decode(missingInput));
        var wrongType = assertThrows(
                CorpusInputFormatException.class,
                () -> CorpusInputCodec.decode(textInput));

        assertEquals("missing field: input", missing.getMessage());
        assertEquals("field 'input' must be a byte string", wrongType.getMessage());
    }

    @Test
    void rejectsDuplicateFieldsUnknownKindsAndTrailingValues() throws Exception {
        var duplicate = cbor(generator -> {
            generator.writeStartObject(null, 3);
            generator.writeStringField("kind", "expr");
            generator.writeStringField("kind", "expr");
            generator.writeBinaryField("input", new byte[0]);
            generator.writeEndObject();
        });
        var unknownKind = cbor(generator -> {
            generator.writeStartObject(null, 2);
            generator.writeStringField("kind", "declaration");
            generator.writeBinaryField("input", new byte[0]);
            generator.writeEndObject();
        });
        var minimal = CorpusInputCodec.encode(CorpusInput.expression(new byte[0]));
        var trailing = Arrays.copyOf(minimal, minimal.length + 1);
        trailing[trailing.length - 1] = (byte) 0xf6;

        assertEquals(
                "duplicate field: kind",
                assertThrows(
                                CorpusInputFormatException.class,
                                () -> CorpusInputCodec.decode(duplicate))
                        .getMessage());
        assertEquals(
                "unknown input kind: declaration",
                assertThrows(
                                CorpusInputFormatException.class,
                                () -> CorpusInputCodec.decode(unknownKind))
                        .getMessage());
        assertEquals(
                "trailing data after top-level map",
                assertThrows(
                                CorpusInputFormatException.class,
                                () -> CorpusInputCodec.decode(trailing))
                        .getMessage());
    }

    @Test
    void addsTaggedStageTimesWithoutDroppingUnknownMetadata() throws Exception {
        var encoded = cbor(generator -> {
            generator.writeStartObject(null, 4);
            generator.writeStringField("kind", "expr");
            generator.writeBinaryField("input", new byte[] {4, 2});
            generator.writeObjectFieldStart("future");
            generator.writeNumberField("answer", 42);
            generator.writeEndObject();
            generator.writeObjectFieldStart("stages");
            generator.writeObjectFieldStart("other");
            generator.writeStringField("verdict", "kept");
            generator.writeEndObject();
            generator.writeEndObject();
            generator.writeEndObject();
        });

        var updated = CorpusInputCodec.withStageMetadata(
                encoded,
                new StageMetadata(
                        "parser",
                        "pass",
                        Instant.ofEpochSecond(10),
                        Instant.ofEpochSecond(12)));

        var tree = new ObjectMapper(FACTORY).readTree(updated);
        assertEquals(42, tree.path("future").path("answer").intValue());
        assertEquals("kept", tree.path("stages").path("other").path("verdict").textValue());
        assertEquals(
                "pass", CorpusInputCodec.stageVerdict(updated, "parser").orElseThrow());

        var taggedTimes = 0;
        try (var parser = FACTORY.createParser(updated)) {
            while (parser.nextToken() != null) {
                if (("startTime".equals(parser.currentName())
                                || "endTime".equals(parser.currentName()))
                        && parser.nextToken() != null) {
                    assertEquals(1, parser.getCurrentTag());
                    taggedTimes++;
                }
            }
        }
        assertEquals(2, taggedTimes);
    }

    private byte[] cbor(CborWriter writer) throws Exception {
        var output = new ByteArrayOutputStream();
        try (var generator = FACTORY.createGenerator(output)) {
            writer.write(generator);
        }
        return output.toByteArray();
    }

    @FunctionalInterface
    private interface CborWriter {
        void write(JsonGenerator generator) throws IOException;
    }
}
