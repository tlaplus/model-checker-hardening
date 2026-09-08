package io.github.tlaplus.hardening.workflow.input;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.tlaplus.hardening.common.Digests;
import io.github.tlaplus.hardening.common.TlaExpressions;
import io.github.tlaplus.hardening.config.FuzzTlaConfig;
import io.github.tlaplus.hardening.config.OperatorLibraryConfig;
import io.github.tlaplus.hardening.gen.Draw;
import io.github.tlaplus.hardening.gen.ExpressionCategory;
import io.github.tlaplus.hardening.gen.ExpressionLimits;
import io.github.tlaplus.hardening.gen.InputKind;
import io.github.tlaplus.hardening.gen.InputRejectedException;
import io.github.tlaplus.hardening.gen.IrGenerationConfig;
import io.github.tlaplus.hardening.workflow.apalache.ApalacheIrJson;
import io.github.tlaplus.hardening.gen.engine.GeneralExpressionKind;
import io.github.tlaplus.hardening.gen.engine.IntegerExpressionKind;
import io.github.tlaplus.hardening.workflow.spec.SpecArtifact;
import io.github.tlaplus.hardening.workflow.spec.SpecDecoders;
import io.github.tlaplus.hardening.workflow.spec.SpecText;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Cross-revision fixtures, recorded against a3218a2 before changing production code. */
public class DecoderReplayTest {
    @Test
    void preservesDecoderRenderingTypesRichnessAndByteConsumption() throws Exception {
        try (var fixture = getClass().getResourceAsStream("/gen/decoder-replay.txt")) {
            assertEquals(new String(fixture.readAllBytes(), StandardCharsets.UTF_8), replay());
        }
    }

    /** Fixture recording is explicit, never an option of the test itself. */
    public static void main(String[] arguments) throws Exception {
        Files.writeString(Path.of(arguments[0]), replay());
    }

    private static String replay() throws Exception {
        var defaults = IrGenerationConfig.defaults();
        var configurations = new LinkedHashMap<String, SpecDecoders>();
        configurations.put("default", SpecDecoders.of(defaults));
        configurations.put("all", SpecDecoders.of(defaults.withIgnoredCategories(Set.of())));
        configurations.put("no-set", SpecDecoders.of(defaults.ignoring(ExpressionCategory.SET)));
        configurations.put("weighted", SpecDecoders.of(defaults.withFormWeights(Map.of(
                GeneralExpressionKind.TERMINAL, 8, IntegerExpressionKind.PLUS, 16))));
        configurations.put("bounded", SpecDecoders.of(defaults.withExpressionLimits(
                new ExpressionLimits(1, 3, 4, 2, 2, 2))));
        var config = FuzzTlaConfig.defaults();
        configurations.put("library", SpecDecoders.prepare(new FuzzTlaConfig(
                config.generatedKind(), config.generator(), config.workflow(), config.pbt(),
                new OperatorLibraryConfig(List.of(Path.of("src/test/resources/custom").toAbsolutePath()),
                        List.of(new OperatorLibraryConfig.Module("PolyOps", List.of(
                                "Wrapped", "Singleton", "Contains", "Empty", "ReadValue",
                                "WithValue", "First", "Local", "Init")))))));
        var output = new StringBuilder();
        var inputs = inputs();
        for (var entry : configurations.entrySet()) {
            for (var kind : InputKind.values()) {
                for (var index = 0; index < inputs.size(); index++) {
                    var draw = new Draw(inputs.get(index));
                    String result;
                    try {
                        result = describe(draw.draw(entry.getValue().decoder(kind)));
                    } catch (InputRejectedException exception) {
                        result = exception.getClass().getName() + ": " + exception.getMessage();
                    }
                    output.append(entry.getKey()).append('/').append(kind.encodedName())
                            .append('/').append(index).append(' ').append(draw.remaining())
                            .append(' ').append(Digests.digest(result.getBytes(StandardCharsets.UTF_8)))
                            .append('\n');
                }
            }
        }
        return output.toString();
    }

    private static String describe(SpecArtifact artifact) {
        var result = new StringBuilder(SpecText.render(artifact.module()));
        result.append('\n').append(ApalacheIrJson.render(artifact.module()));
        result.append('\n').append(artifact.length());
        result.append('\n').append(CollectionRichness.score(artifact.generated(), 2.0));
        artifact.generated().forEach(expression -> TlaExpressions.forEach(expression,
                node -> result.append('\n').append(node.typeTag())));
        artifact.standaloneExpression().ifPresent(expression -> result.append('\n').append(expression));
        return result.toString();
    }

    private static List<byte[]> inputs() {
        var inputs = new ArrayList<byte[]>();
        var random = new Random(0x51ced);
        for (var length : List.of(0, 1, 2, 3, 8, 16, 32, 128, 512, 2048)) {
            for (var sample = 0; sample < 24; sample++) {
                var bytes = new byte[length];
                random.nextBytes(bytes);
                inputs.add(bytes);
            }
            for (var value : List.of(0, 1, 255)) {
                var bytes = new byte[length];
                Arrays.fill(bytes, (byte) (int) value);
                inputs.add(bytes);
            }
        }
        return inputs;
    }
}
