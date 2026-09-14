package io.github.tlaplus.hardening.workflow.tlc;

import io.github.tlaplus.hardening.workflow.spec.FuzzInputModule;
import io.github.tlaplus.hardening.workflow.worker.CheckRequest;

/** The TLC configuration file for one checked module. */
final class TlcConfiguration {
    private TlcConfiguration() {}

    /**
     * Returns the configuration naming the module's entry points.
     *
     * <p>TLC checks the specification, whose fairness constrains a temporal property, and needs no
     * state constraint: every generated module bounds its step counter in its next-state action.
     * The property is named only when the request asks for it, since TLC otherwise spends its
     * liveness checking on {@code TRUE}.
     */
    static String text(CheckRequest request) {
        var text = new StringBuilder()
                .append("SPECIFICATION ").append(FuzzInputModule.SPEC).append('\n')
                .append("INVARIANT ").append(FuzzInputModule.INV).append('\n');
        if (request.temporalProperty()) {
            text.append("PROPERTY ").append(FuzzInputModule.PROP).append('\n');
        }
        return text.toString();
    }
}
