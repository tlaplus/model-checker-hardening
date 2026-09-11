package io.github.tlaplus.hardening.signature;

import static org.junit.jupiter.api.Assertions.assertTrue;

import at.forsyte.apalache.tla.lir.TlaEx;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.Pattern;
import org.apalache_mc.tla.jio.TlaJson;
import org.apalache_mc.tla.jir.TlaModules;
import org.apalache_mc.tla.jir.TlaTypedScopeUncheckedBuilder;
import org.apalache_mc.tla.jir.TlaTypes;
import org.junit.jupiter.api.Test;

/**
 * Pins the contract the manual states: a pattern names an operator exactly as the {@code oper}
 * field of Apalache's IR JSON, which {@code fuzztla print --apalache-ir} prints.
 */
class OperatorNamesTest {
    @Test
    void namesOperatorsAsTheIrJsonDoes() {
        var builder = new TlaTypedScopeUncheckedBuilder();
        var step = builder.name("step", TlaTypes.INT);
        var expressions = new LinkedHashMap<String, TlaEx>();
        expressions.put("MOD", builder.mod(step, builder.integer(0)));
        expressions.put("DIV", builder.div(step, builder.integer(0)));
        expressions.put("POW", builder.exp(step, builder.integer(0)));
        expressions.put("Sequences!Seq", builder.seqSet(builder.enumSet(builder.integer(1))));

        for (var entry : expressions.entrySet()) {
            var operator = OperatorNames.find(entry.getKey()).orElseThrow();
            var json = TlaJson.writeModule(
                    TlaModules.create("M", List.of(builder.decl("Op", entry.getValue()))), 2);
            var field = Pattern.compile("\"oper\"\\s*:\\s*\"" + Pattern.quote(operator.name()) + "\"");
            assertTrue(field.matcher(json).find(), json);
        }
    }

    @Test
    void knowsTheOperatorsTheManualNames() {
        assertTrue(OperatorNames.all().keySet().containsAll(List.of(
                "MOD", "DIV", "POW", "Sequences!Seq", "Sequences!Head", "OPER_APP", "SET_ENUM",
                "TUPLE", "FUN_APP", "SET_FILTER", "CHOOSE3", "FiniteSets!Cardinality",
                "Apalache!ApaFoldSet")));
    }
}
