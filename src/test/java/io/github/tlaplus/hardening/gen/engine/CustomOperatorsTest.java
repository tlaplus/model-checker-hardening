package io.github.tlaplus.hardening.gen.engine;

import at.forsyte.apalache.tla.lir.*;
import io.github.tlaplus.hardening.config.*;
import io.github.tlaplus.hardening.gen.*;
import io.github.tlaplus.hardening.gen.library.*;
import io.github.tlaplus.hardening.workflow.library.LibraryPreparation;
import io.github.tlaplus.hardening.workflow.spec.SpecDecoders;
import io.github.tlaplus.hardening.workflow.spec.SpecText;
import java.nio.file.Path;
import java.util.*;
import org.apalache_mc.tla.jir.NamedType;
import org.apalache_mc.tla.jir.TlaExpressions;
import org.apalache_mc.tla.jir.TlaModules;
import org.apalache_mc.tla.jir.TlaTypes;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CustomOperatorsTest {
    private static IrGenerationConfig config;
    private static final List<String> SELECTED = List.of("Wrapped", "Singleton", "Contains", "Empty",
            "ReadValue", "WithValue", "First", "Local", "Init");

    @BeforeAll
    static void prepare() throws Exception {
        var defaults = FuzzTlaConfig.defaults();
        config = LibraryPreparation.prepare(new FuzzTlaConfig(defaults.generatedKind(), defaults.generator(),
                defaults.workflow(), defaults.pbt(), new OperatorLibraryConfig(
                        List.of(Path.of("src/test/resources/custom").toAbsolutePath()),
                        List.of(new OperatorLibraryConfig.Module("PolyOps", SELECTED))))).generator();
    }

    private static CustomExpressionKind kind(String name) {
        return new CustomExpressionKind(new OperatorId("PolyOps", name));
    }

    private static TlaEx call(String name, IrType result, byte... input) {
        var context = new GenerationContext(config);
        var types = new IrTypeGenFactory(context);
        return new IrExprGenFactory(context, types).mkGen(kind(name), result, 1).generate(input);
    }

    @Test
    void catalogAppendsExactlyOneKindPerSelectionInConfigurationOrder() {
        var all = ExpressionKindCatalog.all(config);
        assertEquals(ExpressionKind.all(), all.subList(0, ExpressionKind.all().size()));
        assertEquals(SELECTED.stream().map(CustomOperatorsTest::kind).toList(),
                all.subList(ExpressionKind.all().size(), all.size()));
        assertNull(config.library().get(new OperatorId("PolyOps", "Identity")));
    }

    @Test
    void resultTypeConstrainsEveryOccurrenceWithoutSharingInstantiations() {
        for (var type : List.of(PrimitiveType.INT, PrimitiveType.BOOL,
                new SetType(PrimitiveType.STRING))) {
            var expression = (OperEx) call("Wrapped", type);
            assertEquals(type.toTlaType(), TlaTypes.typeOf(expression));
            assertEquals(type.toTlaType(), TlaTypes.typeOf(TlaExpressions.arguments(expression).get(1)));
        }
        var singleton = call("Singleton", new SetType(PrimitiveType.INT));
        assertEquals(new SetType(PrimitiveType.INT).toTlaType(), TlaTypes.typeOf(singleton));
        assertFalse(kind("Singleton").isConfiguredApplicable(config, PrimitiveType.INT));
    }

    @Test
    void argumentOnlyVariablesAreDrawnOnceAndInStructuralOrder() {
        var contains = (OperEx) call("Contains", PrimitiveType.BOOL, (byte) 0, (byte) 1);
        var arguments = TlaExpressions.arguments(contains);
        assertEquals(new SetType(PrimitiveType.INT).toTlaType(), TlaTypes.typeOf(arguments.get(1)));
        assertEquals(PrimitiveType.INT.toTlaType(), TlaTypes.typeOf(arguments.get(2)));
        var context = new GenerationContext(config);
        var types = new IrTypeGenFactory(context);
        var plan = kind("First").plan(config, PrimitiveType.INT).orElseThrow();
        var draw = new Draw(new byte[] {0, 2, 91});
        var signature = (OperT1) draw.draw(plan.generator(context, types));
        assertEquals(List.of(PrimitiveType.INT.toTlaType(), PrimitiveType.STRING.toTlaType()),
                TlaTypes.operatorArguments(signature));
        assertEquals(1, draw.remaining());
    }

    @Test
    void nullaryOperatorsAndRowsInstantiateToConcreteTypes() {
        var empty = (OperEx) call("Empty", new SetType(PrimitiveType.STRING));
        assertEquals(1, TlaExpressions.arguments(empty).size());
        var recordCall = (OperEx) call("ReadValue", PrimitiveType.INT);
        var record = (RecRowT1) TlaTypes.typeOf(TlaExpressions.arguments(recordCall).get(1));
        assertTrue(TlaTypes.rowTail(record.row()).isEmpty());
        assertEquals(PrimitiveType.INT.toTlaType(), TlaTypes.rowFields(record.row()).get("value"));
    }

    @Test
    void rowCompletionCanAddFieldsWithinItsBudget() {
        var context = new GenerationContext(config);
        var types = new IrTypeGenFactory(context);
        var signature = (OperT1) kind("ReadValue").plan(config, PrimitiveType.INT).orElseThrow()
                .generator(context, types).generate(new byte[] {1, 0, 2, 0});
        var row = ((RecRowT1) TlaTypes.operatorArguments(signature).getFirst()).row();
        assertTrue(TlaTypes.rowTail(row).isEmpty());
        assertEquals(2, TlaTypes.rowFields(row).size());
        assertEquals(PrimitiveType.INT.toTlaType(), TlaTypes.rowFields(row).get("value"));
    }

    @Test
    void variantRowsAndSharedRowConstraintsAreInstantiatedConsistently() {
        var payload = TlaTypes.typeVariable(15);
        var rowVariable = TlaTypes.typeVariable(29);
        var variant = TlaTypes.variant(rowVariable, new NamedType("Some", payload));
        var signature = TlaTypes.operator(PrimitiveType.BOOL.toTlaType(), variant);
        var context = new GenerationContext(config);
        var types = new IrTypeGenFactory(context);
        var plan = TypeInstantiation.plan(signature, config, 3).orElseThrow();
        var concrete = (OperT1) plan.generator(context, types).generate(new byte[] {0, 1, 1, 0, 2, 0});
        var row = ((VariantT1) TlaTypes.operatorArguments(concrete).getFirst()).row();
        assertTrue(TlaTypes.usedVariables(concrete).isEmpty());
        assertEquals(PrimitiveType.INT.toTlaType(), TlaTypes.rowFields(row).get("Some"));
        assertEquals(2, TlaTypes.rowFields(row).size());

        var left = TlaTypes.rowRecord(rowVariable,
                new NamedType("field0", PrimitiveType.INT.toTlaType()));
        var right = TlaTypes.rowRecord(rowVariable,
                new NamedType("field1", PrimitiveType.BOOL.toTlaType()));
        var both = TlaTypes.operator(PrimitiveType.BOOL.toTlaType(), left, right);
        context = new GenerationContext(config);
        types = new IrTypeGenFactory(context);
        var shared = (OperT1) TypeInstantiation.plan(both, config, 3)
                .orElseThrow().generator(context, types).generate(new byte[] {1, 0, 0, 0});
        for (var argument : TlaTypes.operatorArguments(shared)) {
            var fields = TlaTypes.rowFields(((RecRowT1) argument).row());
            assertEquals(2, fields.size());
            assertTrue(fields.containsKey("field2"), fields.toString());
        }
    }

    @Test
    void typeVariableNumberingIsNotDrawOrderAndBoundsApplyToEveryOccurrence() {
        var variable = TlaTypes.typeVariable(300);
        var signature = TlaTypes.operator(
                PrimitiveType.BOOL.toTlaType(), variable, TlaTypes.set(variable));
        var canonical = TypeInstantiation.canonical(signature);
        var canonicalVariable = TlaTypes.typeVariable(0);
        assertEquals(TlaTypes.operator(PrimitiveType.BOOL.toTlaType(),
                canonicalVariable, TlaTypes.set(canonicalVariable)), canonical);
        var limits = new ExpressionLimits(1, 4, 8, 2, 4, 4);
        var bounded = config.withExpressionLimits(limits);
        var context = new GenerationContext(bounded);
        var types = new IrTypeGenFactory(context);
        var plan = TypeInstantiation.plan(canonical, bounded, 1).orElseThrow();
        assertEquals(0, plan.variables().getFirst().depth());
        var concrete = (OperT1) plan.generator(context, types).generate(new byte[] {(byte) 255});
        for (var argument : TlaTypes.operatorArguments(concrete)) assertTrue(depth(argument) <= 1);
        assertTrue(TypeInstantiation.plan(canonical, bounded, 0).isEmpty());
        assertTrue(TypeInstantiation.plan(
                TlaTypes.set(TlaTypes.set(TlaTypes.typeVariable(0))), bounded, 1).isEmpty());
    }

    @Test
    void customChoiceStillCostsTwoBytesAndExhaustionDoesNotCallTheLibrary() {
        var bool = PrimitiveType.BOOL;
        var factoryContext = new GenerationContext(config);
        var factory = new IrExprGenFactory(factoryContext, new IrTypeGenFactory(factoryContext));
        int slotsBefore = 0;
        for (var form : ExpressionKind.all()) slotsBefore += factory.selectionWeight(form, bool);
        var draw = new Draw(new byte[] {(byte) (slotsBefore >>> 8), (byte) slotsBefore, 99});
        var result = draw.draw(factory.mkGen(bool, 1));
        assertInstanceOf(OperEx.class, result);
        assertEquals(config.library().get(kind("Wrapped").id()).name(),
                ((NameEx) TlaExpressions.arguments((OperEx) result).getFirst()).name());
        assertEquals(1, draw.remaining());
        var exhausted = factory.mkGen(bool, 1).generate(new byte[0]);
        assertInstanceOf(ValEx.class, exhausted);
    }

    @Test
    void filteringAndFeasibilityAreByteFree() {
        var ignored = config.ignoring(ExpressionCategory.SET);
        assertFalse(kind("Contains").isConfiguredApplicable(ignored, PrimitiveType.BOOL));
        assertFalse(kind("Wrapped").isConfiguredApplicable(
                config.ignoring(ExpressionCategory.OPERATOR), PrimitiveType.BOOL));
        var context = new GenerationContext(config);
        var draw = new Draw(new byte[] {11, 22});
        for (int i = 0; i < 10; i++) {
            assertTrue(kind("Wrapped").plan(config, PrimitiveType.BOOL).isPresent());
        }
        assertEquals(2, draw.remaining());
        assertEquals(PrimitiveType.BOOL.toTlaType(),
                TlaTypes.typeOf(call("Wrapped", PrimitiveType.BOOL)));
    }

    @Test
    void fixedLibraryResultShapesAreReachableByTheTypeDecoder() {
        var context = new GenerationContext(config);
        var type = new IrTypeGenFactory(context).anyType().generate(new byte[] {0, 13, 0, 1});
        assertEquals(new RecordType(List.of(new Field("value", PrimitiveType.INT))), type);
    }

    @Test
    void customWeightsAreIncludedInTheFixedWidthSlotBound() {
        var builder = new org.apalache_mc.tla.jir.TlaTypedScopeUncheckedBuilder();
        var declarations = new ArrayList<TlaDecl>();
        var selected = new ArrayList<OperatorId>();
        var weights = new LinkedHashMap<ExpressionKind, Integer>();
        for (int i = 0; i < 1050; i++) {
            var id = new OperatorId("Many", "Op" + i);
            selected.add(id);
            weights.put(new CustomExpressionKind(id), 64);
            declarations.add(builder.decl(id.operator(), builder.bool(true)));
        }
        var module = TlaModules.create("Many", declarations);
        var library = OperatorLibrary.fromModules(Map.of("Many", module), selected);
        var oversized = config.withLibrary(library).withFormWeights(weights);
        assertThrows(IllegalArgumentException.class, () -> IrGenerators.expressions(oversized));
    }

    @Test
    void outputsLinkOnlyUsedDefinitionsAndDoNotScoreTheirBodies() {
        var expression = call("Wrapped", PrimitiveType.INT);
        var artifact = io.github.tlaplus.hardening.workflow.spec.SpecArtifact.fromExpression(
                expression, config.library());
        assertEquals(List.of(expression), artifact.generated());
        assertInstanceOf(LetInEx.class, artifact.standaloneExpression().orElseThrow());
        // One helper, one selected definition, one variable, and four skeleton operators.
        assertEquals(7, TlaModules.declarations(artifact.module()).size());
        var source = SpecText.render(artifact.module());
        assertFalse(source.contains("EXTENDS PolyOps"));
        assertEquals(source, SpecText.render(io.github.tlaplus.hardening.workflow.spec.SpecArtifact
                .fromExpression(expression, config.library()).module()));
    }

    @Test
    void freshCopiesProtectTheLibraryFromMutation() {
        var expression = call("Local", PrimitiveType.BOOL);
        var first = config.library().declarationsFor(List.of(expression));
        var second = config.library().declarationsFor(List.of(expression));
        assertNotEquals(first.getFirst().ID(), second.getFirst().ID());
        assertNotEquals(first.getFirst().body().ID(), second.getFirst().body().ID());
        assertEquals(second.getFirst().body().toString(),
                config.library().declarationsFor(List.of(expression)).getFirst().body().toString());
    }

    @Test
    void customWeightedGenerationIsDeterministicAndExercisesBothDecoders() {
        var weighted = config.withFormWeights(Map.of(kind("Wrapped"), 64, kind("WithValue"), 64));
        var decoders = SpecDecoders.of(weighted);
        var random = new Random(79);
        int used = 0;
        for (var inputKind : InputKind.values()) {
            for (int i = 0; i < 60; i++) {
                byte[] bytes = new byte[256];
                random.nextBytes(bytes);
                try {
                    var artifact = decoders.decoder(inputKind).generate(bytes);
                    var source = SpecText.render(artifact.module());
                    assertEquals(source, SpecText.render(decoders.decoder(inputKind).generate(bytes).module()));
                    if (source.contains("Custom")) used++;
                } catch (InputRejectedException expected) { }
            }
        }
        assertTrue(used > 20, "custom calls must be reachable, found " + used);
    }

    /** Nesting depth of an instantiated type; row wrappers do not add a level. */
    private static int depth(TlaType1 type) {
        var children = LibraryTypes.children(type);
        boolean row = type instanceof RowT1;
        if (children.isEmpty()) return row ? -1 : 0;
        return children.stream().mapToInt(CustomOperatorsTest::depth).max().orElse(0)
                + (row ? 0 : 1);
    }
}
