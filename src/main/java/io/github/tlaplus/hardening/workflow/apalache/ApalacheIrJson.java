package io.github.tlaplus.hardening.workflow.apalache;

import at.forsyte.apalache.io.json.ujsonimpl.TlaToUJson$;
import at.forsyte.apalache.tla.lir.LetInEx;
import at.forsyte.apalache.tla.lir.OperEx;
import at.forsyte.apalache.tla.lir.TlaDecl;
import at.forsyte.apalache.tla.lir.TlaEx;
import at.forsyte.apalache.tla.lir.TlaModule;
import at.forsyte.apalache.tla.lir.TlaOperDecl;
import at.forsyte.apalache.tla.lir.oper.TlaOper;
import io.github.tlaplus.hardening.workflow.spec.FuzzInputModule;
import java.util.List;
import org.apalache_mc.tla.jir.TlaTypedScopeUncheckedBuilder;
import scala.jdk.javaapi.CollectionConverters;

/** Renders the generated module as typed Apalache IR JSON. */
public final class ApalacheIrJson {
    private static final TlaOper LABEL_OPERATOR = labelOperator();

    private ApalacheIrJson() {}

    public static String render(TlaEx expression) {
        var module = eraseLabels(FuzzInputModule.create(expression));
        return TlaToUJson$.MODULE$.apply(module).render(2, false);
    }

    private static TlaModule eraseLabels(TlaModule module) {
        List<TlaDecl> declarations = CollectionConverters.asJava(module.declarations()).stream()
                .map(ApalacheIrJson::eraseLabels)
                .toList();
        return new TlaModule(
                module.name(), CollectionConverters.asScala(declarations).toSeq());
    }

    private static TlaDecl eraseLabels(TlaDecl declaration) {
        if (!(declaration instanceof TlaOperDecl operator)) {
            return declaration;
        }
        var normalized = new TlaOperDecl(
                operator.name(),
                operator.formalParams(),
                eraseLabels(operator.body()),
                operator.typeTag());
        normalized.isRecursive_$eq(operator.isRecursive());
        return normalized;
    }

    private static TlaEx eraseLabels(TlaEx expression) {
        if (expression instanceof OperEx operator) {
            if (operator.oper().equals(LABEL_OPERATOR)) {
                return eraseLabels(operator.args().head());
            }
            List<TlaEx> arguments = CollectionConverters.asJava(operator.args()).stream()
                    .map(ApalacheIrJson::eraseLabels)
                    .toList();
            return new OperEx(
                    operator.oper(),
                    CollectionConverters.asScala(arguments).toSeq(),
                    operator.typeTag());
        }
        if (expression instanceof LetInEx letIn) {
            List<TlaOperDecl> declarations = CollectionConverters.asJava(letIn.decls()).stream()
                    .map(declaration -> (TlaOperDecl) eraseLabels(declaration))
                    .toList();
            return new LetInEx(
                    eraseLabels(letIn.body()),
                    CollectionConverters.asScala(declarations).toSeq(),
                    letIn.typeTag());
        }
        return expression;
    }

    private static TlaOper labelOperator() {
        var builder = new TlaTypedScopeUncheckedBuilder();
        return ((OperEx) builder.label(builder.bool(false), "label")).oper();
    }
}
