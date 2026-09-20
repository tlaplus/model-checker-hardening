package io.github.tlaplus.hardening.signature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import at.forsyte.apalache.tla.lir.TlaEx;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apalache_mc.tla.jir.ExpressionPair;
import org.apalache_mc.tla.jir.TlaTypedScopeUncheckedBuilder;
import org.apalache_mc.tla.jir.TlaTypes;
import org.junit.jupiter.api.Test;
import org.tomlj.Toml;

/** Checks the recall-first database {@code signatures/all-defects.toml}. */
class AllDefectsTest {
    private static final Path DATABASE = KnownDefectDatabase.ALL;

    /** Documents that no signature can express; the database header gives the reasons. */
    private static final Set<String> UNCOVERED = Set.of(
            "conformance/constant-false-invariant.md",
            "conformance/constant-property-tlc-rejects.md",
            "conformance/label-inside-except.md",
            "conformance/order-sensitive-set-fold.md",
            "conformance/vacuous-initial-predicate.md",
            "findings/SANY/sany-002.md",
            "findings/apalache-json/apalache-json-001.md",
            "findings/apalache-json/apalache-json-002.md",
            "findings/apalache-printer/apalache-printer-001.md",
            "findings/apalache-printer/apalache-printer-002.md",
            "findings/apalache-printer/apalache-printer-003.md",
            "findings/apalache-printer/apalache-printer-004.md",
            "findings/apalache-printer/apalache-printer-005.md",
            "findings/apalache-printer/apalache-printer-006.md",
            "findings/apalache-printer/apalache-printer-007.md",
            "findings/TLC/tlc-013.md");

    private final SignatureShapes shapes = new SignatureShapes();
    private final TlaTypedScopeUncheckedBuilder builder = shapes.builder;

    @Test
    void everyReferenceNamesADocumentInTheRepository() throws Exception {
        SignatureShapes.assertReferencesExist(DATABASE, KnownDefectDatabase.load(List.of(DATABASE)));
    }

    /** The shipped signatures open the database unchanged, so primary signatures stay the same. */
    @Test
    void startsWithTheShippedSignatures() throws Exception {
        var shipped = KnownDefectDatabase.load(List.of(KnownDefectDatabase.SHIPPED)).signatures();
        var all = KnownDefectDatabase.load(List.of(DATABASE)).signatures();

        assertTrue(all.size() > shipped.size());
        for (int i = 0; i < shipped.size(); i++) {
            var expected = shipped.get(i);
            var actual = all.get(i);
            assertEquals(expected.id(), actual.id());
            assertEquals(expected.description(), actual.description(), expected.id());
            assertEquals(patterns(KnownDefectDatabase.SHIPPED).get(i), patterns(DATABASE).get(i),
                    expected.id());
            assertTrue(actual.references().containsAll(expected.references()), expected.id());
        }
    }

    /** Every finding and conformance document is referenced or explicitly left uncovered. */
    @Test
    void everyDocumentIsCoveredOrListedAsUncovered() throws Exception {
        var referenced = KnownDefectDatabase.load(List.of(DATABASE)).signatures().stream()
                .flatMap(signature -> signature.references().stream())
                .map(reference -> DATABASE.getParent().resolve(reference).normalize().toString())
                .collect(Collectors.toSet());
        var documents = new TreeSet<String>();
        documents.addAll(documents("conformance"));
        documents.addAll(documents("findings"));

        for (var document : documents) {
            assertTrue(referenced.contains(document) ^ UNCOVERED.contains(document),
                    document + " must be referenced by a signature or listed as uncovered, not both");
        }
        assertTrue(documents.containsAll(UNCOVERED), "UNCOVERED lists a missing document");
    }

    /** Each signature matches the shape its document records, and not the nearest legal one. */
    @Test
    void everySignatureMatchesItsShapeAndNotItsNeighbour() throws Exception {
        var database = KnownDefectDatabase.load(List.of(DATABASE));
        var cases = new HashMap<>(shapes.shippedCases());
        cases.putAll(newCases());

        assertEquals(
                cases.keySet(),
                database.signatures().stream().map(KnownDefect::id).collect(Collectors.toSet()));
        for (var signature : database.signatures()) {
            var pair = cases.get(signature.id());
            assertTrue(shapes.ids(database, pair.get(0)).contains(signature.id()),
                    signature.id() + " does not match its shape");
            assertFalse(shapes.ids(database, pair.get(1)).contains(signature.id()),
                    signature.id() + " matches its neighbour");
        }
    }

    private Map<String, List<TlaEx>> newCases() {
        var step = shapes.step();
        var flag = shapes.flag();
        var x = builder.name("x", TlaTypes.INT);
        var y = builder.name("y", TlaTypes.INT);
        var one = builder.enumSet(builder.integer(1));
        var sequence = builder.name("s", TlaTypes.sequence(TlaTypes.INT));
        var function = builder.name("f", TlaTypes.function(TlaTypes.INT, TlaTypes.INT));
        var applied = builder.eql(builder.funApply(function, builder.integer(1)), builder.integer(1));
        var fold = builder.foldSet(
                builder.lambda("Keep", builder.name("a", TlaTypes.INT),
                        builder.param("a", TlaTypes.INT), builder.param("b", TlaTypes.INT)),
                builder.integer(0), one);
        var functions = builder.name("fs",
                TlaTypes.set(TlaTypes.function(TlaTypes.INT, TlaTypes.INT)));
        var pairFunction = builder.name("g",
                TlaTypes.function(TlaTypes.tuple(TlaTypes.INT, TlaTypes.INT), TlaTypes.INT));
        var acc = builder.name("acc", TlaTypes.BOOL);
        return Map.ofEntries(
                Map.entry("infinite-integer-set",
                        List.of(builder.in(step, builder.intSet()), builder.in(step, one))),
                Map.entry("powerset",
                        List.of(builder.powerSet(one), builder.enumSet(one))),
                Map.entry("function-set-empty-component",
                        List.of(builder.funSet(builder.emptySet(TlaTypes.INT), one),
                                builder.funSet(one, one))),
                Map.entry("function-set-equality",
                        List.of(builder.eql(builder.funSet(one, one), functions),
                                builder.in(function, builder.funSet(one, one)))),
                Map.entry("function-set-expansion",
                        List.of(builder.filter(function, builder.funSet(one, one), builder.bool(true)),
                                builder.exists(function, builder.funSet(one, one), builder.bool(true)))),
                Map.entry("integer-range",
                        List.of(builder.interval(builder.integer(1), builder.integer(2)),
                                builder.enumSet(builder.integer(1), builder.integer(2)))),
                Map.entry("power",
                        List.of(builder.exp(step, builder.integer(2)),
                                builder.mult(step, builder.integer(2)))),
                Map.entry("division",
                        List.of(builder.div(step, builder.integer(2)),
                                builder.mult(step, builder.integer(2)))),
                Map.entry("modulo-negative-divisor",
                        List.of(builder.mod(step, builder.uminus(builder.integer(1))),
                                builder.mod(step, builder.integer(2)))),
                Map.entry("enabled",
                        List.of(builder.enabled(shapes.action()), shapes.action())),
                Map.entry("fairness",
                        List.of(builder.weakFair(flag, shapes.action()),
                                builder.always(shapes.eventuallyFlag()))),
                Map.entry("empty-sequence-head-or-tail",
                        List.of(builder.head(builder.emptySeq(TlaTypes.INT)), builder.head(sequence))),
                Map.entry("subseq-literal-outside-domain",
                        List.of(builder.subSeq(sequence, builder.integer(0), builder.integer(1)),
                                builder.subSeq(sequence, builder.integer(1), builder.integer(1)))),
                Map.entry("function-application-literal-outside-domain",
                        List.of(builder.funApply(sequence, builder.integer(0)),
                                builder.funApply(sequence, builder.integer(1)))),
                Map.entry("choose-literal-without-witness",
                        List.of(builder.choose(x, one, builder.bool(false)),
                                builder.choose(x, one, builder.eql(x, builder.integer(1))))),
                Map.entry("choose-literal-several-witnesses",
                        List.of(builder.choose(x, one, builder.bool(true)),
                                builder.choose(x, one, builder.eql(x, builder.integer(1))))),
                Map.entry("case-literal-guards",
                        List.of(builder.caseSplit(arm(builder.bool(false), step)),
                                builder.caseSplit(arm(flag, step)))),
                Map.entry("tlc-partial-operator-in-filter",
                        List.of(builder.filter(x, one, builder.eql(builder.funApply(function, x), x)),
                                builder.filter(x, one, builder.eql(x, builder.integer(1))))),
                Map.entry("tlc-union-nonenumerable",
                        List.of(builder.unionAll(builder.enumSet(builder.stringSet())),
                                builder.unionAll(builder.enumSet(one)))),
                Map.entry("tlc-partial-operator-under-eventuality",
                        List.of(builder.eventually(applied), shapes.eventuallyFlag())),
                Map.entry("temporal-under-bounded-quantifier",
                        List.of(builder.exists(shapes.bound(), builder.booleanSet(),
                                        shapes.eventuallyFlag()),
                                builder.exists(shapes.bound(), builder.booleanSet(), flag))),
                Map.entry("temporal-under-if",
                        List.of(builder.ite(flag, shapes.eventuallyFlag(), flag),
                                builder.ite(flag, flag, flag))),
                Map.entry("tlc-double-negation-under-temporal",
                        List.of(builder.always(builder.not(builder.not(builder.always(flag)))),
                                builder.always(builder.always(flag)))),
                Map.entry("sany-temporal-under-modulo",
                        List.of(builder.mod(step, builder.ite(shapes.eventuallyFlag(), step, step)),
                                builder.mod(step, builder.ite(flag, step, step)))),
                Map.entry("apalache-nested-leads-to",
                        List.of(builder.leadsTo(builder.leadsTo(flag, flag), flag),
                                builder.leadsTo(flag, flag))),
                Map.entry("apalache-leads-to-two-binder-function",
                        List.of(builder.leadsTo(builder.eql(
                                        builder.funDef(step, new ExpressionPair<>(x, one),
                                                new ExpressionPair<>(y, one)), pairFunction), flag),
                                builder.leadsTo(builder.eql(
                                        builder.funDef(step, new ExpressionPair<>(x, one)),
                                        function), flag))),
                Map.entry("apalache-nested-one-conjunct",
                        List.of(builder.and(builder.and(flag)), builder.and(flag, flag))),
                Map.entry("apalache-fold-singleton-membership",
                        List.of(builder.foldSet(combinator(builder.notIn(acc, builder.enumSet(acc))),
                                        builder.bool(false), builder.booleanSet()),
                                builder.foldSet(combinator(builder.not(acc)),
                                        builder.bool(false), builder.booleanSet()))),
                Map.entry("apalache-unchanged-after-assignment",
                        List.of(builder.and(shapes.action(), builder.not(builder.unchanged(flag))),
                                builder.and(shapes.action(), builder.unchanged(step)))),
                Map.entry("printer-fold-in-left-operand",
                        List.of(builder.and(builder.eql(step, fold), flag),
                                builder.and(flag, builder.eql(step, fold)))),
                Map.entry("printer-nested-case-arm",
                        List.of(builder.caseSplit(arm(flag, builder.caseSplit(arm(flag, step))),
                                        arm(flag, step)),
                                builder.caseSplit(arm(flag, step),
                                        arm(flag, builder.caseSplit(arm(flag, step)))))),
                Map.entry("printer-set-map-connective-body",
                        List.of(builder.map(builder.and(builder.in(x, one)),
                                        new ExpressionPair<>(x, one)),
                                builder.map(builder.in(x, one), new ExpressionPair<>(x, one)))));
    }

    private static ExpressionPair<TlaEx> arm(TlaEx guard, TlaEx value) {
        return new ExpressionPair<>(guard, value);
    }

    /** A fold combinator over Booleans with the given body in its accumulator {@code acc}. */
    private TlaEx combinator(TlaEx body) {
        return builder.lambda("Step", body,
                builder.param("acc", TlaTypes.BOOL), builder.param("elem", TlaTypes.BOOL));
    }

    /** The finding or conformance documents under a repository directory, relative to its root. */
    private static List<String> documents(String directory) throws IOException {
        try (Stream<Path> files = Files.walk(Path.of(directory))) {
            return files.filter(file -> file.toString().endsWith(".md"))
                    .filter(file -> !file.getFileName().toString().equals("README.md"))
                    .map(Path::toString)
                    .toList();
        }
    }

    /** The {@code match} list of every signature of a database, as written. */
    private static List<List<Object>> patterns(Path database) throws IOException {
        var signatures = Toml.parse(database).getArray("signature");
        return java.util.stream.IntStream.range(0, signatures.size())
                .mapToObj(i -> signatures.getTable(i).getArray("match").toList())
                .toList();
    }
}
