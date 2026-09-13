package io.github.tlaplus.hardening.gen;

import at.forsyte.apalache.tla.lir.NameEx;
import at.forsyte.apalache.tla.lir.OperEx;
import at.forsyte.apalache.tla.lir.OperT1;
import at.forsyte.apalache.tla.lir.TlaEx;
import at.forsyte.apalache.tla.lir.oper.TlaOper;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import org.apalache_mc.tla.jir.TlaExpressions;
import org.apalache_mc.tla.jir.TlaOperators;
import org.apalache_mc.tla.jir.TlaTypes;

/** Deterministic samples of generated modules that exhibit a shape an integration test needs. */
public final class GeneratedSpecSamples {
    private static final Set<TlaOper> VARIANT_READS = Set.of(
            TlaOperators.VARIANT_GET_UNSAFE, TlaOperators.VARIANT_GET_OR_ELSE,
            TlaOperators.VARIANT_TAG, TlaOperators.VARIANT_FILTER);

    private GeneratedSpecSamples() {}

    /**
     * Decodes random inputs from a fixed seed until {@code wanted} accepted modules are found or
     * {@code attempts} inputs are spent, skipping rejected inputs.
     */
    public static List<GeneratedSpec> collect(IrGenerationConfig config, long seed, int attempts, int wanted,
                                              Predicate<GeneratedSpec> accepts) {
        var generator = IrGenerators.specs(config);
        var random = new Random(seed);
        var samples = new ArrayList<GeneratedSpec>();
        for (var sample = 0; sample < attempts && samples.size() < wanted; sample++) {
            var input = new byte[512 + random.nextInt(1536)];
            random.nextBytes(input);
            try {
                var spec = generator.generate(input);
                if (accepts.test(spec)) {
                    samples.add(spec);
                }
            } catch (InputRejectedException rejected) {
                // Rejected inputs carry no module.
            }
        }
        return samples;
    }

    /** Reports whether the module applies an operator to an operator argument, a lambda or a name. */
    public static boolean passesOperatorArgument(GeneratedSpec spec) {
        return anyNode(spec, node -> node instanceof OperEx operator
                && operator.oper() == TlaOperators.OPER_APP
                && TlaExpressions.arguments(operator).stream().skip(1)
                        .anyMatch(argument -> TlaTypes.typeOf(argument) instanceof OperT1));
    }

    /**
     * Reports whether a variant tag, accessor or filter in the module reads a name directly. The
     * read value is the tag's only argument and the others' second, after the tag name; an
     * accessor's fallback may be a name without the variant being one.
     */
    public static boolean readsVariantName(GeneratedSpec spec) {
        return anyNode(spec, node -> node instanceof OperEx operator
                && VARIANT_READS.contains(operator.oper())
                && TlaExpressions.arguments(operator).get(operator.oper() == TlaOperators.VARIANT_TAG ? 0 : 1)
                        instanceof NameEx);
    }

    private static boolean anyNode(GeneratedSpec spec, Predicate<TlaEx> matches) {
        var found = new AtomicBoolean();
        spec.generated().forEach(body -> TlaExpressions.forEach(body, node -> {
            if (matches.test(node)) {
                found.set(true);
            }
        }));
        return found.get();
    }
}
