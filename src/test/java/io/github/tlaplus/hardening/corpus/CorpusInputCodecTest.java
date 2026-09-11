package io.github.tlaplus.hardening.corpus;

import static io.github.tlaplus.hardening.corpus.CborDocuments.FACTORY;
import static io.github.tlaplus.hardening.corpus.CborDocuments.cbor;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.tlaplus.hardening.gen.InputKind;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;

class CorpusInputCodecTest {

    @Test
    void encodesTheDocumentedMinimalExpressionInput() throws Exception {
        var corpusInput = new CorpusInput(InputKind.EXPRESSION, new byte[] {0x01, 0x23, (byte) 0xaf});

        var encoded = CorpusInputCodec.encode(corpusInput);

        assertArrayEquals(
                HexFormat.of().parseHex("a2646b696e64646578707265696e707574430123af"),
                encoded);
        assertEquals(corpusInput, CorpusInputCodec.decode(encoded));
    }

    @Test
    void encodesAndDecodesCompactGenerationMetadata() throws Exception {
        var corpusInput = new CorpusInput(InputKind.EXPRESSION, new byte[] {1, 2, 3});
        var generation = new GenerationMetadata(7, 18.5);

        var encoded = CorpusInputCodec.encode(corpusInput, generation);
        var envelope = CorpusEnvelopeCodec.decodeEnvelope(encoded);
        var tree = new ObjectMapper(FACTORY).readTree(encoded);

        assertEquals(corpusInput, envelope.corpusInput());
        assertEquals(generation, envelope.generation().orElseThrow());
        assertTrue(tree.has("gen"));
        assertTrue(!tree.has("generation"));
        assertEquals(7, tree.path("gen").path("cohort").intValue());
        assertEquals(18.5, tree.path("gen").path("richness").doubleValue());
    }

    @Test
    void recordsKnownDefectsOnlyForAQuarantinedInput() throws Exception {
        var corpusInput = new CorpusInput(InputKind.MODULE, new byte[] {1});
        var quarantined = new GenerationMetadata(1, 2.0, List.of("string-set", "sequence-set"));

        var admitted = CorpusInputCodec.encode(corpusInput, new GenerationMetadata(1, 2.0));
        var encoded = CorpusInputCodec.encode(corpusInput, quarantined);

        assertTrue(!new ObjectMapper(FACTORY).readTree(admitted).path("gen").has("knownDefects"));
        assertEquals(quarantined, CorpusEnvelopeCodec.decodeEnvelope(encoded).generation().orElseThrow());
        assertEquals(
                "string-set",
                new ObjectMapper(FACTORY).readTree(encoded).path("gen").path("knownDefects").get(0).textValue());
        assertEquals("string-set", quarantined.primaryKnownDefect().orElseThrow());
    }

    @Test
    void rejectsKnownDefectsThatAreNotTextStrings() throws Exception {
        var numeric = cbor(generator -> {
            generator.writeStartObject(null, 3);
            generator.writeStringField("kind", "expr");
            generator.writeBinaryField("input", new byte[0]);
            generator.writeObjectFieldStart("gen");
            generator.writeNumberField("cohort", 1);
            generator.writeNumberField("richness", 2.0);
            generator.writeArrayFieldStart("knownDefects");
            generator.writeNumber(1);
            generator.writeEndArray();
            generator.writeEndObject();
            generator.writeEndObject();
        });

        assertEquals(
                "field 'gen.knownDefects' must be an array of text strings",
                assertThrows(CorpusFormatException.class, () -> CorpusInputCodec.decode(numeric))
                        .getMessage());
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
                                CorpusFormatException.class,
                                () -> CorpusInputCodec.decode(missingRichness))
                        .getMessage());
        assertTrue(assertThrows(
                        CorpusFormatException.class,
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
                new CorpusInput(InputKind.EXPRESSION, new byte[] {4, 5, 6}),
                CorpusInputCodec.decode(encoded));
    }

    @Test
    void recognizesModuleInputs() throws Exception {
        var corpusInput = new CorpusInput(InputKind.MODULE, new byte[] {9});

        assertEquals(corpusInput, CorpusInputCodec.decode(CorpusInputCodec.encode(corpusInput)));
    }

    @Test
    void corpusInputOwnsItsByteArray() {
        var source = new byte[] {1, 2};
        var corpusInput = new CorpusInput(InputKind.EXPRESSION, source);
        source[0] = 9;
        var returned = corpusInput.input();
        returned[1] = 9;

        assertArrayEquals(new byte[] {1, 2}, corpusInput.input());
    }

    @Test
    void rejectsMalformedOrNonMapCbor() {
        var truncated = assertThrows(
                CorpusFormatException.class,
                () -> CorpusInputCodec.decode(new byte[] {(byte) 0xa1}));
        var array = assertThrows(
                CorpusFormatException.class,
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
                CorpusFormatException.class,
                () -> CorpusInputCodec.decode(missingInput));
        var wrongType = assertThrows(
                CorpusFormatException.class,
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
        var minimal = CorpusInputCodec.encode(new CorpusInput(InputKind.EXPRESSION, new byte[0]));
        var trailing = Arrays.copyOf(minimal, minimal.length + 1);
        trailing[trailing.length - 1] = (byte) 0xf6;

        assertEquals(
                "duplicate field: kind",
                assertThrows(
                                CorpusFormatException.class,
                                () -> CorpusInputCodec.decode(duplicate))
                        .getMessage());
        assertEquals(
                "unknown input kind: declaration",
                assertThrows(
                                CorpusFormatException.class,
                                () -> CorpusInputCodec.decode(unknownKind))
                        .getMessage());
        assertEquals(
                "trailing data after top-level map",
                assertThrows(
                                CorpusFormatException.class,
                                () -> CorpusInputCodec.decode(trailing))
                        .getMessage());
    }

    /** A field given twice inside the gen map is rejected, and named by its path. */
    @Test
    void rejectsDuplicateGenerationFields() throws Exception {
        var duplicateCohort = cbor(generator -> {
            generator.writeStartObject(null, 3);
            generator.writeStringField("kind", "expr");
            generator.writeBinaryField("input", new byte[0]);
            generator.writeObjectFieldStart("gen");
            generator.writeNumberField("cohort", 1);
            generator.writeNumberField("cohort", 2);
            generator.writeNumberField("richness", 2.0);
            generator.writeEndObject();
            generator.writeEndObject();
        });

        assertEquals(
                "duplicate field: gen.cohort",
                assertThrows(
                                CorpusFormatException.class,
                                () -> CorpusInputCodec.decode(duplicateCohort))
                        .getMessage());
    }

    /** Stage metadata does not decide an input's identity, whatever shape it has. */
    @Test
    void decodesAnInputBesideStageMetadataItDoesNotUnderstand() throws Exception {
        var encoded = cbor(generator -> {
            generator.writeStartObject(null, 3);
            generator.writeStringField("kind", "expr");
            generator.writeBinaryField("input", new byte[] {7});
            generator.writeObjectFieldStart("stages");
            generator.writeObjectFieldStart("future-stage");
            generator.writeStringField("outcome", "who knows");
            generator.writeEndObject();
            generator.writeEndObject();
            generator.writeEndObject();
        });

        assertEquals(
                new CorpusInput(InputKind.EXPRESSION, new byte[] {7}), CorpusInputCodec.decode(encoded));
    }
}
