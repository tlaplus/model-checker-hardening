package io.github.tlaplus.hardening.workflow.tlc;

import io.github.tlaplus.hardening.common.ArchRole;
import io.github.tlaplus.hardening.common.Key;
import io.github.tlaplus.hardening.common.Role;
import io.github.tlaplus.hardening.workflow.spec.FuzzInputModule;
import io.github.tlaplus.hardening.workflow.worker.CheckKind;
import io.github.tlaplus.hardening.workflow.worker.CheckRequest;
import java.util.ArrayList;
import java.util.List;

/** The TLC configuration file for one checked module. */
@ArchRole(Role.CODEC)
@Key
public final class TlcConfiguration {
    private TlcConfiguration() {}

    /**
     * Returns the configuration of every TLC invocation {@code request} takes, in the order they
     * run: one, or the relation followed by its property runs (ADR 0027 §4).
     */
    public static List<String> texts(CheckRequest request) {
        var texts = new ArrayList<String>();
        texts.add(text(request));
        if (request.kind().hasPropertyRuns()) {
            CheckKind.PROPERTY_RUNS.forEach(kind -> texts.add(text(request.withKind(kind))));
        }
        return List.copyOf(texts);
    }

    /**
     * Returns the configuration naming the module's entry points.
     *
     * <p>TLC checks the specification of the requested side, whose fairness constrains a temporal
     * property, and needs no state constraint: every generated module bounds its step counter in its
     * next-state action. The property is named only when the request asks for it, since TLC
     * otherwise spends its liveness checking on {@code TRUE}. An action invariant is a second
     * property, since TLC rejects a primed {@code INVARIANT}. A property run of a metamorphic module
     * checks its property alone (ADR 0027 §4).
     */
    static String text(CheckRequest request) {
        var kind = request.kind();
        var text = new StringBuilder()
                .append("SPECIFICATION ").append(FuzzInputModule.specification(kind.side())).append('\n');
        if (kind.invariant()) {
            text.append("INVARIANT ").append(FuzzInputModule.INV).append('\n');
        }
        if (kind.property()) {
            text.append("PROPERTY ").append(FuzzInputModule.property(kind.side())).append('\n');
        }
        if (kind.actionInvariant()) {
            text.append("PROPERTY ").append(FuzzInputModule.STEP_PROPERTY).append('\n');
        }
        return text.toString();
    }
}
