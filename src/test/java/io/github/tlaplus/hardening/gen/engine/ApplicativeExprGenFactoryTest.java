package io.github.tlaplus.hardening.gen.engine;

import static io.github.tlaplus.hardening.gen.TlaIrTestSupport.containsOperator;
import static io.github.tlaplus.hardening.gen.engine.FormDecodingTestSupport.assertForm;
import static io.github.tlaplus.hardening.gen.TlaIrTestSupport.print;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import at.forsyte.apalache.tla.lir.OperEx;
import io.github.tlaplus.hardening.gen.Draw;
import io.github.tlaplus.hardening.gen.IrGenerationConfig;

import java.util.List;
import java.util.Map;
import java.util.Random;

import org.apalache_mc.tla.jir.TlaExpressions;
import org.apalache_mc.tla.jir.TlaOperators;
import org.junit.jupiter.api.Test;

/**
 * Fixed vectors pin the applicative forms' IR and cursor protocol. Operands are drawn at depth zero,
 * so they are byte-free terminals that name a visible binding of their type when one exists.
 */
class ApplicativeExprGenFactoryTest {
    private static final RecordType RECORD = new RecordType(List.of(
            new Field("f", PrimitiveType.BOOL), new Field("g", PrimitiveType.INT)));
    private static final TupleType TUPLE = new TupleType(List.of(
            PrimitiveType.BOOL, PrimitiveType.INT, PrimitiveType.BOOL));
    private static final SequenceType SEQUENCE = new SequenceType(PrimitiveType.INT);

    private final GenerationContext context = new GenerationContext(IrGenerationConfig.defaults());
    private final IrTypeGenFactory types = new IrTypeGenFactory(context);
    private final IrExprGenFactory expressions = new IrExprGenFactory(context, types);

    @Test
    void aRecordReadCanProjectAVisibleName() {
        assertForm("x[\"g\"]", ApplicativeExpressionKind.RECORD_ACCESS, PrimitiveType.INT,
                List.of(ScopedName.stateVariable("x", RECORD)), 1, 1, 42);
    }

    @Test
    void aTupleReadChoosesAmongThePositionsOfTheRequestedType() {
        assertForm("t[3]", ApplicativeExpressionKind.TUPLE_ACCESS, PrimitiveType.BOOL,
                List.of(ScopedName.binder("t", TUPLE)), 1, 1, 1, 42);
        assertForm("t[1]", ApplicativeExpressionKind.TUPLE_ACCESS, PrimitiveType.BOOL,
                List.of(ScopedName.binder("t", TUPLE)), 1, 1, 0, 42);
    }

    @Test
    void aComponentOfAVisibleNameIsAReachableType() {
        var nested = new SequenceType(RECORD);
        // The applied type is the record type inside `s`, keeping its field names, rather than a
        // fresh record; at depth zero its operand is that type's closed terminal.
        assertForm("[f |-> FALSE, g |-> 1][\"g\"]", ApplicativeExpressionKind.RECORD_ACCESS,
                PrimitiveType.INT, List.of(ScopedName.binder("s", nested)), 1, 1, 42);
        var reachable = new Draw(new byte[0]).draw(context.withBinding(
                ScopedName.binder("s", nested), ignored -> context.reachableTypes()));
        assertEquals(List.of(nested, RECORD, PrimitiveType.BOOL, PrimitiveType.INT), reachable);
    }

    @Test
    void anEvenMarkerOrAnEmptyScopeDrawsAFreshType() {
        assertForm("<<1>>[1]", ApplicativeExpressionKind.TUPLE_ACCESS, PrimitiveType.INT,
                List.of(ScopedName.binder("t", TUPLE)), 1, 0, 0, 42);
        assertForm("<<1>>[1]", ApplicativeExpressionKind.TUPLE_ACCESS, PrimitiveType.INT,
                List.of(), 1, 1, 0, 42);
    }

    @Test
    void aFreshRecordPlacesTheRequestedFieldAmongDrawnOnes() {
        // Fresh: one more field of the first primitive type (Boolean), and the requested
        // field second. Field names come from the run's fresh supply.
        assertForm("[field0 |-> FALSE, field1 |-> 1][\"field1\"]",
                ApplicativeExpressionKind.RECORD_ACCESS, PrimitiveType.INT,
                List.of(), 1, 0, 1, 0, 0, 1, 42);
    }

    @Test
    void aSequenceReadDrawsAnIntegerIndex() {
        assertForm("s[1]", ApplicativeExpressionKind.SEQUENCE_ACCESS, PrimitiveType.INT,
                List.of(ScopedName.binder("s", SEQUENCE)), 1, 1, 42);
    }

    @Test
    void updatesReplaceOneComponentOfTheRequestedValue() {
        assertForm("[ x EXCEPT ![\"f\"] = FALSE ]", ApplicativeExpressionKind.RECORD_EXCEPT, RECORD,
                List.of(ScopedName.stateVariable("x", RECORD)), 1, 0, 42);
        assertForm("[ t EXCEPT ![2] = 1 ]", ApplicativeExpressionKind.TUPLE_EXCEPT, TUPLE,
                List.of(ScopedName.binder("t", TUPLE)), 1, 1, 42);
        assertForm("[ s EXCEPT ![1] = 1 ]", ApplicativeExpressionKind.SEQUENCE_EXCEPT, SEQUENCE,
                List.of(ScopedName.binder("s", SEQUENCE)), 1, 42);
    }

    @Test
    void domainsTakeAnyVisibleValueOfTheirKind() {
        assertForm("DOMAIN x", ApplicativeExpressionKind.RECORD_DOMAIN,
                new SetType(PrimitiveType.STRING),
                List.of(ScopedName.stateVariable("x", RECORD)), 1, 1, 42);
        assertForm("DOMAIN s", ApplicativeExpressionKind.SEQUENCE_DOMAIN,
                new SetType(PrimitiveType.INT),
                List.of(ScopedName.binder("t", TUPLE), ScopedName.binder("s", SEQUENCE)), 1, 1, 42);
    }

    @Test
    void anExhaustedCursorStillBuildsEveryForm() {
        for (var kind : ApplicativeExpressionKind.values()) {
            var type = switch (kind.operation()) {
                case ACCESS -> PrimitiveType.BOOL;
                case EXCEPT -> switch (kind.applicativeType()) {
                    case RECORD -> RECORD;
                    case TUPLE -> TUPLE;
                    case SEQUENCE -> SEQUENCE;
                };
                case DOMAIN -> new SetType(kind.applicativeType().domainElement());
            };
            var draw = new Draw(new byte[0]);
            assertFalse(print(draw.draw(expressions.mkGen(kind, type, 1))).isEmpty(), kind.name());
        }
    }

    @Test
    void anUpdateReplacementNeverContainsALabel() {
        // SANY rejects every label inside an EXCEPT replacement (sany-002).
        var config = IrGenerationConfig.defaults().withFormWeights(Map.of(
                GeneralExpressionKind.LABEL, IrGenerationConfig.MAXIMUM_FORM_WEIGHT));
        var random = new Random(0xe8ce97L);
        var labelled = false;
        for (var sample = 0; sample < 300; sample++) {
            var runContext = new GenerationContext(config);
            var runTypes = new IrTypeGenFactory(runContext);
            var runExpressions = new IrExprGenFactory(runContext, runTypes);
            var input = new byte[64 + random.nextInt(256)];
            random.nextBytes(input);
            var update = new Draw(input).draw(
                    runExpressions.mkGen(ApplicativeExpressionKind.RECORD_EXCEPT, RECORD, 6));
            var arguments = TlaExpressions.arguments((OperEx) update);
            labelled |= containsOperator(arguments.getFirst(), TlaOperators.LABEL);
            assertFalse(containsOperator(arguments.getLast(), TlaOperators.LABEL), print(update));
        }
        assertTrue(labelled, "no label was generated outside a replacement, so the check is vacuous");
    }
}
