package io.github.tlaplus.hardening.workflow.tool;

import at.forsyte.apalache.tla.lir.TlaVarDecl;
import io.github.tlaplus.hardening.corpus.EncodedTrace;
import io.github.tlaplus.hardening.gen.trace.ItfTraces;
import io.github.tlaplus.hardening.gen.trace.TracePayload;
import java.util.List;
import java.util.Optional;

/**
 * Turns the trace a checker worker recorded into what its stage stores (ADR 0034 §5): read at the
 * types of the module's variables, then encoded canonically. A trace that does not fit its module
 * is not stored, which, like a missing trace length, changes no verdict.
 */
final class RecordedTraces {
    private RecordedTraces() {}

    static Optional<EncodedTrace> encode(Optional<String> recorded, List<TlaVarDecl> variables) {
        return recorded.flatMap(json -> {
            try {
                return Optional.of(new EncodedTrace(TracePayload.encodeTrace(ItfTraces.read(json, variables))));
            } catch (IllegalArgumentException unfitting) {
                return Optional.empty();
            }
        });
    }
}
