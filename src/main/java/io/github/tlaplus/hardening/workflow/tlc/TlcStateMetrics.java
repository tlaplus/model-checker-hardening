package io.github.tlaplus.hardening.workflow.tlc;

import io.github.tlaplus.hardening.checker.ExplorationCount;
import io.github.tlaplus.hardening.checker.ExplorationMetrics;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import tla2sany.semantic.SemanticNode;
import tlc2.tool.Action;
import tlc2.tool.TLCState;
import tlc2.util.BitVector;
import tlc2.util.FP64;
import tlc2.util.IStateWriter;
import tlc2.value.impl.Value;

/**
 * Measures the state graph TLC explores, through the state-writer callbacks TLC makes for every
 * initial state and every generated transition.
 *
 * <p>TLC reports each initial state once, and each transition with whether its target is new. The
 * metrics therefore cover exactly the states TLC found, including when TLC stops on a violation or
 * an evaluation error. The callbacks are synchronized because TLC may run several workers.
 *
 * <p>Two states that differ only in the step counter project to the same state. Actions are counted
 * by source location: TLC creates one {@link Action} per binding of an existential parameter, and
 * those are one disjunct of the specification.
 */
final class TlcStateMetrics implements IStateWriter {
    private final String projectedAwayVariable;
    private final long stateNodeCap;
    private final Map<Action, String> locations = new IdentityHashMap<>();
    private final Set<String> fired = new HashSet<>();
    private final Set<String> discovering = new HashSet<>();
    private final Set<Long> projected = new HashSet<>();

    private long initStates;
    private long distinctStates;
    private long transitions;
    private long depth;
    private long projectedDepth;
    private long maxStateNodes;
    private long maxCardinality;
    private long maxNesting;
    private boolean saturated;

    /**
     * @param projectedAwayVariable the variable a projected state omits
     * @param stateNodeCap the value nodes measured per state before the size walk stops
     */
    TlcStateMetrics(String projectedAwayVariable, long stateNodeCap) {
        this.projectedAwayVariable = Objects.requireNonNull(projectedAwayVariable, "projectedAwayVariable");
        this.stateNodeCap = stateNodeCap;
    }

    /** Adds the counts this writer measured. */
    synchronized void addTo(ExplorationMetrics.Builder metrics) {
        metrics.count(ExplorationCount.INIT_STATES, initStates)
                .count(ExplorationCount.DISTINCT_STATES, distinctStates)
                .count(ExplorationCount.GENERATED_STATES, initStates + transitions)
                .count(ExplorationCount.PROJECTED_STATES, projected.size())
                .count(ExplorationCount.DEPTH, depth)
                .count(ExplorationCount.PROJECTED_DEPTH, projectedDepth)
                .count(ExplorationCount.ACTIONS_FIRED, fired.size())
                .count(ExplorationCount.ACTIONS_DISCOVERING, discovering.size())
                .count(ExplorationCount.MAX_STATE_NODES, maxStateNodes)
                .count(ExplorationCount.MAX_CARDINALITY, maxCardinality)
                .count(ExplorationCount.MAX_NESTING, maxNesting)
                .saturated(saturated);
    }

    @Override
    public synchronized void writeState(TLCState state) {
        initStates++;
        found(state);
    }

    @Override
    public synchronized void writeState(TLCState state, TLCState successor, short successorStateIsNew, Action action) {
        transitions++;
        var location = action == null ? null : locations.computeIfAbsent(action, Action::getLocation);
        if (location != null) {
            fired.add(location);
        }
        if (successorStateIsNew == IsUnseen) {
            if (location != null) {
                discovering.add(location);
            }
            found(successor);
        }
    }

    @Override
    public void writeState(
            TLCState state, TLCState successor, short successorStateIsNew, Action action, SemanticNode pred) {
        writeState(state, successor, successorStateIsNew, action);
    }

    @Override
    public void writeState(TLCState state, TLCState successor, short successorStateIsNew) {
        writeState(state, successor, successorStateIsNew, (Action) null);
    }

    @Override
    public void writeState(
            TLCState state, TLCState successor, short successorStateIsNew, Visualization visualization) {
        writeState(state, successor, successorStateIsNew, (Action) null);
    }

    @Override
    public void writeState(
            TLCState state,
            TLCState successor,
            BitVector actionChecks,
            int from,
            int length,
            short successorStateIsNew) {
        writeState(state, successor, successorStateIsNew, (Action) null);
    }

    @Override
    public void writeState(
            TLCState state,
            TLCState successor,
            BitVector actionChecks,
            int from,
            int length,
            short successorStateIsNew,
            Visualization visualization) {
        writeState(state, successor, successorStateIsNew, (Action) null);
    }

    @Override
    public void close() {}

    @Override
    public String getDumpFileName() {
        return "";
    }

    /** Not a no-op: TLC reports states only to a writer that says it wants them. */
    @Override
    public boolean isNoop() {
        return false;
    }

    @Override
    public boolean isDot() {
        return false;
    }

    @Override
    public boolean isConstrained() {
        return false;
    }

    @Override
    public void snapshot() {}

    private void found(TLCState state) {
        distinctStates++;
        var level = (long) state.getLevel() - TLCState.INIT_LEVEL;
        depth = Math.max(depth, level);

        var shape = new TlcValueShape(stateNodeCap);
        var fingerprint = FP64.New();
        for (var variable : state.getVars()) {
            var name = variable.getName();
            var value = state.lookup(name);
            if (value == null) {
                continue;
            }
            shape.add(value);
            if (!projectedAwayVariable.equals(name.toString())) {
                fingerprint = FP64.Extend(fingerprint, name.toString());
                fingerprint = ((Value) value).fingerPrint(fingerprint);
            }
        }
        if (projected.add(fingerprint)) {
            projectedDepth = Math.max(projectedDepth, level);
        }
        maxStateNodes = Math.max(maxStateNodes, shape.nodes());
        maxCardinality = Math.max(maxCardinality, shape.cardinality());
        maxNesting = Math.max(maxNesting, shape.nesting());
        saturated |= shape.saturated();
    }
}
