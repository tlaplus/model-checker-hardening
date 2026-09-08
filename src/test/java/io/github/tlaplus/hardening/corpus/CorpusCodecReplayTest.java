package io.github.tlaplus.hardening.corpus;

import static io.github.tlaplus.hardening.corpus.CborDocuments.cbor;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.tlaplus.hardening.checker.CheckerFailure;
import io.github.tlaplus.hardening.checker.CheckerFailureCode;
import io.github.tlaplus.hardening.common.GeneratorAggregate;
import io.github.tlaplus.hardening.gen.InputKind;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;

/** Encoding bytes and diagnostics recorded in a separate JVM running a3218a2. */
public class CorpusCodecReplayTest {
    @Test
    void preservesEncodedBytesAndMalformedInputDiagnostics() throws Exception {
        try (var fixture = getClass().getResourceAsStream("/corpus/codec-replay.txt")) {
            assertEquals(new String(fixture.readAllBytes(), StandardCharsets.UTF_8), replay());
        }
    }

    public static void main(String[] arguments) throws Exception {
        Files.writeString(Path.of(arguments[0]), replay());
    }

    private static String replay() throws Exception {
        var output = new StringBuilder();
        for (var kind : InputKind.values()) {
            var input = new CorpusInput(kind, new byte[] {0, 1, -1, 127, -128});
            append(output, kind + "/minimal", () -> encoded(CorpusInputCodec.encode(input)));
            for (var richness : List.of(0.0, -0.0, 0.25, Double.MIN_VALUE, Double.MAX_VALUE)) {
                append(output, kind + "/generation/" + richness,
                        () -> encoded(CorpusInputCodec.encode(input, new GenerationMetadata(7, richness))));
            }
        }
        append(output, "empty-statistics", () -> hex(CorpusRunStatisticsCodec.encode(CorpusRunStatistics.empty())));
        var statistics = new CorpusRunStatistics(1234, 234, Map.of(CorpusStage.PARSER, 42L),
                new GeneratorAggregate(9, 1, 2, 3, new GeneratorAggregate.Richness(4, 0.25, 9, 3)));
        append(output, "statistics", () -> {
            var bytes = CorpusRunStatisticsCodec.encode(statistics);
            assertEquals(statistics, CorpusRunStatisticsCodec.decode(bytes));
            return hex(bytes);
        });

        var base = extended(42, "opaque");
        var parser = new StageMetadata("parser", CorpusVerdict.PASS,
                Instant.ofEpochSecond(-2, 250_000_000), Instant.ofEpochSecond(3, 500_000_000));
        var parsed = CorpusEnvelopeCodec.withStageMetadata(base, parser);
        append(output, "parser", () -> hex(parsed));
        var failed = CorpusEnvelopeCodec.withStageMetadata(parsed, new StageMetadata("tlc", CorpusVerdict.FAIL,
                Instant.ofEpochSecond(4), Instant.ofEpochSecond(6),
                Optional.of(new CheckerFailure(CheckerFailureCode.SPEC_EVAL, Optional.of("undefined expression")))));
        var counterexample = CorpusEnvelopeCodec.withStageMetadata(parsed,
                new StageMetadata("apalache", CorpusVerdict.COUNTEREXAMPLE,
                        Instant.ofEpochSecond(7), Instant.ofEpochSecond(8)));
        var aggregate = new StageMetadata("aggregator", CorpusVerdict.FAIL,
                Instant.ofEpochSecond(9), Instant.ofEpochSecond(10));
        append(output, "merged", () -> hex(CorpusEnvelopeCodec.mergeWithStageMetadata(
                List.of(failed, counterexample), aggregate)));
        append(output, "root-conflict", () -> hex(CorpusEnvelopeCodec.mergeWithStageMetadata(
                List.of(base, extended(43, "opaque")), aggregate)));
        append(output, "opaque-stage-conflict", () -> hex(CorpusEnvelopeCodec.mergeWithStageMetadata(
                List.of(base, extended(42, "different")), aggregate)));
        append(output, "parsed-stage-conflict", () -> hex(CorpusEnvelopeCodec.mergeWithStageMetadata(
                List.of(parsed, CorpusEnvelopeCodec.withStageMetadata(base,
                        new StageMetadata("parser", CorpusVerdict.FAIL, parser.startTime(), parser.endTime()))), aggregate)));
        append(output, "opaque-decode", () -> CorpusEnvelopeCodec.decodeEnvelope(parsed).stages().toString());

        var minimal = CorpusInputCodec.encode(new CorpusInput(InputKind.EXPRESSION, new byte[] {0, 1}));
        append(output, "fractional-times", () -> CorpusEnvelopeCodec.decodeEnvelope(
                CorpusEnvelopeCodec.withStageMetadata(minimal, parser)).stages().toString());
        for (int length = 0; length < minimal.length; length++) {
            var truncated = Arrays.copyOf(minimal, length);
            append(output, "truncated/" + length, () -> CorpusInputCodec.decode(truncated).kind().encodedName());
        }
        for (var field : List.of("kind", "input", "gen", "stages")) {
            var malformed = cbor(g -> {
                g.writeStartObject();
                g.writeBooleanField(field, true);
                g.writeEndObject();
            });
            append(output, "wrong-shape/" + field, () -> CorpusEnvelopeCodec.decodeEnvelope(malformed).toString());
        }
        return output.toString();
    }

    private static byte[] extended(int answer, String opaque) throws Exception {
        return cbor(g -> {
            g.writeStartObject();
            g.writeStringField("kind", "module");
            g.writeBinaryField("input", new byte[] {4, 2});
            g.writeObjectFieldStart("future");
            g.writeNumberField("answer", answer);
            g.writeBinaryField("binary", new byte[] {-1});
            g.writeEndObject();
            g.writeObjectFieldStart("stages");
            g.writeStringField("future-stage", opaque);
            g.writeEndObject();
            g.writeEndObject();
        });
    }

    private static String encoded(byte[] bytes) throws Exception {
        var envelope = CorpusEnvelopeCodec.decodeEnvelope(bytes);
        return hex(bytes) + " " + envelope.corpusInput().kind() + " " + hex(envelope.corpusInput().input())
                + " " + envelope.generation();
    }

    private static String hex(byte[] bytes) {
        return HexFormat.of().formatHex(bytes);
    }

    private static void append(StringBuilder output, String name, Callable<String> operation) {
        output.append(name).append(' ');
        try {
            output.append(operation.call());
        } catch (Exception exception) {
            output.append(exception.getClass().getName()).append(": ").append(exception.getMessage());
        }
        output.append('\n');
    }
}
