# Graph-based testing

!!! warning "Proposed"
    `--how=gbt` is designed in [ADR 0023](../decisions/0023-graph-based-testing.md)
    and not implemented yet. This page describes the planned interface.

A graph-based campaign generates modules whose control structure is a directed graph.
A variable `pc` holds the current node. Each node assigns new values to the data
variables, and each edge runs its source node's assignments and moves `pc` to its
target. The invariant is `pc /= k` for one target node k. Because every edge is
enabled, FuzzTLA knows from the graph whether a checker must report a counterexample,
and how long it must be. A checker that disagrees has a defect, even when the other
checker agrees with it.

## 1. Setting up a corpus

```sh
./bin/fuzztla init --corpus corpus-gbt
# edit corpus-gbt/config.toml as below
./bin/fuzztla run --how=gbt --corpus corpus-gbt
```

```toml
[generator]
kind = "graph"                # required by --how=gbt
max_variables = 3
max_steps = 5                 # the step bound B

[workflow]
checkers = ["tlc", "apalache"] # one checker is enough, see section 3

[graph]
max_graph_nodes = 6
max_successors = 3
max_guards = 2                # used only with rules
# rules = { module = "Rewrites", classpath = ["../libraries/rewrites"] }
# weights = { UnionSelf = 2 }
```

- **The technique is fixed.** The first run records `gbt` in `.technique`; later runs
  must pass `--how=gbt`.
- **Mutation works as under `pbt`.** `[mutator] feedback_ratio` sets the share of
  byte mutants of `04quality-pass` entries; random inputs fill the rest.
- **Guards need rules.** Without `rules`, edges have no guards (section 4).

## 2. What the checker sees

`fuzztla print --spec` shows the module. Besides it, `print` shows the graph, the
target k and the expected outcome.

```tla
VARIABLES var0, var1, pc, step

Node0 == var0' = var0 + 1 /\ UNCHANGED var1
Node1 == var1' = var1 \cup {var0} /\ UNCHANGED var0
Node2 == var0' = 0 /\ var1' = {}

Init == var0 = 3 /\ var1 = {1} /\ pc = 0 /\ step = 0

Next ==
  \/ step < 5 /\ pc = 0 /\ Node0 /\ pc' = 1 /\ step' = step + 1
  \/ step < 5 /\ pc = 1 /\ Node1 /\ pc' = 1 /\ step' = step + 1
  \/ step < 5 /\ pc = 1 /\ Node1 /\ pc' = 0 /\ step' = step + 1
  \/ UNCHANGED <<var0, var1, pc, step>>

Inv == pc /= 2
```

- Node 0 is initial; `pc` ranges over `0..N-1`.
- `Node<u>` assigns some data variables with `v' = e` and leaves the others unchanged.
  The right-hand sides are generated expressions, as under `pbt`.
- There is one disjunct of `Next` per edge. The edges here are 0 → 1, 1 → 1 and
  1 → 0. Node 2 has no incoming edge.

## 3. Verdicts

Let dist(k) be the length of a shortest path from node 0 to k, and B the step bound.

| Target | Expected verdict | Expected trace length |
| --- | --- | --- |
| dist(k) ≤ B | counterexample | dist(k) for TLC, at least dist(k) for the others |
| dist(k) > B, or unreachable | pass | — |

An entry passes the aggregator when the checkers agree, as under `pbt`, and every
checker reports the expected verdict or fails. The example expects a pass.

- **A wrong verdict is a defect.** A pass on a reachable target, or a counterexample
  on an unreachable one, fails the entry, whatever the other checker reports.
- **Failures are allowed.** A partial expression, such as `CHOOSE x \in {} : TRUE`,
  makes TLC fail on any input; conformance and the known-defect signatures handle it.
- **One checker suffices.** With `checkers = ["apalache"]`, the expected outcome is
  the oracle.

## 4. Tautological guards

With `rules` set, each edge may carry guards that are always true, built from the
rules of a [rewrite-rule module](metamorphic-testing.md#5-writing-rules). The rule
`UnionSelf(S) == S = S \union S` with `S := var1` yields the guard below:

```tla
\/ step < 5 /\ pc = 1 /\ var1 = var1 \union var1 /\ Node1 /\ pc' = 0 /\ step' = step + 1
```

A checker that evaluates a guard to `FALSE` disables the edge, and then reports a
pass or a longer trace than expected. Either the checker is wrong or the rule is
unsound; check the rule first.

## 5. Limitations

- Only `pc` and trace lengths are predicted. A wrong value of a data variable is
  found only through conformance or a guard.
- `--how=mt` does not rewrite `graph` entries.
- Graph modules have no temporal property.

## Background

[ADR 0023](../decisions/0023-graph-based-testing.md) records the design. Rules come
from [ADR 0017](../decisions/0017-rewrite-rule-library.md), and the step bound from
[ADR 0007](../decisions/0007-levels-and-temporal-properties.md).
