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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static io.github.tlaplus.hardening.common.ScalaCollections.*;
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
                        List.of(new OperatorLibraryConfig.Module("PolyOps", SELECTED)))));
    }

    private static CustomExpressionKind kind(String name) {
        return new CustomExpressionKind(new OperatorId("PolyOps", name));
    }

    private static TlaEx call(String name, IrType result, byte... input) {
        var context = new GenerationContext(config);
        var types = new IrTypeGenFactory(context);
        var expressions = new IrExprGenFactory(context, types);
        return new CustomExprGenFactory(context, types, expressions).mkGen(kind(name), result, 1).generate(input);
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
            assertEquals(type.toTlaType(), LibraryTypes.type(expression.typeTag()));
            assertEquals(type.toTlaType(), LibraryTypes.type(expression.args().apply(1).typeTag()));
        }
        var singleton = call("Singleton", new SetType(PrimitiveType.INT));
        assertEquals(new SetType(PrimitiveType.INT).toTlaType(), LibraryTypes.type(singleton.typeTag()));
        assertFalse(kind("Singleton").isConfiguredApplicable(config, PrimitiveType.INT));
    }

    @Test
    void argumentOnlyVariablesAreDrawnOnceAndInStructuralOrder() {
        var contains = (OperEx) call("Contains", PrimitiveType.BOOL, (byte) 0, (byte) 1);
        assertEquals(new SetType(PrimitiveType.INT).toTlaType(), LibraryTypes.type(contains.args().apply(1).typeTag()));
        assertEquals(PrimitiveType.INT.toTlaType(), LibraryTypes.type(contains.args().apply(2).typeTag()));
        var context = new GenerationContext(config);
        var types = new IrTypeGenFactory(context);
        var plan = kind("First").plan(config, PrimitiveType.INT).orElseThrow();
        var draw = new Draw(new byte[] {0, 2, 91});
        var signature = (OperT1) draw.draw(plan.generator(context, types));
        assertEquals(List.of(PrimitiveType.INT.toTlaType(), PrimitiveType.STRING.toTlaType()), list(signature.args()));
        assertEquals(1, draw.remaining());
    }

    @Test
    void nullaryOperatorsAndRowsInstantiateToConcreteTypes() {
        var empty = (OperEx) call("Empty", new SetType(PrimitiveType.STRING));
        assertEquals(1, empty.args().size());
        var recordCall = (OperEx) call("ReadValue", PrimitiveType.INT);
        var record = (RecRowT1) LibraryTypes.type(recordCall.args().apply(1).typeTag());
        assertTrue(record.row().other().isEmpty());
        assertEquals(PrimitiveType.INT.toTlaType(), record.row().fieldTypes().apply("value"));
    }

    @Test
    void rowCompletionCanAddFieldsWithinItsBudget() {
        var context = new GenerationContext(config);
        var types = new IrTypeGenFactory(context);
        var signature = (OperT1) kind("ReadValue").plan(config, PrimitiveType.INT).orElseThrow()
                .generator(context, types).generate(new byte[] {1, 0, 2, 0});
        var row = ((RecRowT1) signature.args().head()).row();
        assertTrue(row.other().isEmpty());
        assertEquals(2, row.fieldTypes().size());
        assertEquals(PrimitiveType.INT.toTlaType(), row.fieldTypes().apply("value"));
    }

    @Test
    void variantRowsAndSharedRowConstraintsAreInstantiatedConsistently() {
        var payload = new VarT1(15);
        var rowVariable = new VarT1(29);
        var variant = new VariantT1(RowT1$.MODULE$.apply(rowVariable,
                seq(List.of(new scala.Tuple2<String, TlaType1>("Some", payload)))));
        var signature = new OperT1(seq(List.of(variant)), PrimitiveType.BOOL.toTlaType());
        var context = new GenerationContext(config);
        var types = new IrTypeGenFactory(context);
        var plan = TypeInstantiation.plan(signature, config.expressions(), config.ignoredCategories(), 3).orElseThrow();
        var concrete = (OperT1) plan.generator(context, types).generate(new byte[] {0, 1, 1, 0, 2, 0});
        var row = ((VariantT1) concrete.args().head()).row();
        assertTrue(concrete.isMono());
        assertEquals(PrimitiveType.INT.toTlaType(), row.fieldTypes().apply("Some"));
        assertEquals(2, row.fieldTypes().size());

        var left = new RecRowT1(RowT1$.MODULE$.apply(rowVariable,
                seq(List.of(new scala.Tuple2<String, TlaType1>("field0", PrimitiveType.INT.toTlaType())))));
        var right = new RecRowT1(RowT1$.MODULE$.apply(rowVariable,
                seq(List.of(new scala.Tuple2<String, TlaType1>("field1", PrimitiveType.BOOL.toTlaType())))));
        var both = new OperT1(seq(List.of(left, right)), PrimitiveType.BOOL.toTlaType());
        context = new GenerationContext(config);
        types = new IrTypeGenFactory(context);
        var shared = (OperT1) TypeInstantiation.plan(both, config.expressions(), config.ignoredCategories(), 3)
                .orElseThrow().generator(context, types).generate(new byte[] {1, 0, 0, 0});
        for (var argument : list(shared.args())) {
            var fields = ((RecRowT1) argument).row().fieldTypes();
            assertEquals(2, fields.size());
            assertTrue(fields.contains("field2"), fields.toString());
        }
    }

    @Test
    void typeVariableNumberingIsNotDrawOrderAndBoundsApplyToEveryOccurrence() {
        var variable = new VarT1(300);
        var signature = new OperT1(seq(List.of(variable, new SetT1(variable))), PrimitiveType.BOOL.toTlaType());
        var canonical = TypeInstantiation.canonical(signature);
        assertEquals(new OperT1(seq(List.of(new VarT1(0), new SetT1(new VarT1(0)))), PrimitiveType.BOOL.toTlaType()), canonical);
        var limits = new ExpressionLimits(1, 4, 8, 2, 4, 4);
        var bounded = config.withExpressionLimits(limits);
        var context = new GenerationContext(bounded);
        var types = new IrTypeGenFactory(context);
        var plan = TypeInstantiation.plan(canonical, limits, config.ignoredCategories(), 1).orElseThrow();
        assertEquals(0, plan.variables().getFirst().depth());
        var concrete = (OperT1) plan.generator(context, types).generate(new byte[] {(byte) 255});
        for (var argument : list(concrete.args())) assertTrue(LibraryTypes.depth(argument) <= 1);
        assertTrue(TypeInstantiation.plan(canonical, limits, config.ignoredCategories(), 0).isEmpty());
        assertTrue(TypeInstantiation.plan(new SetT1(new SetT1(new VarT1(0))),
                limits, config.ignoredCategories(), 1).isEmpty());
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
                ((NameEx) ((OperEx) result).args().head()).name());
        assertEquals(1, draw.remaining());
        var exhausted = factory.mkGen(bool, 1).generate(new byte[0]);
        assertInstanceOf(ValEx.class, exhausted);
    }

    @Test
    void filteringAndFeasibilityAreByteFree() {
        var ignored = config.ignoring(ExpressionCategory.SET);
        assertFalse(kind("Contains").isConfiguredApplicable(ignored, PrimitiveType.BOOL));
        assertFalse(kind("Wrapped").isConfiguredApplicable(config.ignoring(ExpressionCategory.OPERATOR), PrimitiveType.BOOL));
        var context = new GenerationContext(config);
        var draw = new Draw(new byte[] {11, 22});
        for (int i = 0; i < 10; i++) assertTrue(kind("Wrapped").plan(config, PrimitiveType.BOOL).isPresent());
        assertEquals(2, draw.remaining());
        assertEquals(PrimitiveType.BOOL.toTlaType(), LibraryTypes.type(call("Wrapped", PrimitiveType.BOOL).typeTag()));
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
        var library = OperatorLibrary.fromModules(Map.of("Many", new TlaModule("Many", seq(declarations))), selected);
        var oversized = config.withLibrary(library).withFormWeights(weights);
        assertThrows(IllegalArgumentException.class, () -> IrGenerators.expressions(oversized));
    }

    @Test
    void outputsLinkOnlyUsedDefinitionsAndDoNotScoreTheirBodies() {
        var expression = call("Wrapped", PrimitiveType.INT);
        var artifact = io.github.tlaplus.hardening.workflow.spec.SpecArtifact.fromExpression(expression, config.library());
        assertEquals(List.of(expression), artifact.generated());
        assertInstanceOf(LetInEx.class, artifact.standaloneExpression().orElseThrow());
        // One helper, one selected definition, one variable, and four skeleton operators.
        assertEquals(7, artifact.module().declarations().size());
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
        first.getFirst().body_$eq(call("Empty", new SetType(PrimitiveType.INT)));
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
}
