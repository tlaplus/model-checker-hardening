package io.github.tlaplus.hardening.corpus;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.fasterxml.jackson.dataformat.cbor.CBORGenerator;
import com.fasterxml.jackson.dataformat.cbor.CBORParser;
import io.github.tlaplus.hardening.checker.CheckerFailure;
import io.github.tlaplus.hardening.checker.CheckerFailureCode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
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
    void encodesAndDecodesCompactGenerationMetadata() throws Exception {
        var corpusInput = CorpusInput.expression(new byte[] {1, 2, 3});
        var generation = new GenerationMetadata(7, 18.5);

        var encoded = CorpusInputCodec.encode(corpusInput, generation);
        var envelope = CorpusInputCodec.decodeEnvelope(encoded);
        var tree = new ObjectMapper(FACTORY).readTree(encoded);

        assertEquals(corpusInput, envelope.corpusInput());
        assertEquals(generation, envelope.generation().orElseThrow());
        assertTrue(tree.has("gen"));
        assertTrue(!tree.has("generation"));
        assertEquals(7, tree.path("gen").path("cohort").intValue());
        assertEquals(18.5, tree.path("gen").path("richness").doubleValue());
    }

    @Test
    void rejectsMalformedGenerationMetadata() throws Exception {
        var missingRichness = cbor(generator -> {
            generator.writeStartObject(null, 3);
            generator.writeStringField("kind", "expr");
            generator.writeBinaryField("input", new byte[0]);
            generator.writeObjectFieldStart("gen");
            generator.writeNumberField("cohort", 1);
            generator.writeEndObject();
            generator.writeEndObject();
        });
        var negativeCohort = cbor(generator -> {
            generator.writeStartObject(null, 3);
            generator.writeStringField("kind", "expr");
            generator.writeBinaryField("input", new byte[0]);
            generator.writeObjectFieldStart("gen");
            generator.writeNumberField("cohort", -1);
            generator.writeNumberField("richness", 2.0);
            generator.writeEndObject();
            generator.writeEndObject();
        });

        assertEquals(
                "missing field: gen.richness",
                assertThrows(
                                CorpusInputFormatException.class,
                                () -> CorpusInputCodec.decode(missingRichness))
                        .getMessage());
        assertTrue(assertThrows(
                        CorpusInputFormatException.class,
                        () -> CorpusInputCodec.decode(negativeCohort))
                .getMessage()
                .contains("cohort must be nonnegative"));
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

    @Test
    void preservesGenerationMetadataWhenAddingStageMetadata() throws Exception {
        var generation = new GenerationMetadata(4, 8.0);
        var encoded = CorpusInputCodec.encode(
                CorpusInput.expression(new byte[] {4, 2}), generation);

        var updated = CorpusInputCodec.withStageMetadata(
                encoded,
                new StageMetadata(
                        "parser",
                        "pass",
                        Instant.ofEpochSecond(10),
                        Instant.ofEpochSecond(12)));

        var envelope = CorpusInputCodec.decodeEnvelope(updated);
        assertEquals(generation, envelope.generation().orElseThrow());
        assertEquals("pass", envelope.stages().getFirst().verdict());
    }

    @Test
    void encodesAndDecodesNumericCheckerFailureMetadata() throws Exception {
        var failure = new CheckerFailure(
                CheckerFailureCode.SPEC_EVAL,
                Optional.of("Attempted to apply Head to the empty sequence."));
        var encoded = CorpusInputCodec.withStageMetadata(
                CorpusInputCodec.encode(CorpusInput.expression(new byte[] {4, 2})),
                new StageMetadata(
                        "tlc",
                        "fail",
                        Instant.ofEpochSecond(10),
                        Instant.ofEpochSecond(12),
                        Optional.of(failure)));

        var envelope = CorpusInputCodec.decodeEnvelope(encoded);
        var tree = new ObjectMapper(FACTORY).readTree(encoded);

        assertEquals(Optional.of(failure), envelope.stages().getFirst().failure());
        assertEquals(75, tree.path("stages").path("tlc").path("code").intValue());
        assertEquals(
                failure.detail().orElseThrow(),
                tree.path("stages").path("tlc").path("detail").textValue());
    }

    @Test
    void rejectsInvalidCheckerFailureMetadata() throws Exception {
        var missingCode = checkerEnvelope("fail", null, null);
        var unknownCode = checkerEnvelope("fail", 76, null);
        var detailWithoutCode = checkerEnvelope("fail", null, "undefined expression");
        var codeOnPass = checkerEnvelope("pass", 75, null);
        var codeOnParser = stageEnvelope("parser", "fail", 150, null);
        var excessiveDetail = checkerEnvelope("fail", 75, "x".repeat(81));
        var multilineDetail = checkerEnvelope("fail", 75, "first\nsecond");
        var textCode = cbor(generator -> {
            generator.writeStartObject(null, 3);
            generator.writeStringField("kind", "expr");
            generator.writeBinaryField("input", new byte[0]);
            generator.writeObjectFieldStart("stages");
            generator.writeObjectFieldStart("tlc");
            generator.writeStringField("verdict", "fail");
            generator.writeStringField("code", "75");
            writeTaggedEpoch(generator, "startTime", Instant.ofEpochSecond(10));
            writeTaggedEpoch(generator, "endTime", Instant.ofEpochSecond(12));
            generator.writeEndObject();
            generator.writeEndObject();
            generator.writeEndObject();
        });

        assertInvalidEnvelope(missingCode, "requires a failure code");
        assertInvalidEnvelope(unknownCode, "unsupported checker failure code: 76");
        assertInvalidEnvelope(detailWithoutCode, "requires field 'code'");
        assertInvalidEnvelope(codeOnPass, "requires the fail verdict");
        assertInvalidEnvelope(codeOnParser, "parser metadata must not contain");
        assertInvalidEnvelope(excessiveDetail, "must not exceed 80 characters");
        assertInvalidEnvelope(multilineDetail, "must be a single line");
        assertInvalidEnvelope(textCode, "must be an integer");
    }

    @Test
    void decodesSupportedEnvelopeFieldsInStageOrderAndIgnoresExtensions() throws Exception {
        var encoded = cbor(generator -> {
            generator.writeStartObject(null, 4);
            generator.writeStringField("kind", "expr");
            generator.writeBinaryField("input", new byte[] {4, 2});
            generator.writeStringField("future", "ignored");
            generator.writeObjectFieldStart("stages");
            writeStage(
                    generator,
                    "generator",
                    "pass",
                    Instant.ofEpochSecond(10),
                    Instant.ofEpochSecond(10));
            generator.writeObjectFieldStart("parser");
            generator.writeStringField("verdict", "fail");
            generator.writeStringField("future", "ignored");
            writeTaggedEpoch(generator, "startTime", Instant.ofEpochSecond(20));
            writeTaggedEpoch(generator, "endTime", Instant.ofEpochSecond(23));
            generator.writeEndObject();
            generator.writeEndObject();
            generator.writeEndObject();
        });

        var envelope = CorpusInputCodec.decodeEnvelope(encoded);

        assertEquals(CorpusInput.expression(new byte[] {4, 2}), envelope.corpusInput());
        assertEquals(
                List.of(
                        new StageMetadata(
                                "generator",
                                "pass",
                                Instant.ofEpochSecond(10),
                                Instant.ofEpochSecond(10)),
                        new StageMetadata(
                                "parser",
                                "fail",
                                Instant.ofEpochSecond(20),
                                Instant.ofEpochSecond(23))),
                envelope.stages());
    }

    @Test
    void rejectsInvalidSupportedMetadataWhenDecodingEnvelope() throws Exception {
        var untaggedTime = cbor(generator -> {
            generator.writeStartObject(null, 3);
            generator.writeStringField("kind", "expr");
            generator.writeBinaryField("input", new byte[0]);
            generator.writeObjectFieldStart("stages");
            generator.writeObjectFieldStart("parser");
            generator.writeStringField("verdict", "pass");
            generator.writeNumberField("startTime", 10);
            writeTaggedEpoch(generator, "endTime", Instant.ofEpochSecond(12));
            generator.writeEndObject();
            generator.writeEndObject();
            generator.writeEndObject();
        });
        var reversedTimes = cbor(generator -> {
            generator.writeStartObject(null, 3);
            generator.writeStringField("kind", "expr");
            generator.writeBinaryField("input", new byte[0]);
            generator.writeObjectFieldStart("stages");
            writeStage(
                    generator,
                    "parser",
                    "pass",
                    Instant.ofEpochSecond(12),
                    Instant.ofEpochSecond(10));
            generator.writeEndObject();
            generator.writeEndObject();
        });

        assertTrue(assertThrows(
                        CorpusInputFormatException.class,
                        () -> CorpusInputCodec.decodeEnvelope(untaggedTime))
                .getMessage()
                .contains("must be a tag-1 epoch number"));
        assertTrue(assertThrows(
                        CorpusInputFormatException.class,
                        () -> CorpusInputCodec.decodeEnvelope(reversedTimes))
                .getMessage()
                .contains("endTime must not precede startTime"));
    }

    private byte[] cbor(CborWriter writer) throws Exception {
        var output = new ByteArrayOutputStream();
        try (var generator = FACTORY.createGenerator(output)) {
            writer.write(generator);
        }
        return output.toByteArray();
    }

    private byte[] checkerEnvelope(String verdict, Integer code, String detail)
            throws Exception {
        return stageEnvelope("tlc", verdict, code, detail);
    }

    private byte[] stageEnvelope(
            String stage, String verdict, Integer code, String detail) throws Exception {
        return cbor(generator -> {
            generator.writeStartObject(null, 3);
            generator.writeStringField("kind", "expr");
            generator.writeBinaryField("input", new byte[0]);
            generator.writeObjectFieldStart("stages");
            generator.writeObjectFieldStart(stage);
            generator.writeStringField("verdict", verdict);
            if (code != null) {
                generator.writeNumberField("code", code);
            }
            if (detail != null) {
                generator.writeStringField("detail", detail);
            }
            writeTaggedEpoch(generator, "startTime", Instant.ofEpochSecond(10));
            writeTaggedEpoch(generator, "endTime", Instant.ofEpochSecond(12));
            generator.writeEndObject();
            generator.writeEndObject();
            generator.writeEndObject();
        });
    }

    private void assertInvalidEnvelope(byte[] encoded, String message) {
        assertTrue(assertThrows(
                        CorpusInputFormatException.class,
                        () -> CorpusInputCodec.decodeEnvelope(encoded))
                .getMessage()
                .contains(message));
    }

    private void writeStage(
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

    private void writeTaggedEpoch(CBORGenerator generator, String field, Instant instant)
            throws IOException {
        generator.writeFieldName(field);
        generator.writeTag(1);
        generator.writeNumber(instant.getEpochSecond());
    }

    @FunctionalInterface
    private interface CborWriter {
        void write(CBORGenerator generator) throws IOException;
    }
}
