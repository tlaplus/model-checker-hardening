package io.github.tlaplus.hardening.gen.engine;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tlaplus.hardening.gen.Draw;
import io.github.tlaplus.hardening.gen.ExpressionCategory;
import io.github.tlaplus.hardening.gen.IrGenerationConfig;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ExpressionKindCatalogTest {
    private static final Map<ExpressionCategory, Set<ExpressionKind>> KINDS_BY_CATEGORY =
            Map.ofEntries(
                    Map.entry(
                            ExpressionCategory.ACTION,
                            kinds(
                                    GeneralExpressionKind.PRIME,
                                    BooleanExpressionKind.PRIME_EQUAL,
                                    BooleanExpressionKind.UNCHANGED)),
                    Map.entry(
                            ExpressionCategory.TEMPORAL,
                            kinds(
                                    BooleanExpressionKind.STUTTER,
                                    BooleanExpressionKind.NO_STUTTER,
                                    BooleanExpressionKind.ENABLED,
                                    BooleanExpressionKind.ALWAYS,
                                    BooleanExpressionKind.EVENTUALLY,
                                    BooleanExpressionKind.LEADS_TO,
                                    BooleanExpressionKind.GUARANTEES,
                                    BooleanExpressionKind.WEAK_FAIR,
                                    BooleanExpressionKind.STRONG_FAIR)),
                    Map.entry(
                            ExpressionCategory.UNBOUND,
                            kinds(
                                    GeneralExpressionKind.UNBOUNDED_CHOOSE,
                                    BooleanExpressionKind.FORALL_UNBOUNDED,
                                    BooleanExpressionKind.EXISTS_UNBOUNDED)),
                    Map.entry(
                            ExpressionCategory.EXOTIC,
                            kinds(
                                    BooleanExpressionKind.ACTION_THEN,
                                    BooleanExpressionKind.TEMPORAL_EXISTS,
                                    BooleanExpressionKind.TEMPORAL_FORALL)),
                    Map.entry(
                            ExpressionCategory.CORE,
                            kinds(
                                    GeneralExpressionKind.TERMINAL,
                                    GeneralExpressionKind.NAME,
                                    BooleanExpressionKind.BOOLEAN_LITERAL,
                                    IntegerExpressionKind.INTEGER_LITERAL,
                                    OtherExpressionKind.STRING_LITERAL)),
                    Map.entry(
                            ExpressionCategory.CONTROL,
                            kinds(
                                    GeneralExpressionKind.IF_THEN_ELSE,
                                    GeneralExpressionKind.CASE)),
                    Map.entry(ExpressionCategory.LABEL, kinds(GeneralExpressionKind.LABEL)),
                    Map.entry(
                            ExpressionCategory.OPERATOR,
                            kinds(
                                    GeneralExpressionKind.OPERATOR_APPLICATION,
                                    GeneralExpressionKind.LET,
                                    OtherExpressionKind.LAMBDA)),
                    Map.entry(
                            ExpressionCategory.QUANTIFIER,
                            kinds(
                                    GeneralExpressionKind.BOUNDED_CHOOSE,
                                    BooleanExpressionKind.FORALL_BOUNDED,
                                    BooleanExpressionKind.EXISTS_BOUNDED)),
                    Map.entry(
                            ExpressionCategory.BOOL_LOGIC,
                            kinds(
                                    BooleanExpressionKind.EQUAL,
                                    BooleanExpressionKind.NOT_EQUAL,
                                    BooleanExpressionKind.NOT,
                                    BooleanExpressionKind.AND,
                                    BooleanExpressionKind.OR,
                                    BooleanExpressionKind.IMPLIES,
                                    BooleanExpressionKind.EQUIVALENT)),
                    Map.entry(
                            ExpressionCategory.ARITHMETIC,
                            kinds(
                                    BooleanExpressionKind.LESS_THAN,
                                    BooleanExpressionKind.GREATER_THAN,
                                    BooleanExpressionKind.LESS_EQUAL,
                                    BooleanExpressionKind.GREATER_EQUAL,
                                    IntegerExpressionKind.PLUS,
                                    IntegerExpressionKind.MINUS,
                                    IntegerExpressionKind.UNARY_MINUS,
                                    IntegerExpressionKind.MULTIPLY,
                                    IntegerExpressionKind.DIVIDE,
                                    IntegerExpressionKind.MODULO,
                                    IntegerExpressionKind.EXPONENT)),
                    Map.entry(
                            ExpressionCategory.SET,
                            kinds(
                                    BooleanExpressionKind.IN,
                                    BooleanExpressionKind.NOT_IN,
                                    BooleanExpressionKind.SUBSET_EQUAL,
                                    SetExpressionKind.EMPTY_SET,
                                    SetExpressionKind.ENUM_SET,
                                    SetExpressionKind.SET_INTERSECTION,
                                    SetExpressionKind.SET_UNION,
                                    SetExpressionKind.SET_DIFFERENCE,
                                    SetExpressionKind.UNION_ALL,
                                    SetExpressionKind.SET_FILTER,
                                    SetExpressionKind.SET_MAP,
                                    SetExpressionKind.POWER_SET,
                                    SetExpressionKind.INTERVAL)),
                    Map.entry(
                            ExpressionCategory.FINITE_SET,
                            kinds(
                                    BooleanExpressionKind.IS_FINITE_SET,
                                    IntegerExpressionKind.CARDINALITY)),
                    Map.entry(
                            ExpressionCategory.UNIVERSE,
                            kinds(
                                    SetExpressionKind.BOOLEAN_SET,
                                    SetExpressionKind.STRING_SET,
                                    SetExpressionKind.INTEGER_SET,
                                    SetExpressionKind.NATURAL_SET)),
                    Map.entry(
                            ExpressionCategory.SEQUENCE,
                            kinds(
                                    GeneralExpressionKind.HEAD,
                                    IntegerExpressionKind.LENGTH,
                                    SetExpressionKind.SEQUENCE_SET,
                                    SequenceExpressionKind.EMPTY_SEQUENCE,
                                    SequenceExpressionKind.SEQUENCE_LITERAL,
                                    SequenceExpressionKind.APPEND,
                                    SequenceExpressionKind.CONCATENATE,
                                    SequenceExpressionKind.TAIL,
                                    SequenceExpressionKind.SUBSEQUENCE)),
                    Map.entry(
                            ExpressionCategory.FUNCTION,
                            kinds(
                                    GeneralExpressionKind.FUNCTION_APPLICATION,
                                    SetExpressionKind.FUNCTION_SET,
                                    SetExpressionKind.DOMAIN,
                                    OtherExpressionKind.FUNCTION_DEFINITION,
                                    OtherExpressionKind.EXCEPT,
                                    OtherExpressionKind.EXCEPT_MANY)),
                    Map.entry(
                            ExpressionCategory.FOLD,
                            kinds(
                                    GeneralExpressionKind.FOLD_SET,
                                    GeneralExpressionKind.FOLD_SEQUENCE)),
                    Map.entry(
                            ExpressionCategory.TUPLE,
                            kinds(
                                    SetExpressionKind.CARTESIAN_PRODUCT,
                                    OtherExpressionKind.TUPLE_LITERAL)),
                    Map.entry(
                            ExpressionCategory.RECORD,
                            kinds(
                                    SetExpressionKind.RECORD_SET,
                                    OtherExpressionKind.RECORD_LITERAL)),
                    Map.entry(
                            ExpressionCategory.VARIANT,
                            kinds(
                                    GeneralExpressionKind.VARIANT_GET_OR_ELSE,
                                    GeneralExpressionKind.VARIANT_GET_UNSAFE,
                                    SetExpressionKind.VARIANT_FILTER,
                                    OtherExpressionKind.VARIANT_TAG,
                                    OtherExpressionKind.VARIANT_LITERAL)),
                    Map.entry(
                            ExpressionCategory.MODEL,
                            kinds(
                                    OtherExpressionKind.MODEL_VALUE,
                                    OtherExpressionKind.PARSED_MODEL_VALUE)));
    private static final Map<ExpressionKind, Set<ExpressionCategory>> CATEGORY_DEPENDENCIES =
            Map.ofEntries(
                    Map.entry(
                            GeneralExpressionKind.BOUNDED_CHOOSE,
                            Set.of(ExpressionCategory.SET)),
                    Map.entry(
                            GeneralExpressionKind.FUNCTION_APPLICATION,
                            Set.of(ExpressionCategory.SET)),
                    Map.entry(
                            GeneralExpressionKind.FOLD_SET,
                            Set.of(ExpressionCategory.SET, ExpressionCategory.OPERATOR)),
                    Map.entry(
                            GeneralExpressionKind.FOLD_SEQUENCE,
                            Set.of(ExpressionCategory.SEQUENCE, ExpressionCategory.OPERATOR)),
                    Map.entry(
                            BooleanExpressionKind.FORALL_BOUNDED,
                            Set.of(ExpressionCategory.SET)),
                    Map.entry(
                            BooleanExpressionKind.EXISTS_BOUNDED,
                            Set.of(ExpressionCategory.SET)),
                    Map.entry(
                            BooleanExpressionKind.IS_FINITE_SET,
                            Set.of(ExpressionCategory.SET)),
                    Map.entry(
                            IntegerExpressionKind.CARDINALITY,
                            Set.of(ExpressionCategory.SET)),
                    Map.entry(
                            SetExpressionKind.FUNCTION_SET,
                            Set.of(ExpressionCategory.SET)),
                    Map.entry(
                            SetExpressionKind.RECORD_SET,
                            Set.of(ExpressionCategory.SET)),
                    Map.entry(
                            SetExpressionKind.SEQUENCE_SET,
                            Set.of(ExpressionCategory.SET)),
                    Map.entry(
                            SetExpressionKind.CARTESIAN_PRODUCT,
                            Set.of(ExpressionCategory.SET)),
                    Map.entry(
                            SetExpressionKind.BOOLEAN_SET,
                            Set.of(ExpressionCategory.SET)),
                    Map.entry(
                            SetExpressionKind.STRING_SET,
                            Set.of(ExpressionCategory.SET)),
                    Map.entry(
                            SetExpressionKind.INTEGER_SET,
                            Set.of(ExpressionCategory.SET)),
                    Map.entry(
                            SetExpressionKind.NATURAL_SET,
                            Set.of(ExpressionCategory.SET)),
                    Map.entry(
                            SetExpressionKind.VARIANT_FILTER,
                            Set.of(ExpressionCategory.SET)),
                    Map.entry(
                            SetExpressionKind.DOMAIN,
                            Set.of(ExpressionCategory.SET)),
                    Map.entry(
                            OtherExpressionKind.FUNCTION_DEFINITION,
                            Set.of(ExpressionCategory.SET)),
                    Map.entry(
                            OtherExpressionKind.EXCEPT,
                            Set.of(ExpressionCategory.SET)),
                    Map.entry(
                            OtherExpressionKind.EXCEPT_MANY,
                            Set.of(ExpressionCategory.SET)));

    /**
     * The catalog order is the byte encoding of a nonterminal choice, so every stored corpus input
     * decodes against this exact list. A change here reinterprets existing inputs: update this
     * golden list only together with a deliberate corpus-format change.
     */
    private static final List<ExpressionKind> CATALOG_ORDER = List.of(
                    GeneralExpressionKind.TERMINAL,
                    GeneralExpressionKind.NAME,
                    GeneralExpressionKind.IF_THEN_ELSE,
                    GeneralExpressionKind.LABEL,
                    GeneralExpressionKind.BOUNDED_CHOOSE,
                    GeneralExpressionKind.UNBOUNDED_CHOOSE,
                    GeneralExpressionKind.CASE,
                    GeneralExpressionKind.OPERATOR_APPLICATION,
                    GeneralExpressionKind.LET,
                    GeneralExpressionKind.PRIME,
                    GeneralExpressionKind.FUNCTION_APPLICATION,
                    GeneralExpressionKind.FOLD_SET,
                    GeneralExpressionKind.FOLD_SEQUENCE,
                    GeneralExpressionKind.HEAD,
                    GeneralExpressionKind.VARIANT_GET_OR_ELSE,
                    GeneralExpressionKind.VARIANT_GET_UNSAFE,
                    BooleanExpressionKind.BOOLEAN_LITERAL,
                    BooleanExpressionKind.EQUAL,
                    BooleanExpressionKind.NOT_EQUAL,
                    BooleanExpressionKind.NOT,
                    BooleanExpressionKind.AND,
                    BooleanExpressionKind.OR,
                    BooleanExpressionKind.IMPLIES,
                    BooleanExpressionKind.EQUIVALENT,
                    BooleanExpressionKind.FORALL_BOUNDED,
                    BooleanExpressionKind.EXISTS_BOUNDED,
                    BooleanExpressionKind.FORALL_UNBOUNDED,
                    BooleanExpressionKind.EXISTS_UNBOUNDED,
                    BooleanExpressionKind.LESS_THAN,
                    BooleanExpressionKind.GREATER_THAN,
                    BooleanExpressionKind.LESS_EQUAL,
                    BooleanExpressionKind.GREATER_EQUAL,
                    BooleanExpressionKind.IN,
                    BooleanExpressionKind.NOT_IN,
                    BooleanExpressionKind.SUBSET_EQUAL,
                    BooleanExpressionKind.IS_FINITE_SET,
                    BooleanExpressionKind.PRIME_EQUAL,
                    BooleanExpressionKind.STUTTER,
                    BooleanExpressionKind.NO_STUTTER,
                    BooleanExpressionKind.ENABLED,
                    BooleanExpressionKind.UNCHANGED,
                    BooleanExpressionKind.ACTION_THEN,
                    BooleanExpressionKind.ALWAYS,
                    BooleanExpressionKind.EVENTUALLY,
                    BooleanExpressionKind.LEADS_TO,
                    BooleanExpressionKind.GUARANTEES,
                    BooleanExpressionKind.WEAK_FAIR,
                    BooleanExpressionKind.STRONG_FAIR,
                    BooleanExpressionKind.TEMPORAL_EXISTS,
                    BooleanExpressionKind.TEMPORAL_FORALL,
                    IntegerExpressionKind.INTEGER_LITERAL,
                    IntegerExpressionKind.PLUS,
                    IntegerExpressionKind.MINUS,
                    IntegerExpressionKind.UNARY_MINUS,
                    IntegerExpressionKind.MULTIPLY,
                    IntegerExpressionKind.DIVIDE,
                    IntegerExpressionKind.MODULO,
                    IntegerExpressionKind.EXPONENT,
                    IntegerExpressionKind.CARDINALITY,
                    IntegerExpressionKind.LENGTH,
                    SetExpressionKind.EMPTY_SET,
                    SetExpressionKind.ENUM_SET,
                    SetExpressionKind.SET_INTERSECTION,
                    SetExpressionKind.SET_UNION,
                    SetExpressionKind.SET_DIFFERENCE,
                    SetExpressionKind.UNION_ALL,
                    SetExpressionKind.SET_FILTER,
                    SetExpressionKind.SET_MAP,
                    SetExpressionKind.FUNCTION_SET,
                    SetExpressionKind.RECORD_SET,
                    SetExpressionKind.SEQUENCE_SET,
                    SetExpressionKind.CARTESIAN_PRODUCT,
                    SetExpressionKind.POWER_SET,
                    SetExpressionKind.INTERVAL,
                    SetExpressionKind.BOOLEAN_SET,
                    SetExpressionKind.STRING_SET,
                    SetExpressionKind.INTEGER_SET,
                    SetExpressionKind.NATURAL_SET,
                    SetExpressionKind.VARIANT_FILTER,
                    SetExpressionKind.DOMAIN,
                    SequenceExpressionKind.EMPTY_SEQUENCE,
                    SequenceExpressionKind.SEQUENCE_LITERAL,
                    SequenceExpressionKind.APPEND,
                    SequenceExpressionKind.CONCATENATE,
                    SequenceExpressionKind.TAIL,
                    SequenceExpressionKind.SUBSEQUENCE,
                    OtherExpressionKind.STRING_LITERAL,
                    OtherExpressionKind.VARIANT_TAG,
                    OtherExpressionKind.MODEL_VALUE,
                    OtherExpressionKind.PARSED_MODEL_VALUE,
                    OtherExpressionKind.FUNCTION_DEFINITION,
                    OtherExpressionKind.EXCEPT,
                    OtherExpressionKind.EXCEPT_MANY,
                    OtherExpressionKind.TUPLE_LITERAL,
                    OtherExpressionKind.RECORD_LITERAL,
                    OtherExpressionKind.VARIANT_LITERAL,
                    OtherExpressionKind.LAMBDA);

    @Test
    void catalogOrderIsTheStoredByteEncoding() {
        assertEquals(CATALOG_ORDER, ExpressionKindCatalog.all());
    }

    @Test
    void catalogContainsEveryFamilyConstantExactlyOnce() {
        var expectedSize = GeneralExpressionKind.values().length
                + BooleanExpressionKind.values().length
                + IntegerExpressionKind.values().length
                + SetExpressionKind.values().length
                + SequenceExpressionKind.values().length
                + OtherExpressionKind.values().length;

        assertEquals(expectedSize, ExpressionKindCatalog.all().size());
        assertEquals(expectedSize, new HashSet<>(ExpressionKindCatalog.all()).size());
        assertTrue(expectedSize <= ExpressionKindCatalog.MAXIMUM_SELECTION_SLOTS);
    }

    @Test
    void everyExpressionKindHasExactlyOneDocumentedCategory() {
        var assigned = new HashSet<ExpressionKind>();
        for (var entry : KINDS_BY_CATEGORY.entrySet()) {
            for (var kind : entry.getValue()) {
                assertTrue(assigned.add(kind), () -> kind + " was assigned twice");
                assertEquals(entry.getKey(), kind.category(), kind.toString());
            }
        }

        assertEquals(new HashSet<>(ExpressionKindCatalog.all()), assigned);
    }

    @Test
    void requirementsIncludePrimaryCategoriesAndDriveAvailability() {
        var ignoredByDefault = IrGenerationConfig.defaults().ignoredCategories();
        for (var kind : ExpressionKindCatalog.all()) {
            var expectedRequirements = new HashSet<ExpressionCategory>();
            expectedRequirements.add(kind.category());
            expectedRequirements.addAll(
                    CATEGORY_DEPENDENCIES.getOrDefault(kind, Set.of()));
            assertEquals(expectedRequirements, kind.requiredCategories(), kind.toString());
            assertEquals(
                    !Collections.disjoint(kind.requiredCategories(), ignoredByDefault),
                    kind.isUnavailableWith(ignoredByDefault),
                    kind.toString());
        }

        var defaults = expressionFactory(IrGenerationConfig.defaults());
        assertFalse(defaults.isApplicable(
                GeneralExpressionKind.PRIME, PrimitiveType.BOOL));
        assertTrue(defaults.isApplicable(
                GeneralExpressionKind.BOUNDED_CHOOSE, PrimitiveType.BOOL));
        assertTrue(defaults.isApplicable(
                BooleanExpressionKind.FORALL_BOUNDED, PrimitiveType.BOOL));

        var allEnabled = expressionFactory(configIgnoring(Set.of()));
        assertTrue(allEnabled.isApplicable(
                GeneralExpressionKind.PRIME, PrimitiveType.BOOL));
        assertTrue(allEnabled.isApplicable(
                BooleanExpressionKind.TEMPORAL_EXISTS, PrimitiveType.BOOL));

        var noSets = expressionFactory(configIgnoring(Set.of(ExpressionCategory.SET)));
        assertFalse(noSets.isApplicable(
                GeneralExpressionKind.BOUNDED_CHOOSE, PrimitiveType.BOOL));
        assertFalse(noSets.isApplicable(
                BooleanExpressionKind.FORALL_BOUNDED, PrimitiveType.BOOL));
        assertFalse(noSets.isApplicable(
                BooleanExpressionKind.IS_FINITE_SET, PrimitiveType.BOOL));
        assertFalse(noSets.isApplicable(
                GeneralExpressionKind.FUNCTION_APPLICATION, PrimitiveType.BOOL));
        assertFalse(noSets.isApplicable(
                GeneralExpressionKind.FOLD_SET, PrimitiveType.BOOL));
        assertTrue(noSets.isApplicable(
                GeneralExpressionKind.FOLD_SEQUENCE, PrimitiveType.BOOL));

        var noOperators = expressionFactory(configIgnoring(Set.of(ExpressionCategory.OPERATOR)));
        assertFalse(noOperators.isApplicable(
                GeneralExpressionKind.LET, PrimitiveType.BOOL));
        assertFalse(noOperators.isApplicable(
                GeneralExpressionKind.FOLD_SET, PrimitiveType.BOOL));
        assertFalse(noOperators.isApplicable(
                GeneralExpressionKind.FOLD_SEQUENCE, PrimitiveType.BOOL));
    }

    @Test
    void everyRepresentativeTypeUsesTheFixedSelectionWidth() {
        for (var type : representativeTypes()) {
            var draw = new Draw(new byte[] {0, 0, 99});
            var context = new GenerationContext(IrGenerationConfig.defaults());
            var typeFactory = new IrTypeGenFactory(context);
            var expressionFactory = new IrExprGenFactory(context, typeFactory);

            draw.draw(expressionFactory.mkGen(type, 1));

            assertEquals(1, draw.remaining(), () -> "unexpected consumption for " + type);
        }
    }

    @Test
    void weightedFormsOccupyTheirConfiguredNumberOfSlots() {
        var weighted = weightedConfig(Map.of(
                GeneralExpressionKind.NAME, 8,
                BooleanExpressionKind.EQUAL, 3,
                IntegerExpressionKind.PLUS, 6,
                SetExpressionKind.ENUM_SET, 4));
        var context = new GenerationContext(weighted);
        var typeFactory = new IrTypeGenFactory(context);
        var expressionFactory = new IrExprGenFactory(context, typeFactory);
        var setOfBool = new SetType(PrimitiveType.BOOL);

        // A form nobody weights keeps one slot; a weighted one takes its configured share.
        assertEquals(
                1, expressionFactory.selectionWeight(SetExpressionKind.EMPTY_SET, setOfBool));
        assertEquals(
                4, expressionFactory.selectionWeight(SetExpressionKind.ENUM_SET, setOfBool));
        assertEquals(
                3,
                expressionFactory.selectionWeight(
                        BooleanExpressionKind.EQUAL, PrimitiveType.BOOL));
        assertEquals(
                6,
                expressionFactory.selectionWeight(
                        IntegerExpressionKind.PLUS, PrimitiveType.INT));

        // NAME is worth nothing without a binding, and its full weight with one.
        assertEquals(
                0, expressionFactory.selectionWeight(GeneralExpressionKind.NAME, setOfBool));
        new Draw(new byte[0]).draw(context.withBinding(
                new ScopedName("bound", setOfBool),
                ignored -> {
                    assertEquals(
                            8,
                            expressionFactory.selectionWeight(
                                    GeneralExpressionKind.NAME, setOfBool));
                    return null;
                }));
    }

    /**
     * A weighted terminal only pays off where it can name something. Weighting its
     * closed-constant case would shrink every expression instead of biasing towards the
     * surrounding lexical context.
     */
    @Test
    void terminalTakesItsWeightOnlyWhileABindingIsVisible() {
        var weighted = weightedConfig(Map.of(GeneralExpressionKind.TERMINAL, 4));
        var context = new GenerationContext(weighted);
        var typeFactory = new IrTypeGenFactory(context);
        var expressionFactory = new IrExprGenFactory(context, typeFactory);

        assertEquals(
                1,
                expressionFactory.selectionWeight(
                        GeneralExpressionKind.TERMINAL, PrimitiveType.BOOL));

        new Draw(new byte[0]).draw(context.withBinding(
                new ScopedName("bound", PrimitiveType.BOOL),
                ignored -> {
                    assertEquals(
                            4,
                            expressionFactory.selectionWeight(
                                    GeneralExpressionKind.TERMINAL, PrimitiveType.BOOL));
                    return null;
                }));
    }

    @Test
    void weightsMayNotExceedTheAddressableSlots() {
        var tooMany = weightedConfig(
                Map.of(GeneralExpressionKind.NAME, IrGenerationConfig.MAXIMUM_FORM_WEIGHT));
        assertEquals(
                IrGenerationConfig.MAXIMUM_FORM_WEIGHT - 1, tooMany.additionalSelectionSlots());

        // The catalog plus the added slots is the worst case any single request can present.
        assertTrue(
                ExpressionKindCatalog.all().size() + tooMany.additionalSelectionSlots()
                        <= ExpressionKindCatalog.MAXIMUM_SELECTION_SLOTS);
        assertDoesNotThrow(() -> ExpressionKindCatalog.requireAddressableSlots(tooMany));
    }

    /**
     * The selector skips the draw when only one form applies, because choosing nothing must not
     * shift later bytes. That branch is defensive: an enabled type always offers {@code TERMINAL}
     * plus at least one enabled constructor, which is what makes the selection width uniform in
     * practice. This pins that property, so a change making some type degenerate is visible here
     * rather than as an unexplained shift in how corpus inputs decode.
     */
    @Test
    void everyRepresentativeTypeOffersMoreThanOneForm() {
        var expressionFactory = expressionFactory(IrGenerationConfig.defaults());

        for (var type : representativeTypes()) {
            var applicable = ExpressionKindCatalog.all().stream()
                    .filter(kind -> expressionFactory.isApplicable(kind, type))
                    .toList();

            assertTrue(
                    applicable.size() > 1,
                    () -> "selection is degenerate for " + type + ": " + applicable);
        }
    }

    @Test
    void constructingExpressionGeneratorsDoesNotSpendNodeBudgetOrBytes() {
        var draw = new Draw(new byte[] {0, 0, 99});
        var context = new GenerationContext(IrGenerationConfig.defaults());
        var typeFactory = new IrTypeGenFactory(context);
        var expressionFactory = new IrExprGenFactory(context, typeFactory);

        for (var index = 0; index < 100; index++) {
            expressionFactory.mkGen(PrimitiveType.BOOL, 1);
        }

        assertEquals(3, draw.remaining());
        draw.draw(expressionFactory.mkGen(PrimitiveType.BOOL, 1));
        assertEquals(1, draw.remaining());
    }

    /** One type per {@link IrType} variant, covering every family of the catalog. */
    private List<IrType> representativeTypes() {
        return List.of(
                PrimitiveType.BOOL,
                PrimitiveType.INT,
                PrimitiveType.STRING,
                new ConstantType("MODEL"),
                new SetType(PrimitiveType.BOOL),
                new SequenceType(PrimitiveType.BOOL),
                new FunctionType(PrimitiveType.BOOL, PrimitiveType.INT),
                new TupleType(List.of(PrimitiveType.BOOL, PrimitiveType.INT)),
                new RecordType(List.of(new Field("field", PrimitiveType.BOOL))),
                new VariantType(List.of(new Field("Tag", PrimitiveType.INT))),
                new OperatorType(List.of(PrimitiveType.BOOL), PrimitiveType.INT));
    }

    private IrGenerationConfig weightedConfig(Map<ExpressionKind, Integer> weights) {
        return IrGenerationConfig.defaults().withFormWeights(weights);
    }

    private IrExprGenFactory expressionFactory(IrGenerationConfig config) {
        var context = new GenerationContext(config);
        return new IrExprGenFactory(context, new IrTypeGenFactory(context));
    }

    private IrGenerationConfig configIgnoring(Set<ExpressionCategory> ignoredCategories) {
        return IrGenerationConfig.defaults().withIgnoredCategories(ignoredCategories);
    }

    private static Set<ExpressionKind> kinds(ExpressionKind... kinds) {
        return Set.of(kinds);
    }
}
