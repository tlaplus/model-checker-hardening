package io.github.tlaplus.hardening.workflow.spec;

import at.forsyte.apalache.tla.lir.TlaDecl;
import at.forsyte.apalache.tla.lir.TlaEx;
import at.forsyte.apalache.tla.lir.TlaModule;
import at.forsyte.apalache.tla.lir.TlaType1$;
import at.forsyte.apalache.tla.lir.transformations.impl.IdleTracker;
import at.forsyte.apalache.tla.lir.transformations.standard.DeepCopy;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apalache_mc.tla.jir.TlaDeclarations;
import org.apalache_mc.tla.jir.TlaTypedScopeUncheckedBuilder;
import scala.jdk.javaapi.CollectionConverters;

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

    /** Next-state action. */
    public static final String NEXT = "Next";

    /** State invariant. */
    public static final String INV = "Inv";

    /**
     * State constraint bounding exploration.
     *
     * <p>A generated module advances a step counter and bounds it here; the expression wrapper has
     * a single state and defines the constraint as {@code TRUE}. Defining it unconditionally is
     * what lets one configuration file serve both kinds.
     */
    public static final String BOUND = "Bound";

    private static final String VARIABLE_NAME = "exprValue";

    private FuzzInputModule() {}

    /**
     * Wraps one generated expression in the single-state module: the expression is both the
     * initial value of {@code exprValue} and the invariant asserted about it.
     */
    public static TlaModule create(TlaEx expression) {
        Objects.requireNonNull(expression, "expression");

        var builder = new TlaTypedScopeUncheckedBuilder();
        var expressionType = TlaType1$.MODULE$.fromTypeTag(expression.typeTag());
        var exprValue = TlaDeclarations.variable(VARIABLE_NAME, expressionType);
        // Apalache requires unique node identities, and the expression appears twice.
        var expressionCopy = new DeepCopy(new IdleTracker()).deepCopyEx(expression);

        var init = builder.decl(
                INIT, builder.eql(builder.varDeclAsNameEx(exprValue), expression));
        var next = builder.decl(
                NEXT, builder.unchanged(builder.varDeclAsNameEx(exprValue)));
        var invariant = builder.decl(
                INV,
                builder.eql(builder.varDeclAsNameEx(exprValue), expressionCopy));
        var bound = builder.decl(BOUND, builder.bool(true));

        var declarations = new ArrayList<TlaDecl>(List.of(exprValue));
        declarations.add(init);
        declarations.add(next);
        declarations.add(invariant);
        declarations.add(bound);
        return module(declarations);
    }

    private static TlaModule module(List<TlaDecl> declarations) {
        return new TlaModule(
                MODULE_NAME, CollectionConverters.asScala(declarations).toSeq());
    }
}
