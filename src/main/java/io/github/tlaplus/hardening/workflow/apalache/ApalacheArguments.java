package io.github.tlaplus.hardening.workflow.apalache;

import io.github.tlaplus.hardening.workflow.spec.FuzzInputModule;
import io.github.tlaplus.hardening.workflow.worker.CheckKind;
import io.github.tlaplus.hardening.workflow.worker.CheckRequest;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** The Apalache command line for one checked module. */
public final class ApalacheArguments {
    private ApalacheArguments() {}

    /** Returns the directory below which Apalache writes the runs of one job. */
    static Path outputDirectory(Path jobDirectory) {
        return jobDirectory.resolve("out");
    }

    /** Returns the check invocation for one input: {@link #options} between the job's paths. */
    static String[] check(Path jobDirectory, Path specification, CheckRequest request) {
        var arguments = new ArrayList<String>();
        arguments.add("--out-dir=" + outputDirectory(jobDirectory));
        arguments.addAll(options(request));
        arguments.add(specification.toString());
        return arguments.toArray(String[]::new);
    }

    /**
     * Returns the options of every Apalache invocation {@code request} takes, in the order they
     * run: one, or the relation followed by its property runs (ADR 0029 §1).
     */
    public static List<String> texts(CheckRequest request) {
        var texts = new ArrayList<String>();
        texts.add(String.join(" ", options(request)));
        if (request.kind().hasPropertyRuns()) {
            CheckKind.PROPERTY_RUNS.forEach(kind -> texts.add(String.join(" ", options(request.withKind(kind)))));
        }
        return List.copyOf(texts);
    }

    /**
     * Returns the {@code check} command and its options for one invocation. The unrolling length
     * comes from the request: an expression input has a single state, while an assembled module
     * bounds its own step counter.
     *
     * <p>A temporal property checked with the invariant is checked as {@link
     * FuzzInputModule#LIVENESS}, the implication from the module's fairness to its property, because
     * Apalache supports no fairness in a specification. A module with fairness then makes Apalache
     * report that limitation instead of returning a counterexample the fairness would exclude.
     *
     * <p>A property run of a metamorphic module checks its side's property directly, from its side's
     * {@code Init} and {@code Next} (ADR 0029 §1). Its corpus quarantines fairness, and {@code TRUE
     * => P} would need one more step than {@code P} for an action property ({@code
     * apalache-temporal-005}).
     *
     * <p>An action invariant joins the invariant in {@code --inv}; Apalache classifies an
     * action-level operator as an action invariant and checks it on every transition.
     */
    private static List<String> options(CheckRequest request) {
        var kind = request.kind();
        var side = FuzzInputModule.entryPoints(kind.side());
        var options = new ArrayList<String>();
        options.add("check");
        options.add("--init=" + side.init());
        options.add("--next=" + side.next());
        if (kind.invariant()) {
            options.add("--inv=" + (kind.actionInvariant()
                    ? FuzzInputModule.INV + "," + FuzzInputModule.STEP
                    : FuzzInputModule.INV));
        }
        if (kind.property()) {
            options.add("--temporal=" + (kind.invariant() ? FuzzInputModule.LIVENESS : side.property()));
        }
        options.add("--length=" + request.unrollingLength());
        options.add("--no-deadlock");
        return options;
    }
}
