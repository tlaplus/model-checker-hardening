package io.github.tlaplus.hardening.gen.graph;

import at.forsyte.apalache.tla.lir.TlaVarDecl;
import io.github.tlaplus.hardening.common.ArchRole;
import io.github.tlaplus.hardening.common.Key;
import io.github.tlaplus.hardening.common.Preconditions;
import io.github.tlaplus.hardening.common.Role;
import io.github.tlaplus.hardening.gen.GeneratedSpec;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * A module decoded from a graph input (ADR 0023 §2), with the graph it encodes and the target its
 * invariant {@code pc /= target} names.
 *
 * <p>Every edge leaving the current node is enabled while the step counter is below its bound, so
 * a state with {@code pc = target} is reachable exactly when a path of at most
 * {@code spec.stepBound()} edges leads from node 0 to the target (ADR 0023 §4).
 *
 * @param spec the declarations of the module, which {@code FuzzInputModule} assembles as for a
 *     module input
 * @param graph the control graph
 * @param target the node the invariant names
 */
@ArchRole(Role.MODEL)
@Key
public record GeneratedGraph(GeneratedSpec spec, TransitionGraph graph, int target) {
    /** Name of the control variable, whose value is the current node. */
    public static final String PC_VARIABLE = "pc";

    public GeneratedGraph {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(graph, "graph");
        Preconditions.require(target >= 0 && target < graph.nodeCount(), "the target is a node of the graph");
        Preconditions.require(!graph.successors().getFirst().isEmpty(), "the initial node has a successor");
        Preconditions.require(spec.variables().stream().map(TlaVarDecl::name).anyMatch(PC_VARIABLE::equals),
                "a graph module declares pc");
    }

    /**
     * Returns the number of transitions after which a behaviour first reaches the target, or empty
     * when no behaviour reaches it within the step bound.
     */
    public OptionalInt targetDistance() {
        var distance = graph.distance(target);
        return distance.isPresent() && distance.getAsInt() <= spec.stepBound() ? distance : OptionalInt.empty();
    }
}
