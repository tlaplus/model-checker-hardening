package io.github.tlaplus.hardening.workflow.apalache;

import static io.github.tlaplus.hardening.common.ScalaCollections.list;
import static io.github.tlaplus.hardening.common.ScalaCollections.seq;

import at.forsyte.apalache.io.json.DefaultTagJsonReader;
import at.forsyte.apalache.io.json.ujsonimpl.TlaToUJson$;
import at.forsyte.apalache.io.json.ujsonimpl.UJsonRepresentation;
import at.forsyte.apalache.io.json.ujsonimpl.UJsonToTla;
import at.forsyte.apalache.tla.lir.OperEx;
import at.forsyte.apalache.tla.lir.TlaDecl;
import at.forsyte.apalache.tla.lir.TlaEx;
import at.forsyte.apalache.tla.lir.TlaModule;
import at.forsyte.apalache.tla.lir.TlaOperDecl;
import at.forsyte.apalache.tla.lir.oper.TlaOper;
import io.github.tlaplus.hardening.common.TlaExpressions;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.apalache_mc.tla.jir.TlaTypedScopeUncheckedBuilder;
import scala.Option;
import ujson.Readable;

/** Renders an assembled module as the typed Apalache IR JSON that Apalache consumes. */
public final class ApalacheIrJson {
    private static final TlaOper LABEL_OPERATOR = labelOperator();

    private ApalacheIrJson() {}

    /**
     * Renders the module, dropping expression labels.
     *
     * <p>The pinned Apalache JSON reader cannot decode {@code LABEL}. Labels are semantically
     * transparent to the model checker, so each is replaced by its first operand here; the parser
     * and TLC keep the labeled expression.
     */
    public static String render(TlaModule module) {
        Objects.requireNonNull(module, "module");
        return TlaToUJson$.MODULE$.apply(eraseLabels(module)).render(2, false);
    }

    /**
     * Reads one typed module back from Apalache's JSON IR, rejecting a missing or oversized file.
     *
     * <p>The direct reader is used rather than the builder-backed one: in the pinned dependency the
     * builder reconstructs a polymorphic empty set as a set of sets, losing the generic tags this
     * project relies on.
     */
    public static TlaModule parse(Path path, int maximumBytes) throws IOException {
        if (!Files.isRegularFile(path) || Files.size(path) > maximumBytes) {
            throw new IOException("missing or oversized typechecker output: " + path);
        }
        var json = ujson.package$.MODULE$.read(Readable.fromString(Files.readString(path)), false);
        var decoded = new UJsonToTla(Option.empty(), DefaultTagJsonReader::apply)
                .fromSingleModule(new UJsonRepresentation(json));
        if (decoded.isFailure()) {
            throw new IOException("invalid typechecker IR: " + decoded);
        }
        return decoded.get();
    }

    private static TlaModule eraseLabels(TlaModule module) {
        return new TlaModule(module.name(), seq(list(module.declarations()).stream()
                .map(ApalacheIrJson::eraseLabels)
                .toList()));
    }

    private static TlaDecl eraseLabels(TlaDecl declaration) {
        return declaration instanceof TlaOperDecl operator
                ? TlaExpressions.rewrite(operator, ApalacheIrJson::eraseLabel)
                : declaration;
    }

    /** The walk is bottom-up, so a nested label has already been replaced by its operand. */
    private static TlaEx eraseLabel(TlaEx expression) {
        return expression instanceof OperEx operator && operator.oper().equals(LABEL_OPERATOR)
                ? operator.args().head()
                : expression;
    }

    private static TlaOper labelOperator() {
        var builder = new TlaTypedScopeUncheckedBuilder();
        return ((OperEx) builder.label(builder.bool(false), "label")).oper();
    }
}
