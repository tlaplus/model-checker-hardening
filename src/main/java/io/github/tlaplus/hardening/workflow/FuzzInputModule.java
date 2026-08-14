package io.github.tlaplus.hardening.workflow;

import at.forsyte.apalache.tla.lir.TlaDecl;
import at.forsyte.apalache.tla.lir.TlaEx;
import at.forsyte.apalache.tla.lir.TlaModule;
import at.forsyte.apalache.tla.lir.TlaType1$;
import at.forsyte.apalache.tla.lir.transformations.impl.IdleTracker;
import at.forsyte.apalache.tla.lir.transformations.standard.DeepCopy;
import java.util.List;
import java.util.Objects;
import org.apalache_mc.tla.jir.TlaCheckedBuilder;
import org.apalache_mc.tla.jir.TlaDeclarations;
import scala.jdk.javaapi.CollectionConverters;

/** Constructs the module checked by the parser stage from a generated expression. */
public final class FuzzInputModule {
    private static final String MODULE_NAME = "FuzzInput";
    private static final String VARIABLE_NAME = "exprValue";

    private FuzzInputModule() {}

    public static TlaModule create(TlaEx expression) {
        Objects.requireNonNull(expression, "expression");

        var builder = new TlaCheckedBuilder();
        var expressionType = TlaType1$.MODULE$.fromTypeTag(expression.typeTag());
        var exprValue = TlaDeclarations.variable(VARIABLE_NAME, expressionType);
        var expressionCopy = new DeepCopy(new IdleTracker()).deepCopyEx(expression);

        var init = builder.build(builder.decl(
                "Init",
                builder.eql(
                        builder.varDeclAsNameEx(exprValue), builder.unchecked(expression))));
        var next = builder.build(builder.decl(
                "Next", builder.unchanged(builder.varDeclAsNameEx(exprValue))));
        var invariant = builder.build(builder.decl(
                "Inv",
                builder.eql(
                        builder.varDeclAsNameEx(exprValue),
                        builder.unchecked(expressionCopy))));

        var declarations = List.<TlaDecl>of(exprValue, init, next, invariant);
        return new TlaModule(
                MODULE_NAME, CollectionConverters.asScala(declarations).toSeq());
    }
}
