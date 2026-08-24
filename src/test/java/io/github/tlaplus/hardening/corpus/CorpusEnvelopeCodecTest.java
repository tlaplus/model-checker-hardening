package io.github.tlaplus.hardening.corpus;

import static io.github.tlaplus.hardening.corpus.CborDocuments.FACTORY;
import static io.github.tlaplus.hardening.corpus.CborDocuments.cbor;
import static io.github.tlaplus.hardening.corpus.CborDocuments.writeStage;
import static io.github.tlaplus.hardening.corpus.CborDocuments.writeTaggedEpoch;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.tlaplus.hardening.checker.CheckerFailure;
import io.github.tlaplus.hardening.checker.CheckerFailureCode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CorpusEnvelopeCodecTest {
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

        var updated = CorpusEnvelopeCodec.withStageMetadata(
                encoded,
                new StageMetadata(
                        "parser",
                        CorpusVerdict.PASS,
                        Instant.ofEpochSecond(10),
                        Instant.ofEpochSecond(12)));

        var tree = new ObjectMapper(FACTORY).readTree(updated);
        assertEquals(42, tree.path("future").path("answer").intValue());
        assertEquals("kept", tree.path("stages").path("other").path("verdict").textValue());
        assertEquals("pass", tree.path("stages").path("parser").path("verdict").textValue());

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

    /**
     * A CBOR tag does not survive in a parsed tree, so a stage already in the document has to be
     * written back through the stage writer rather than copied. This is what that protects.
     */
    @Test
    void keepsEarlierStageTimesTaggedWhenAnotherStageIsAdded() throws Exception {
        var input = CorpusInputCodec.encode(CorpusInput.expression(new byte[] {4, 2}));
        var withParser = CorpusEnvelopeCodec.withStageMetadata(
                input,
                new StageMetadata(
                        "parser", CorpusVerdict.PASS, Instant.ofEpochSecond(10), Instant.ofEpochSecond(12)));

        var withTlc = CorpusEnvelopeCodec.withStageMetadata(
                withParser,
                new StageMetadata(
                        "tlc", CorpusVerdict.PASS, Instant.ofEpochSecond(20), Instant.ofEpochSecond(21)));
        var withApalache = CorpusEnvelopeCodec.withStageMetadata(
                withTlc,
                new StageMetadata(
                        "apalache",
                        CorpusVerdict.PASS,
                        Instant.ofEpochSecond(30),
                        Instant.ofEpochSecond(31)));

        // Every stage still decodes, which requires all six timestamps to have kept their tag.
        assertEquals(
                List.of("parser", "tlc", "apalache"),
                CorpusEnvelopeCodec.decodeEnvelope(withApalache).stages().stream()
                        .map(StageMetadata::stage)
                        .toList());
        assertEquals(
                Instant.ofEpochSecond(10),
                CorpusEnvelopeCodec.decodeEnvelope(withApalache).stages().getFirst().startTime());
    }

    @Test
    void preservesGenerationMetadataWhenAddingStageMetadata() throws Exception {
        var generation = new GenerationMetadata(4, 8.0);
        var encoded = CorpusInputCodec.encode(
                CorpusInput.expression(new byte[] {4, 2}), generation);

        var updated = CorpusEnvelopeCodec.withStageMetadata(
                encoded,
                new StageMetadata(
                        "parser",
                        CorpusVerdict.PASS,
                        Instant.ofEpochSecond(10),
                        Instant.ofEpochSecond(12)));

        var envelope = CorpusEnvelopeCodec.decodeEnvelope(updated);
        assertEquals(generation, envelope.generation().orElseThrow());
        assertEquals(CorpusVerdict.PASS, envelope.stages().getFirst().verdict());
    }

    @Test
    void mergesDisjointCheckerStagesWithoutDroppingExtensions() throws Exception {
        var tlc = checkerBranch("tlc", "pass", "tlc-extension", null);
        var apalache = checkerBranch(
                "apalache",
                "fail",
                "apalache-extension",
                CheckerFailureCode.TYPECHECK.encodedCode());

        var merged = CorpusEnvelopeCodec.mergeWithStageMetadata(
                List.of(tlc, apalache),
                new StageMetadata(
                        "aggregator",
                        CorpusVerdict.FAIL,
                        Instant.ofEpochSecond(7),
                        Instant.ofEpochSecond(8)));
        var tree = new ObjectMapper(FACTORY).readTree(merged);

        assertEquals(42, tree.path("future").path("answer").intValue());
        assertEquals(
                "tlc-extension",
                tree.path("stages").path("tlc").path("extension").textValue());
        assertEquals(
                "apalache-extension",
                tree.path("stages").path("apalache").path("extension").textValue());
        assertEquals(
                List.of("parser", "tlc", "apalache", "aggregator"),
                CorpusEnvelopeCodec.decodeEnvelope(merged).stages().stream()
                        .map(StageMetadata::stage)
                        .toList());
    }

    @Test
    void encodesAndDecodesNumericCheckerFailureMetadata() throws Exception {
        var failure = new CheckerFailure(
                CheckerFailureCode.SPEC_EVAL,
                Optional.of("Attempted to apply Head to the empty sequence."));
        var encoded = CorpusEnvelopeCodec.withStageMetadata(
                CorpusInputCodec.encode(CorpusInput.expression(new byte[] {4, 2})),
                new StageMetadata(
                        "tlc",
                        CorpusVerdict.FAIL,
                        Instant.ofEpochSecond(10),
                        Instant.ofEpochSecond(12),
                        Optional.of(failure)));

        var envelope = CorpusEnvelopeCodec.decodeEnvelope(encoded);
        var tree = new ObjectMapper(FACTORY).readTree(encoded);

        assertEquals(Optional.of(failure), envelope.stages().getFirst().failure());
        assertEquals(75, tree.path("stages").path("tlc").path("code").intValue());
        assertEquals(
                failure.detail().orElseThrow(),
                tree.path("stages").path("tlc").path("detail").textValue());
    }

    @Test
    void rejectsInvalidCheckerFailureMetadata() throws Exception {
        var unknownCode = checkerEnvelope("fail", 76, null);
        var detailWithoutCode = checkerEnvelope("fail", null, "undefined expression");
        var codeOnPass = checkerEnvelope("pass", 75, null);
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

        assertInvalidEnvelope(unknownCode, "unsupported checker failure code: 76");
        assertInvalidEnvelope(detailWithoutCode, "requires field 'code'");
        assertInvalidEnvelope(codeOnPass, "requires the fail verdict");
        assertInvalidEnvelope(excessiveDetail, "must not exceed 80 characters");
        assertInvalidEnvelope(multilineDetail, "must be a single line");
        assertInvalidEnvelope(textCode, "must be an integer");
    }

    @Test
    void appliesOnlyGenericFailureRulesToUnknownStages() throws Exception {
        var encoded = stageEnvelope("future-checker", "fail", 75, "undefined expression");

        var metadata = CorpusEnvelopeCodec.decodeEnvelope(encoded).stages().getFirst();

        assertEquals("future-checker", metadata.stage());
        assertEquals(
                Optional.of(new CheckerFailure(
                        CheckerFailureCode.SPEC_EVAL,
                        Optional.of("undefined expression"))),
                metadata.failure());
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

        var envelope = CorpusEnvelopeCodec.decodeEnvelope(encoded);

        assertEquals(CorpusInput.expression(new byte[] {4, 2}), envelope.corpusInput());
        assertEquals(
                List.of(
                        new StageMetadata(
                                "generator",
                                CorpusVerdict.PASS,
                                Instant.ofEpochSecond(10),
                                Instant.ofEpochSecond(10)),
                        new StageMetadata(
                                "parser",
                                CorpusVerdict.FAIL,
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
                        CorpusFormatException.class,
                        () -> CorpusEnvelopeCodec.decodeEnvelope(untaggedTime))
                .getMessage()
                .contains("must be a tag-1 epoch number"));
        assertTrue(assertThrows(
                        CorpusFormatException.class,
                        () -> CorpusEnvelopeCodec.decodeEnvelope(reversedTimes))
                .getMessage()
                .contains("endTime must not precede startTime"));
    }

    /** A stage given twice is rejected, and so is a field repeated inside one stage. */
    @Test
    void rejectsDuplicateStagesAndStageFields() throws Exception {
        var duplicateStage = cbor(generator -> {
            generator.writeStartObject(null, 3);
            generator.writeStringField("kind", "expr");
            generator.writeBinaryField("input", new byte[0]);
            generator.writeObjectFieldStart("stages");
            var start = Instant.ofEpochSecond(1);
            var end = Instant.ofEpochSecond(2);
            writeStage(generator, "tlc", "pass", start, end);
            writeStage(generator, "tlc", "pass", start, end);
            generator.writeEndObject();
            generator.writeEndObject();
        });
        var duplicateVerdict = cbor(generator -> {
            generator.writeStartObject(null, 3);
            generator.writeStringField("kind", "expr");
            generator.writeBinaryField("input", new byte[0]);
            generator.writeObjectFieldStart("stages");
            generator.writeObjectFieldStart("tlc");
            generator.writeStringField("verdict", "pass");
            generator.writeStringField("verdict", "pass");
            generator.writeEndObject();
            generator.writeEndObject();
            generator.writeEndObject();
        });

        assertEquals(
                "duplicate field: stages.tlc",
                assertThrows(
                                CorpusFormatException.class,
                                () -> CorpusEnvelopeCodec.decodeEnvelope(duplicateStage))
                        .getMessage());
        assertEquals(
                "duplicate field: stages.tlc.verdict",
                assertThrows(
                                CorpusFormatException.class,
                                () -> CorpusEnvelopeCodec.decodeEnvelope(duplicateVerdict))
                        .getMessage());
    }

    /**
     * The verdict is a closed set: a stage records one of the outcomes the corpus stores entries
     * by. The stage name stays open, so a stage this build never heard of still reads.
     */
    @Test
    void rejectsAnUnknownVerdictButNotAnUnknownStageName() throws Exception {
        var unknownVerdict = cbor(generator -> {
            generator.writeStartObject(null, 3);
            generator.writeStringField("kind", "expr");
            generator.writeBinaryField("input", new byte[0]);
            generator.writeObjectFieldStart("stages");
            writeStage(
                    generator,
                    "tlc",
                    "inconclusive",
                    Instant.ofEpochSecond(10),
                    Instant.ofEpochSecond(12));
            generator.writeEndObject();
            generator.writeEndObject();
        });
        var unknownStage = cbor(generator -> {
            generator.writeStartObject(null, 3);
            generator.writeStringField("kind", "expr");
            generator.writeBinaryField("input", new byte[0]);
            generator.writeObjectFieldStart("stages");
            writeStage(
                    generator,
                    "future-stage",
                    "pass",
                    Instant.ofEpochSecond(10),
                    Instant.ofEpochSecond(12));
            generator.writeEndObject();
            generator.writeEndObject();
        });

        assertInvalidEnvelope(unknownVerdict, "unsupported corpus verdict: inconclusive");

        var stage = CorpusEnvelopeCodec.decodeEnvelope(unknownStage).stages().getFirst();
        assertEquals("future-stage", stage.stage());
        assertEquals(CorpusVerdict.PASS, stage.verdict());
    }

    /**
     * The stage map's definite length has to cover the verdict, both timestamps, an optional
     * failure code, an optional detail, and every field this build does not model. Rewriting a
     * stage that carries all of them at once is the case where a hand-counted size goes wrong.
     */
    @Test
    void rewritesAStageCarryingAFailureDetailAndUnmodelledFields() throws Exception {
        var encoded = cbor(generator -> {
            generator.writeStartObject(null, 3);
            generator.writeStringField("kind", "expr");
            generator.writeBinaryField("input", new byte[] {7});
            generator.writeObjectFieldStart("stages");
            generator.writeObjectFieldStart("tlc");
            generator.writeStringField("verdict", "fail");
            generator.writeNumberField("code", 75);
            generator.writeStringField("detail", "old detail");
            writeTaggedEpoch(generator, "startTime", Instant.ofEpochSecond(10));
            writeTaggedEpoch(generator, "endTime", Instant.ofEpochSecond(12));
            generator.writeNumberField("futureCount", 5);
            generator.writeStringField("futureNote", "kept");
            generator.writeEndObject();
            generator.writeEndObject();
            generator.writeEndObject();
        });

        var updated = CorpusEnvelopeCodec.withStageMetadata(
                encoded,
                new StageMetadata(
                        "tlc",
                        CorpusVerdict.FAIL,
                        Instant.ofEpochSecond(20),
                        Instant.ofEpochSecond(25),
                        Optional.of(new CheckerFailure(
                                CheckerFailureCode.COUNTEREXAMPLE, Optional.of("new detail")))));

        var stage = CorpusEnvelopeCodec.decodeEnvelope(updated).stages().getFirst();
        assertEquals(CorpusVerdict.FAIL, stage.verdict());
        assertEquals(Instant.ofEpochSecond(20), stage.startTime());
        assertEquals(Instant.ofEpochSecond(25), stage.endTime());
        assertEquals(
                CheckerFailureCode.COUNTEREXAMPLE, stage.failure().orElseThrow().code());
        assertEquals("new detail", stage.failure().orElseThrow().detail().orElseThrow());

        var tree = new ObjectMapper(FACTORY).readTree(updated).get("stages").get("tlc");
        assertEquals(5, tree.get("futureCount").intValue());
        assertEquals("kept", tree.get("futureNote").textValue());
    }

    /**
     * The roster of stages that must classify a failure is this build's, so a document recording a
     * combination this build would not write still reads. Otherwise a later build whose parser
     * classifies failures would produce entries this one calls malformed.
     */
    @Test
    void readsStageMetadataThisBuildWouldNotWrite() throws Exception {
        var parserWithCode = stageEnvelope("parser", "fail", 150, null);
        var checkerWithoutCode = checkerEnvelope("fail", null, null);

        var parserStage = CorpusEnvelopeCodec.decodeEnvelope(parserWithCode).stages().getFirst();
        assertEquals("parser", parserStage.stage());
        assertEquals(
                CheckerFailureCode.PARSE, parserStage.failure().orElseThrow().code());

        var checkerStage =
                CorpusEnvelopeCodec.decodeEnvelope(checkerWithoutCode).stages().getFirst();
        assertEquals(CorpusVerdict.FAIL, checkerStage.verdict());
        assertEquals(Optional.empty(), checkerStage.failure());
    }

    private byte[] checkerEnvelope(String verdict, Integer code, String detail)
            throws Exception {
        return stageEnvelope("tlc", verdict, code, detail);
    }

    private byte[] checkerBranch(
            String checker, String verdict, String extension, Integer code) throws Exception {
        var checkerStart = "tlc".equals(checker) ? 3 : 5;
        return cbor(generator -> {
            generator.writeStartObject(null, 4);
            generator.writeStringField("kind", "expr");
            generator.writeBinaryField("input", new byte[] {4, 2});
            generator.writeObjectFieldStart("future");
            generator.writeNumberField("answer", 42);
            generator.writeEndObject();
            generator.writeObjectFieldStart("stages");
            writeStage(
                    generator,
                    "parser",
                    "pass",
                    Instant.ofEpochSecond(1),
                    Instant.ofEpochSecond(2));
            generator.writeObjectFieldStart(checker);
            generator.writeStringField("verdict", verdict);
            if (code != null) {
                generator.writeNumberField("code", code);
            }
            writeTaggedEpoch(generator, "startTime", Instant.ofEpochSecond(checkerStart));
            writeTaggedEpoch(generator, "endTime", Instant.ofEpochSecond(checkerStart + 1));
            generator.writeStringField("extension", extension);
            generator.writeEndObject();
            generator.writeEndObject();
            generator.writeEndObject();
        });
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
                        CorpusFormatException.class,
                        () -> CorpusEnvelopeCodec.decodeEnvelope(encoded))
                .getMessage()
                .contains(message));
    }
}
