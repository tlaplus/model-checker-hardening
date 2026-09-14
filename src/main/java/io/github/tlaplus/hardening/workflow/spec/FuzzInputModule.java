package io.github.tlaplus.hardening.workflow.spec;

import at.forsyte.apalache.tla.lir.TlaDecl;
import at.forsyte.apalache.tla.lir.TlaEx;
import at.forsyte.apalache.tla.lir.TlaModule;
import at.forsyte.apalache.tla.lir.TlaVarDecl;
import io.github.tlaplus.hardening.gen.GeneratedSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apalache_mc.tla.jir.TlaDeclarations;
import org.apalache_mc.tla.jir.TlaExpressions;
import org.apalache_mc.tla.jir.TlaModules;
import org.apalache_mc.tla.jir.TlaTypedScopeUncheckedBuilder;
import org.apalache_mc.tla.jir.TlaTypes;

/**
 * Assembles the module checked by the parser and model-checker stages.
 *
 * <p>Every generated artifact reaches the tools through this one assembler, whatever the generator
 * produced. The entry-point names below are the contract between the assembled module and the
 * fixed tool invocations: TLC's configuration file and Apalache's command line name them, so they
 * live here rather than being spelled again at each call site.
 */
public final class FuzzInputModule {
    /** Name of the assembled module, and therefore of the file written for each tool. */
    public static final String MODULE_NAME = "FuzzInput";

    /** Initial-state predicate. */
    public static final String INIT = "Init";

    /**
     * Next-state action. A generated module's action ends with a stuttering disjunct over every
     * variable, because Apalache, unlike TLC, adds no stuttering step of its own.
     */
    public static final String NEXT = "Next";

    /** State invariant. */
    public static final String INV = "Inv";

    /** Every definition some tool evaluates directly; everything else is reached through them. */
    public static final List<String> ENTRY_POINTS = List.of(INIT, NEXT, INV);

    private static final String VARIABLE_NAME = "exprValue";

    private FuzzInputModule() {}

    /**
     * Wraps one generated expression in the single-state module: the expression is both the
     * initial value of {@code exprValue} and the invariant asserted about it.
     */
    public static TlaModule create(TlaEx expression) {
        Objects.requireNonNull(expression, "expression");

        var builder = new TlaTypedScopeUncheckedBuilder();
        var expressionType = TlaTypes.typeOf(expression);
        var exprValue = TlaDeclarations.variable(VARIABLE_NAME, expressionType);
        // Apalache requires unique node identities, and the expression appears twice.
        var expressionCopy = TlaExpressions.deepCopy(expression);

        var init = builder.eql(builder.varDeclAsNameEx(exprValue), expression);
        var next = builder.unchanged(builder.varDeclAsNameEx(exprValue));
        var invariant = builder.eql(builder.varDeclAsNameEx(exprValue), expressionCopy);
        return assemble(List.of(exprValue), init, next, invariant);
    }

    /**
     * Assembles a generated module and closes its next-state action with a stuttering disjunct.
     *
     * <p>The generated declarations keep their names; only the entry points are fixed, so the tool
     * invocations need not change with the input.
     */
    public static TlaModule create(GeneratedSpec spec) {
        Objects.requireNonNull(spec, "spec");
        var builder = new TlaTypedScopeUncheckedBuilder();
        var declarations = new ArrayList<TlaDecl>(spec.variables());
        spec.operators().forEach(operator -> declarations.add(operator.declaration()));
        var next = builder.or(spec.nextAction(), builder.unchanged(variablesTuple(builder, spec.variables())));
        return assemble(declarations, spec.initPredicate(), next, spec.invariant());
    }

    /** Appends the fixed entry points once, in the order emitted to every tool. */
    private static TlaModule assemble(
            List<? extends TlaDecl> prefix,
            TlaEx init,
            TlaEx next,
            TlaEx invariant) {
        var builder = new TlaTypedScopeUncheckedBuilder();
        var declarations = new ArrayList<TlaDecl>(prefix);
        declarations.add(builder.decl(INIT, init));
        declarations.add(builder.decl(NEXT, next));
        declarations.add(builder.decl(INV, invariant));
        return TlaModules.create(MODULE_NAME, declarations);
    }

    /** Returns the tuple of every declared variable, in declaration order. */
    private static TlaEx variablesTuple(TlaTypedScopeUncheckedBuilder builder, List<TlaVarDecl> variables) {
        return builder.tuple(variables.stream().map(builder::varDeclAsNameEx).toArray(TlaEx[]::new));
    }
}
