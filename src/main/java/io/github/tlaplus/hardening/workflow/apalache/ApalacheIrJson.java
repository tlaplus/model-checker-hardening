package io.github.tlaplus.hardening.workflow.apalache;

import at.forsyte.apalache.tla.lir.OperEx;
import at.forsyte.apalache.tla.lir.TlaDecl;
import at.forsyte.apalache.tla.lir.TlaEx;
import at.forsyte.apalache.tla.lir.TlaModule;
import at.forsyte.apalache.tla.lir.TlaOperDecl;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.apalache_mc.tla.jio.TlaJson;
import org.apalache_mc.tla.jio.TlaJsonException;
import org.apalache_mc.tla.jir.TlaDeclarations;
import org.apalache_mc.tla.jir.TlaExpressions;
import org.apalache_mc.tla.jir.TlaModules;
import org.apalache_mc.tla.jir.TlaOperators;

/** Renders an assembled module as the typed Apalache IR JSON that Apalache consumes. */
public final class ApalacheIrJson {
    private ApalacheIrJson() {}

    /**
     * Renders the module, dropping expression labels.
     *
     * <p>Labels are semantically transparent to the model checker, so each is replaced by its
     * first operand here while the parser and TLC retain it. The pinned snapshot can read labels,
     * but preserving this compatibility normalization keeps the facade migration from also
     * changing checker inputs.
     */
    public static String render(TlaModule module) {
        Objects.requireNonNull(module, "module");
        return TlaJson.writeModule(eraseLabels(module), 2);
    }

    /**
     * Reads one typed module back from Apalache's JSON IR, rejecting a missing or oversized file.
     *
     * <p>The Java facade's direct reader is used rather than its builder-backed alternative, which
     * reconstructs a polymorphic empty set as a set of sets and loses the generic tags this project
     * relies on.
     */
    public static TlaModule parse(Path path, int maximumBytes) throws IOException {
        if (!Files.isRegularFile(path) || Files.size(path) > maximumBytes) {
            throw new IOException("missing or oversized typechecker output: " + path);
        }
        try {
            return TlaJson.readModule(Files.readString(path));
        } catch (TlaJsonException exception) {
            throw new IOException("invalid typechecker IR: " + exception.getMessage(), exception);
        }
    }

    private static TlaModule eraseLabels(TlaModule module) {
        var declarations = TlaModules.declarations(module).stream()
                .map(ApalacheIrJson::eraseLabels)
                .toList();
        return TlaModules.create(module.name(), declarations);
    }

    private static TlaDecl eraseLabels(TlaDecl declaration) {
        return declaration instanceof TlaOperDecl operator
                ? TlaDeclarations.rewrite(operator, ApalacheIrJson::eraseLabel)
                : declaration;
    }

    /** The walk is bottom-up, so a nested label has already been replaced by its operand. */
    private static TlaEx eraseLabel(TlaEx expression) {
        return expression instanceof OperEx operator && operator.oper() == TlaOperators.LABEL
                ? TlaExpressions.arguments(operator).getFirst()
                : expression;
    }
}
