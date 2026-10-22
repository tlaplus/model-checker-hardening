# 0023: Graph-based testing

**Authors:** Igor Konnov and Claude

**Status:** Proposed

**Date:** 2026-10-22

**Implementation.** None. The design is written against FuzzTLA `ed5c97e`. The
[manual][manual] describes the proposed user interface.

## Context

Under `--how=pbt`, the only oracle is conformance ([fuzzing-workflows.md §1.3][workflows]):
TLC and Apalache must agree. A defect that both checkers share passes, and a module
whose verdict nobody can predict cannot test a single checker. Metamorphic testing
([ADR 0016][adr-0016]) checks one checker against a rewrite of its input, but the state
space of a generated `Next` ([ir-generators.md §9][generators]) remains opaque: which
states are reachable is known only from running a checker.

Graph-based testing (GBT) makes part of the state space known by construction. The
input bytes decode to a directed graph (V, E), the control structure of a transition
system. A state variable `pc` holds the current node. Each node carries an action: in
stage 1, a conjunction of assignments to the data variables, whose right-hand sides
are generated expressions as under `pbt`. Each edge is a disjunct of `Next` that runs
its source node's action and moves `pc` to its target. When every edge leaving the
current node is enabled, the reachable values of `pc` follow from the graph alone.
The decoder therefore predicts the verdict of the invariant `pc /= k` for a target
node k, which gives an oracle that needs no second checker.

Stage 2 conjoins tautological Boolean tests to the edges. A tautology does not change
the prediction, so a checker that evaluates one to `FALSE` disables an edge and
contradicts the predicted verdict.

**Scope.** This ADR decides the technique, the encoding, the generated module, the
oracle, the stage-2 guards and their configuration. Graph entries under `--how=mt`,
temporal properties and a graph-specific quality-gate policy are out of scope.

### Notation

| Symbol | Meaning |
| --- | --- |
| N | number of nodes; V = {0, ..., N - 1}, node 0 is initial |
| E ⊆ V × V | edges; `succ(u)` is the successor set of u |
| k ∈ V | target node, named by the invariant `pc /= k` |
| B | step bound, `[generator] max_steps` ([ir-generators.md §9.3][generators]) |
| dist(u) | length of a shortest path from 0 to u in (V, E), ∞ if none |

## Decision

### 1. Technique and input kind

GBT is a technique and its inputs are a new input kind. Both axes exist already:
`corpus.Technique` chooses the oracle and the payload layout, and `gen.InputKind`
chooses the decoder ([ADR 0016][adr-0016] §1).

- `Technique.GBT("gbt", Oracle.GRAPH)` is selected by `fuzztla run --how=gbt`. The
  first run records `gbt` in `.technique`, as for `mt`.
- `InputKind.GRAPH("graph")` names the graph decoder. `workflow.spec.SpecDecoders`
  maps it, so every stage regenerates a graph entry from its stored bytes.
- `--how=gbt` requires `[generator] kind = "graph"`; `run` refuses any other kind.
- A `graph` entry in a `pbt` corpus is legal and is judged by conformance alone.
- The metamorphic registry rejects the `graph` kind with `InputRejectedException`
  until a later ADR defines rewrites of graph modules.

### 2. Generated module

The decoder emits the following module shape. In the example, `var0 : Int` and
`var1 : Set(Int)`, N = 3, E = {(0, 1), (1, 1), (1, 0)}, k = 2 and B = 5.

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

Node 2 has no incoming edge, so dist(2) = ∞ and the expected verdict is `pass`.

- **Variables.** The data variables are drawn as under `pbt` (§9.1, item 1). `pc : Int`
  and `step : Int` are appended. Right-hand sides see the data variables and the
  auxiliary operators, but neither `pc` nor `step`: a variable assigned from either
  changes with control alone, for the reason §9.1 gives for `step`.
- **Auxiliary operators.** As under `pbt`: state-free `Op<N>` definitions.
- **Node actions.** `Node<u>` is the `pbt` leaf shape restricted to deterministic
  assignments: a non-empty subset of the data variables is assigned by `v' = e`, and
  the rest are listed in one `UNCHANGED`. The leaf keeps its byte-free non-stuttering
  repair (§9.2). `Node<u>` is a `GeneratedActionOperator` whose `ActionEffect` is the
  data variables. A node is emitted even when no edge leaves it.
- **Initial predicate.** One `v = e` per data variable, then `pc = 0 /\ step = 0`.
  `v \in S` is excluded: an empty `S` empties `Init` and voids the prediction.
- **Next-state action.** One disjunct per edge (u, v), in node order and then in
  successor order:
  `step < B /\ pc = u /\ Node<u> /\ pc' = v /\ step' = step + 1`. `FuzzInputModule`
  closes it with the stuttering disjunct, unchanged. A node without successors
  only stutters; deadlock detection stays off.
- **Invariant.** `Inv == pc /= k`. No other invariant is generated: a conjoined `pbt`
  invariant could produce a counterexample before `pc` reaches k, which would weaken
  the prediction to one direction.
- **No temporal property** in either stage.

The decoder returns a `gen.graph.GeneratedGraph`: the `GeneratedSpec` above, the
`TransitionGraph` (N, the successor lists, the distances) and k. The two invariants of
`GeneratedSpec` hold: `Init` constrains every variable, and every edge disjunct accounts
for every variable exactly once before the step update. `FuzzInputModule.create`
therefore assembles a graph entry without change. `SpecArtifact` additionally carries
the expected outcome (section 4).

### 3. Byte encoding

A new package `gen.graph` holds the decoder, its section enum `GraphSection`, the
records above, and `GraphLimits`. Its decoder satisfies requirements 1–6 of
[ir-generators.md §1][generators]. It reuses `engine.IrExprGenFactory`,
`engine.GenerationContext`, `engine.IrTypeGenFactory` and the auxiliary-operator
decoder of `engine.IrSpecGeneratorEngine`. `engine.ActionShapeGenFactory` gains an
assignment-only leaf, which draws no `nondeterministic()` marker.

The payload splits into weighted sections through `Draw.slice`, as `ModuleSection`
does:

1. **Header.** N by `drawLong(1, max_graph_nodes)`, then k by `drawIndex(N)`.
2. **Variables.** As `ModuleSection.VARIABLES`.
3. **Auxiliary operators.** As `ModuleSection.AUXILIARY_OPERATORS`.
4. **Init.** One assignment per data variable.
5. **Nodes.** Split into N equal slices. Slice u decodes `Node<u>` with a fresh node
   budget, then `succ(u)` as a terminated list of at most `max_successors` indices,
   each `drawIndex(N)`. Duplicates collapse, byte-free; self-loops stay.
6. **Guards** (stage 2). Present only when `[graph] rules` is set, so stage-1 entries
   decode alike after stage 2 lands, as the property section does (§9.1). Split into
   N slices aligned with the nodes; section 6 specifies a slice.

Exhausted input decodes to N = 1, k = 0, one data variable and a node without
successors: the initial state violates `Inv`.

**Deviation from [ir-generators.md §1][generators].** `graph` is a third encoding,
independent of `expr` and `module`. Requirement 3 holds within a node slice; a
mutation of N re-slices every node.

### 4. Expected outcome

The decoder computes dist by breadth-first search from node 0 and predicts:

| Condition | Expected verdict | Expected `traceLength` |
| --- | --- | --- |
| dist(k) ≤ B | `counterexample` | dist(k) |
| dist(k) > B | `pass` | — |

The prediction is sound because, in stage 1, every edge leaving `pc = u` is enabled
while `step < B`: `Init` has a state, every assignment is `v' = e`, and an edge has
no `\E` parameter and no guard besides `pc = u` and the step guard. A partial
right-hand side, such as `CHOOSE x \in {} : TRUE`, does not disable an edge either:
TLC stops with an error, and Apalache assigns an arbitrary value.

`traceLength` is the metric of [ADR 0008][adr-0008] that both checkers already
record for a counterexample, 0 for a violation in an initial state. Breadth-first
search reaches a state with `pc = k` first at level dist(k), so TLC with one worker
reports exactly dist(k). No path is shorter, so every checker reports at least dist(k).

The workflow translates the prediction into a `corpus.ExpectedOutcome(CorpusVerdict
verdict, OptionalInt traceLength)`, so `corpus` depends on no type of `gen.graph`.

### 5. Graph oracle

`Oracle.GRAPH` passes an entry when all of the following hold:

1. The checkers agree, as under `CONFORMANCE`, including the state counts of
   [ADR 0019][adr-0019].
2. Every checker reports the expected verdict or `fail`. A `pass` on a reachable
   target, or a `counterexample` on an unreachable one, fails the entry.
3. Every counterexample has `traceLength` ≥ dist(k), and the `tlc` stage's equals
   dist(k).

`fail` is admitted in rule 2 because a partial term makes TLC fail on any input, and
conformance already compares it with Apalache. Rule 3 states no equality for Apalache
or TLC-n, whose search order this ADR does not rely on; phase 3 (section 9) measures
whether Apalache's bounded search reports dist(k) as well.

`Oracle.judge` takes the expected outcome as a second argument, which `CONFORMANCE`
and `METAMORPHIC` ignore. The aggregator obtains it by replaying the entry through
`SpecDecoders`, as the quality gate replays inputs; storage keeps only bytes. Because
the oracle needs no second checker, `[workflow] checkers = ["tlc"]` or
`["apalache"]` is a meaningful GBT corpus, as under `mt` ([ADR 0016][adr-0016] §5).

### 6. Tautological guards (stage 2)

Stage 2 conjoins up to `max_guards` tautologies to each edge, after `pc = u`:

```tla
\/ step < 5 /\ pc = 1 /\ var1 = var1 \union var1 /\ Node1 /\ pc' = 0 /\ step' = step + 1
```

A guard is an instance of a rule `F(x, y) == A = B` or `F(x, y) == A <=> B` of the
rule library of [ADR 0017][adr-0017], whose contract makes `A = B` valid. Its
parameters are drawn by `engine.OperandGenerator` at their types, in the edge's state
scope, so a guard reads the data variables. `gen.rewrite.RuleInstantiation` builds
the instance. The example instantiates `UnionSelf(S) == S = S \union S` of the shipped
`Rewrites.tla` with `S := var1`. Rules of action or temporal level, such as
`UnchangedPrime`, are not offered.

A guard slice of section 3 decodes, for each edge leaving u in successor order, a
terminated list of guards; each guard is a rule index, two bytes as in the rewriter,
followed by its operands. An exhausted slice decodes no guard.

The prediction of section 4 is unchanged. A guard that a checker evaluates to `FALSE`
disables its edge; when that edge lies on every shortest path to k, the checker
reports `pass` or a longer trace, and the oracle fails the entry. A violation
indicates a checker defect or an unsound rule, which triage tells apart as under
`mt`.

### 7. Configuration

A new `[graph]` table, parsed into `config.GraphConfig` and passed to the decoder as
`gen.GraphLimits`:

| Key | Default | Meaning |
| --- | --- | --- |
| `max_graph_nodes` | `6` | Maximum N. |
| `max_successors` | `3` | Maximum out-degree of a node. |
| `max_guards` | `2` | Maximum tautological guards per edge (stage 2). |
| `rules` | absent | Rule module of the guards; absent disables stage 2. |
| `weights` | `{}` | Rule weights, as in `[metamorphic]`. |

`[metamorphic]` already defines `rules` and `weights` (`config.MetamorphicConfig`).
Both tables use one shared record, `config.RuleLibraryConfig`, and one preparation,
`workflow.library.RuleLibraryPreparation`; the corpus file `.rewrite-library` pins the
prepared rules. The data variables, the step bound and the expression limits come
from `[generator]`.

### 8. Pipeline

Everything else is reused:

- **Candidate sources.** `PbtCandidates` draws random bytes and `MutantCandidates`
  mutates `04quality-pass` parents. There is no adoption source.
- **Admission.** Richness score, request-frame limit and known-defect signatures
  apply to the generated expressions, as for `module`.
- **Stages.** The parser and checker stages see an ordinary assembled module.
- **Quality gate and export.** Unchanged. `export-db` records the technique `gbt`.
  `fuzztla print` additionally shows N, E, k and the expected outcome.

### 9. Implementation phases

1. `Technique.GBT`, `InputKind.GRAPH`, `gen.graph`, `[graph]` without rules, and the
   conformance oracle.
2. `Oracle.GRAPH` with rules 1 and 2 of section 5, and `ExpectedOutcome`.
3. Rule 3 of section 5, after a probe measures `traceLength` on reachable targets for
   each checker.
4. Tautological guards, the shared `RuleLibraryConfig`, and the guard section.

Each phase updates [ir-generators.md][generators] and
[fuzzing-workflows.md][workflows] when it lands, and types carrying a new `@Key`
get an `@ArchRole`.

## Alternatives considered

- **An input kind under `pbt` only.** It would need no new technique, but conformance
  remains the only oracle. A `graph` entry under `pbt` remains possible (section 1).
- **Reinterpreting `module` payloads under `gbt`.** One kind name would have two
  encodings, and the technique, not the entry, would choose the decoder, which
  contradicts `SpecDecoders`.
- **Running the target node's action on an edge.** Equivalent in expressiveness, but
  the source node matches PlusCal's translation, where a label's block runs before
  its `goto`.
- **Edges with their own payloads.** More actions per byte, but node actions keep the
  graph small and give the out-edges of a node one shared definition.
- **A generated invariant conjoined with `pc /= k`.** It adds expression coverage to
  `Inv`, but a counterexample would no longer be predicted, only a `pass` refuted.
- **String `pc` labels.** They exercise string values, but integers keep `pc` uniform
  with `step`; a later ADR can add labels if the integer encoding hides defects.
- **Storing the expected outcome in admission metadata.** It saves a replay in the
  aggregator, but stores a value derived from the bytes, which could disagree with
  the decoder after a change.
- **Fixed tautology schemas** such as `P \/ ~P` and `e = e`. They need no rule
  library, but the library already supplies valid, typed, weighted equalities, and
  schemas this simple are prone to short-circuit evaluation.

## Consequences

- **Verdicts are self-checking.** A single-checker corpus has an oracle, and a defect
  shared by TLC and Apalache fails rule 2.
- **Partial terms cost no new false positives.** In stage 1 they make TLC fail and
  Apalache continue, which conformance already reports and the known-defect
  signatures already cover. In stage 2, Apalache may give the two copies of a partial
  term in `A = B` different values ([ADR 0016][adr-0016]), so a guard can be `FALSE`
  there; the same signatures apply.
- **Data values are not predicted.** The oracle constrains `pc` and trace lengths;
  defects in evaluating right-hand sides surface only through conformance and through
  guards.
- **The share of expected passes depends on the graph.** With N ≤ 6 and B = 5, most
  targets are reachable; the decoder does not balance the two outcomes.
- **`pc` counts as state change.** ADR 0008 projects out only `step`, so an entry
  whose data never changes still has projected depth. Whether to project out `pc`
  too is left open until a GBT corpus shows how the gate ranks such entries.
- **Graph entries cannot be rewritten yet.** `mt` rejects the `graph` kind.

[adr-0008]: 0008-exploration-metrics.md
[adr-0016]: 0016-metamorphic-testing.md
[adr-0017]: 0017-rewrite-rule-library.md
[adr-0019]: 0019-state-count-agreement.md
[generators]: ../architecture/ir-generators.md
[manual]: ../manual/graph-based-testing.md
[workflows]: ../architecture/fuzzing-workflows.md
