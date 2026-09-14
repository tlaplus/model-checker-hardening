package io.github.tlaplus.hardening.workflow.apalache;

import io.github.tlaplus.hardening.workflow.spec.FuzzInputModule;
import io.github.tlaplus.hardening.workflow.worker.CheckRequest;
import java.nio.file.Path;
import java.util.ArrayList;

/** The Apalache command line for one checked module. */
final class ApalacheArguments {
    private ApalacheArguments() {}

    /**
     * Returns the check invocation for one input. The unrolling length comes from the request: an
     * expression input has a single state, while an assembled module bounds its own step counter.
     *
     * <p>A temporal property is checked as {@link FuzzInputModule#LIVENESS}, the implication from
     * the module's fairness to its property, because Apalache supports no fairness in a
     * specification. A module with fairness then makes Apalache report that limitation instead of
     * returning a counterexample the fairness would exclude.
     */
    static String[] check(Path jobDirectory, Path specification, CheckRequest request) {
        var arguments = new ArrayList<String>();
        arguments.add("--out-dir=" + jobDirectory.resolve("out"));
        arguments.add("check");
        arguments.add("--init=" + FuzzInputModule.INIT);
        arguments.add("--next=" + FuzzInputModule.NEXT);
        arguments.add("--inv=" + FuzzInputModule.INV);
        if (request.temporalProperty()) {
            arguments.add("--temporal=" + FuzzInputModule.LIVENESS);
        }
        arguments.add("--length=" + request.unrollingLength());
        arguments.add("--no-deadlock");
        arguments.add(specification.toString());
        return arguments.toArray(String[]::new);
    }
}
