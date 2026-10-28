# Graph-based testing

A graph-based campaign generates modules whose control structure is a directed graph
of 20 to 32 nodes. A spine `0 -> 1 -> ... -> N-1` makes it up to N - 1 steps deep, and
extra edges add loops, cycles and short skips. A variable `pc` holds the current node. Each node assigns new values to the data
variables, and each edge runs its source node's assignments and moves `pc` to its
target. The invariant is `pc /= k` for one target node k. Because every edge is
enabled, FuzzTLA knows from the graph whether a checker must report a counterexample,
and how long it must be. A checker that disagrees has a defect, even when the other
checker agrees with it. Optionally, edges carry tautological guards (section 4), which
a correct checker always evaluates to `TRUE`.

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

[workflow]
checkers = ["tlc", "apalache"] # one checker is enough, see section 3

[workflow.inputs]
known_defects = ["../signatures/all-defects.toml"]

[workflow.apalache]
timeout_sec = 60              # deep spines unroll 10 to 31 steps

[graph]
min_graph_nodes = 20
max_graph_nodes = 32
max_extra_successors = 2
# max_steps = 32              # cap on each input's own step bound
rules = { module = "Rewrites", classpath = ["../libraries/rewrites"] }  # edge guards, section 4
max_guards = 2
```

- **The technique is fixed.** The first run records `gbt` in `.technique`; later runs
  must pass `--how=gbt`. `run` refuses another `kind`.
- **Mutation works as under `pbt`.** `[mutator] feedback_ratio` sets the share of
  byte mutants of `04quality-pass` entries; random inputs fill the rest.
- **`[graph]` may be left out.** Its keys then take the defaults above. The data
  variables and the expressions use the `[generator]` keys. Each input draws its own
  step bound, so `[generator] max_steps` does not apply.
- **Give Apalache some margin.** Apalache unrolls one step per transition, and a
  spine is checked to a bound of 10 to 31 steps. In a first campaign it took 2 s at
  the median and 45 s at most; 1 entry in 300 exceeded the default 30 s.
- **Use the full defect database.** A node action runs every time its node is
  visited, so TLC fails on a partial term, such as a function applied outside its
  domain, more often than in a `pbt` corpus. With `all-defects.toml`, TLC failed on
  9% of the entries of a campaign, against 45% with the shipped `known-defects.toml`
  ([Known-defect signatures](known-defect-signatures.md)).

## 2. What the checker sees

`fuzztla print` shows the graph, the target k and the expected outcome before the
module; `fuzztla print --spec` shows the module alone:

```text
nodes: 23
edges: 0 -> 1, 1 -> 2, 2 -> 3, ..., 21 -> 22, 22 -> 5, 22 -> 9
target: 19 (distance 19, step bound 13)
expected: pass
module:
---- MODULE FuzzInput ----
...
```

A small graph shows the shape of the module (simplified, three nodes, target 2, bound 2):

```tla
VARIABLES var0, var1, pc, step

Node0 == var0' = var0 + 1 /\ UNCHANGED var1
Node1 == var1' = var1 \cup {var0} /\ UNCHANGED var0
Node2 == var0' = 0 /\ var1' = {}

Init == var0 = 3 /\ var1 = {1} /\ pc = 0 /\ step = 0

Next ==
  \/ step < 2 /\ pc = 0 /\ Node0 /\ pc' = 1 /\ step' = step + 1
  \/ step < 2 /\ pc = 1 /\ Node1 /\ pc' = 2 /\ step' = step + 1
  \/ step < 2 /\ pc = 1 /\ Node1 /\ pc' = 1 /\ step' = step + 1
  \/ step < 2 /\ pc = 1 /\ Node1 /\ pc' = 0 /\ step' = step + 1
  \/ UNCHANGED <<var0, var1, pc, step>>

Inv == pc /= 2
```

- Node 0 is initial; `pc` ranges over `0..N-1`. The spine edges here are 0 → 1 and
  1 → 2, and the extra edges 1 → 1 and 1 → 0.
- `Node<u>` assigns some data variables with `v' = e` and leaves the others unchanged.
  The right-hand sides are generated expressions, as under `pbt`, and read neither
  `pc` nor `step`.
- There is one disjunct of `Next` per edge, and each is guarded by the input's own
  step bound.
- The target lies in the upper half of the spine, and the bound between half the
  spine and its length. When the target is farther than the bound, the expected
  verdict is a pass.

## 3. Verdicts

Let dist(k) be the length of a shortest path from node 0 to k, and B the step bound.

| Target | Expected verdict | Expected trace length |
| --- | --- | --- |
| dist(k) ≤ B | counterexample | dist(k) for TLC and Apalache, at least dist(k) for TLC-n |
| dist(k) > B, or unreachable | pass | — |

An entry passes the aggregator when the checkers agree, as under `pbt`, and every
checker reports the expected verdict or fails. The small example expects a
counterexample of 2 steps; the 23-node graph above expects a pass, because its target
is 19 steps away and its bound is 13.

- **A wrong verdict is a defect.** A pass on a reachable target, or a counterexample
  on an unreachable one, fails the entry, whatever the other checker reports.
- **A wrong trace length is a defect.** A counterexample shorter than dist(k), or a
  TLC or Apalache counterexample of another length, fails the entry. Both search for
  a shortest counterexample; TLC with several workers need not find one.
- **Failures are allowed.** A partial expression, such as `CHOOSE x \in {} : TRUE`,
  makes TLC fail on any input; conformance and the known-defect signatures handle it.
- **One checker suffices.** With `checkers = ["apalache"]`, the expected outcome is
  the oracle.

## 4. Tautological guards

With `[graph] rules`, each edge conjoins up to `max_guards` guards after `pc = u`. A
guard is an instance of a rule `F(x, y) == A = B` of a
[rewrite-rule module](metamorphic-testing.md#5-writing-rules), with every parameter
drawn as an expression over the data variables. The rule `UnionSelf(S) == S = S \union
S` with `S := var1` yields the guard below:

```tla
\/ step < 5 /\ pc = 1 /\ var1 = var1 \union var1 /\ Node1 /\ pc' = 0 /\ step' = step + 1
```

- **The expected outcome does not change.** Every rule is valid, so a guard holds in
  every state. A checker that evaluates one to `FALSE` disables the edge, and then
  reports a pass or a longer trace than expected.
- **Some rules do not guard.** A rule with an action-level operator, such as
  `UnchangedPrime`, a higher-order parameter, such as `P(_)` in `ForallNotExists`, or
  a helper definition is left out. `[graph] weights` reweighs the rest as
  `[metamorphic] weights` does.
- **The rules are part of the encoding.** Guards take a section of the input of their
  own, so setting `rules` changes how every graph input decodes. The first run records
  the rule module in `.rewrite-library`, and later runs refuse another one.
- **A `pbt` or `mt` corpus with graph entries** decodes them with the same guards.
- **Guards start at node 0.** The guard bytes, about a fifth of the input, are read
  edge by edge from node 0, at most 8 bytes per guard, so a short input guards the
  first edges of every path to the target. In a campaign whose inputs had 31 bytes at
  the median, 97% of the entries had a guard, but at most 8.

## 5. Triage

Most entries of `03aggregator-fail` are conformance differences, as in a `pbt`
corpus: TLC fails on a partial term where Apalache picks a value. An entry is a
graph-oracle failure when the checkers report the same verdict, or the same failure
status, and `print` shows another expected outcome. Compare the checkers' verdicts
and `traceLength` (`print --envelope`) with the `expected:` line.

In a guarded corpus, such an entry points at a guard on a shortest path to the target:
a checker evaluated it to `FALSE`. Evaluate the guard alone, in the state the trace
reaches before the edge, to tell a checker defect from an unsound rule. A partial term
in a guard, such as a function applied outside its domain, can make Apalache pick
different values for its copies in A and B, as under metamorphic testing; the
known-defect signatures cover those cases.

## 6. Limitations

- Only `pc` and trace lengths are predicted. A wrong value of a data variable is
  found only through conformance, or through a guard that reads it.
- Forward skips shorten some paths: in a campaign, counterexamples were 11 to 30
  steps long, and one in four was 20 or more.
- `--how=mt` rewrites a `graph` entry as a module and ignores its prediction
  ([Metamorphic testing](metamorphic-testing.md#1-setting-up-a-corpus)).
- Graph modules have no temporal property.

## 7. Selection of parents

The quality gate picks the parents of mutation ([Generations and
mutation](mutation.md)). It would rank graph entries by the length of their walks,
because `pc` changes on every step, so a `gbt` corpus measures and ranks them
differently ([ADR 0024](../decisions/0024-graph-action-coverage.md)):

- **`pc` is projected out.** TLC measures graph entries on their data variables, so
  `projectedStates` counts data valuations, and a new count, `dataActions`, counts the
  edges whose node action produced new data.
- **`gbt` ranks by action coverage.** The gate prefers entries with more
  `dataActions`, then more data valuations, then deeper data change. `pbt` and `mt`
  keep ranking by depth.
- **Constant data is shallow.** An entry whose node actions never change the data
  matches `counter_only_progress` and is never mutated.

In a campaign of 3000 entries, the selected parents had 5 distinct node actions at the
median, against 2 under the depth ranking, and 70% had more than one data variable,
against 1%. Deep targets became rare: no entry had a target 30 or more steps away,
since only mutation of deep parents produced them.

## Background

[ADR 0023](../decisions/0023-graph-based-testing.md) records the design;
[ADR 0024](../decisions/0024-graph-action-coverage.md) decides how the gate selects
graph parents.
[ir-generators.md §12](../architecture/ir-generators.md#12-graph-generation) specifies
the encoding. The step bound comes from
[ADR 0007](../decisions/0007-levels-and-temporal-properties.md).
