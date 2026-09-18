package io.github.tlaplus.hardening.signature;

import static org.junit.jupiter.api.Assertions.assertTrue;

import at.forsyte.apalache.tla.lir.TlaEx;
import at.forsyte.apalache.tla.lir.TlaModule;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apalache_mc.tla.jir.ExpressionPair;
import org.apalache_mc.tla.jir.TlaModules;
import org.apalache_mc.tla.jir.TlaTypedScopeUncheckedBuilder;
import org.apalache_mc.tla.jir.TlaTypes;

/**
 * Builds the IR shapes that the repository's signature databases are tested against.
 *
 * <p>A case is a pair: an expression its signature must match, and the nearest legal expression
 * it must not match.
 */
final class SignatureShapes {
    final TlaTypedScopeUncheckedBuilder builder = new TlaTypedScopeUncheckedBuilder();

    /** The cases of {@code signatures/known-defects.toml}, keyed by signature id. */
    Map<String, List<TlaEx>> shippedCases() {
        var step = step();
        var zero = builder.integer(0);
        var cases = new HashMap<String, List<TlaEx>>();
        cases.put("modulo-by-literal-zero",
                List.of(builder.mod(step, zero), builder.mod(step, builder.integer(2))));
        cases.put("division-by-literal-zero",
                List.of(builder.div(step, zero), builder.div(step, builder.integer(2))));
        cases.put("zero-power-zero",
                List.of(builder.exp(zero, builder.integer(0)), builder.exp(step, zero)));
        cases.put("sequence-set",
                List.of(builder.seqSet(builder.enumSet(builder.integer(1))),
                        builder.enumSet(builder.seq(builder.integer(1)))));
        cases.put("string-set",
                List.of(builder.stringSet(), builder.enumSet(builder.str("a"))));
        cases.put("tlc-temporal-equivalence",
                List.of(builder.equiv(flag(), eventuallyFlag()), builder.equiv(flag(), flag())));
        cases.put("tlc-temporal-case",
                List.of(builder.caseSplit(new ExpressionPair<>(flag(), eventuallyFlag())),
                        builder.caseSplit(new ExpressionPair<>(flag(), flag()))));
        cases.put("tlc-temporal-unbounded-quantifier",
                List.of(builder.exists(bound(), eventuallyFlag()),
                        builder.exists(bound(), builder.booleanSet(), eventuallyFlag())));
        cases.put("tlc-eventually-action",
                List.of(builder.eventually(builder.noStutter(action(), flag())),
                        builder.always(builder.stutter(action(), flag()))));
        cases.put("tlc-always-action-under-temporal",
                List.of(builder.eventually(builder.always(builder.stutter(action(), flag()))),
                        builder.always(builder.stutter(action(), flag()))));
        cases.put("tlc-fairness-under-eventuality",
                List.of(builder.eventually(builder.weakFair(flag(), action())),
                        builder.always(builder.weakFair(flag(), action()))));
        cases.put("integer-literal-outside-tlc-range",
                List.of(builder.plus(step, builder.integer(new BigInteger("2147483648"))),
                        builder.plus(step, builder.integer(Integer.MAX_VALUE))));
        cases.put("tlc-always-action-under-connective",
                List.of(builder.not(builder.always(builder.stutter(action(), flag()))),
                        builder.and(builder.always(builder.stutter(action(), flag())), flag())));
        return cases;
    }

    TlaEx step() {
        return builder.name("step", TlaTypes.INT);
    }

    TlaEx flag() {
        return builder.name("flag", TlaTypes.BOOL);
    }

    TlaEx eventuallyFlag() {
        return builder.eventually(flag());
    }

    TlaEx bound() {
        return builder.name("k", TlaTypes.BOOL);
    }

    TlaEx action() {
        return builder.primeEq(flag(), builder.bool(true));
    }

    /** Wraps an expression in a module whose only evaluated definition is {@code Expression}. */
    TlaModule module(TlaEx expression) {
        return TlaModules.create("KnownDefect", List.of(builder.decl("Expression", expression)));
    }

    /** The ids of the signatures that match the expression, in database order. */
    List<String> ids(KnownDefectDatabase database, TlaEx expression) {
        return database.matches(module(expression), List.of("Expression")).stream()
                .map(match -> match.defect().id())
                .toList();
    }

    /** Asserts that every reference of the database names a file, relative to the database. */
    static void assertReferencesExist(Path file, KnownDefectDatabase database) {
        for (var signature : database.signatures()) {
            for (var reference : signature.references()) {
                assertTrue(
                        Files.isRegularFile(file.getParent().resolve(reference)),
                        signature.id() + " references a missing document: " + reference);
            }
        }
    }
}
