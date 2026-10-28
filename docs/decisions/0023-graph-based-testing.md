# 0023: Graph-based testing

**Authors:** Igor Konnov and Claude

**Status:** Implemented

**Date:** 2026-10-22

**Implementation.** Stage 1, phases 1–3 of section 9, is implemented on the branch
`igor/graph-based-testing2` (FuzzTLA `71f911a`, TLC `d2e1472`, Apalache 0.62.3
`3eb15b2`). The [manual][manual] describes it for users. Implementation changed these
details, recorded where they apply:

- The decoder lives in `gen.engine`, whose factories are package-private; `gen.graph`
  holds the model types, and `GraphLimits` is a component of `IrGenerationConfig`
  (sections 3, 7).
- The header is read before the weighted split; the successor lists have a section of
  their own; the target is node 0 only in a one-node graph (section 3).
- Graphs have a spine `0 -> 1 -> ... -> N-1` of 20 to 32 nodes, extra edges reach at
  most two nodes ahead, and each input draws its own step bound (sections 2, 3, 7).
  The first probe found random successor lists too shallow: the targets lay at most
  6 steps from node 0, and Apalache rejected graphs without edges
  ([Probes](#probes)).
- `Oracle.judge` takes the results per checker stage, and whether a checker reports a
  shortest counterexample is a fact on its `CorpusStage`. The probe showed that
  Apalache, like TLC with one worker, does (section 5).

Stage 2, phase 4, is implemented on the branch `igor/gbt-stage2` (FuzzTLA `e3fa311`,
TLC `d2e1472`, Apalache 0.62.3 `3eb15b2`); Probes 3 and 4 measured it. It changed
these details:

- The guard section is the last `GraphSection`, `GUARDS`, of weight 4. Without rules
  its weight is 0, so the other slices, and every stage-1 entry, decode as before
  (section 3).
- The guard section is one cursor over the edges in edge order, not N slices aligned
  with the nodes, and each guard reads at most 8 bytes of it. Probe 3 found the
  slices left 81% of the paths to a reachable target unguarded (section 6).
- For the same reason, the edge section is one cursor in node order, and the node
  section is split into slices filled in node order, of at least 8 bytes each
  (section 3). This changed the encoding of every graph entry, so corpora from
  before it decode to other graphs. Probe 5 compares the two layouts.
- A rule guards an edge only when it has a positive weight, no action-level operator,
  no higher-order parameter, which no drawn value fills, and no helper, which the
  module does not link. `gen.rewrite.Tautologies` implements `gen.engine.EdgeGuards`,
  so `gen.engine` still does not depend on `gen.rewrite` (section 6).
- `RewriteRule` keeps its formula `A = B` or `A <=> B`, and
  `RuleInstantiation.instantiateFormula` instantiates both sides at once, so labels
  and `LET` definitions that a value repeats across them are renamed apart. A
  parameter that the formula puts under one of its binders draws no label (section 6).
- The open type variables of a guard are instantiated through a template of the types
  that hold them, not bare variables, so the row of a record or variant stays a row
  (section 6).
- `[graph] rules` is read into `FuzzTlaConfig.graphGuards`, a `RuleLibraryConfig`;
  `max_guards` is a component of `GraphLimits`. A `pbt` corpus prepares the guard rules
  as well, since it may hold graph entries; `mt` first ignored them (section 7).

**Amendment: graph bases under `mt`.** The branch `igor/mt-graph` (FuzzTLA `8f1fedd`,
TLC `56d37a9`, Apalache 0.62.3 `3eb15b2`) revises section 1
and the scope below, which deferred graph entries under `--how=mt` to a later ADR.
The change is small enough to record here:

- `Technique.MT` generates every kind. The metamorphic registry decodes a graph base
  and rewrites the `GeneratedSpec` it holds, so a graph entry is a `module` relation
  ([ADR 0016][adr-0016] §3), judged by `Oracle.METAMORPHIC`. The graph's prediction
  does not apply to the relation and is not used.
- Every technique prepares `[graph] rules`, so an `mt` corpus decodes a graph base
  with the guards that a `pbt` or `gbt` corpus decodes it with. An `mt` corpus pins
  them in `.rewrite-library` after the rewrite manifest, under a `graph-guards`
  line. Without guards, the record is the rewrite manifest alone, as before.
- `metamorphic.base_corpus` may be a `gbt` corpus. Adoption also compares the base's
  `.rewrite-library`, which holds its guards, with the run's guard manifest.
- The relation does not project out `pc`, and `mt` keeps the depth ranking
  ([ADR 0024][adr-0024] §2). The gate therefore ranks graph relations by the length of
  their walks.

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
oracle, the stage-2 guards and their configuration. Graph entries under `--how=mt`
(see the amendment above), temporal properties and a graph-specific quality-gate
policy are out of scope.

### Notation

| Symbol | Meaning |
| --- | --- |
| N | number of nodes; V = {0, ..., N - 1}, node 0 is initial |
| E ⊆ V × V | edges; `succ(u)` is the successor set of u |
| k ∈ V | target node, named by the invariant `pc /= k` |
| B | step bound of the input, drawn in [⌊N/2⌋, N - 1] and capped by `[graph] max_steps` ([ir-generators.md §9.3][generators]) |
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
  Each `Technique` constant names the kinds it generates: `pbt` all, `mt` `expr` and
  `module`, `gbt` `graph`.
- A `graph` entry in a `pbt` corpus is legal and is judged by conformance alone.
- The metamorphic registry rejects the `graph` kind with `InputRejectedException`
  until a later ADR defines rewrites of graph modules. *Revised by the amendment
  above: `mt` rewrites a graph base as its module.*

### 2. Generated module

The decoder emits the following module shape. In the example, `var0 : Int` and
`var1 : Set(Int)`, N = 3, the spine edges are (0, 1) and (1, 2), the extra edges
(1, 1) and (1, 0), k = 2 and B = 2. Real graphs have 20 to 32 nodes.

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

dist(2) = 2 ≤ B, so the expected verdict is a counterexample of 2 transitions; with
B = 1 it would be `pass`.

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
  `step < B /\ pc = u /\ Node<u> /\ pc' = v /\ step' = step + 1`, with the input's
  own B. `FuzzInputModule` closes it with the stuttering disjunct, unchanged. The
  spine leaves node 0, so `Next` has an edge disjunct: Apalache's assignment finder
  rejects `FALSE \/ UNCHANGED vars`. The last node may have no successor and then
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

`engine.IrGraphGeneratorEngine` decodes a graph input, and `engine.GraphSection` is its
section enum; both live beside the factories they use, which are package-private. The
model types `TransitionGraph` and `GeneratedGraph` are in `gen.graph`, and
`gen.GraphLimits` bounds the graph. The decoder satisfies requirements 1–6 of
[ir-generators.md §1][generators]. It shares `engine.ModulePrelude` (variables and
auxiliary operators) with the module decoder, and `engine.ActionShapeGenFactory`
gains an exact leaf, which draws no `nondeterministic()` marker.

1. **Header.** N by `drawLong(min_graph_nodes, max_graph_nodes)`; the target k by
   `drawLong(⌈N/2⌉, N - 1)`, in the upper half of the spine (k = 0 for N = 1); and the
   bound B by `drawLong(⌊N/2⌋, N - 1)`, both ends capped by `[graph] max_steps`. A
   target with dist(k) > B predicts a pass, so the two outcomes are about equally
   frequent, although every node is reachable along the spine. The header comes before
   the split, because a weighted share for its three bytes would grow with the input.

The rest of the payload splits into the weighted sections of `GraphSection`, through
`engine.WeightedSlices`, the split of `ModuleSection`:

2. **Variables** (weight 1). As `ModuleSection.VARIABLES`.
3. **Auxiliary operators** (2). As `ModuleSection.AUXILIARY_OPERATORS`.
4. **Init** (2). One assignment per data variable.
5. **Edges** (2). One cursor, read in node order. `succ(u)` is the spine successor
   u + 1, byte-free, followed by a terminated list of at most `max_extra_successors`
   extra successors, each `drawLong(0, min(u + 2, N - 1))`, a back edge, a self-loop
   or a forward skip of at most two nodes. Duplicates collapse, byte-free. A one-node
   graph's spine is the self-loop `0 -> 0`. Bytes left after node N - 1 are unused.
6. **Nodes** (8). N slices filled in node order, each of `max(8, L / N)` bytes of the
   section's L while bytes remain, the last taking the remainder; slice u decodes
   `Node<u>` with a fresh node budget.
7. **Guards** (weight 4). Present only when `[graph] rules` is set, so stage-1 entries
   decode alike, as the property section does (§9.1): without rules the section's
   weight is 0. One cursor over the edges in edge order; section 6 specifies it.

The successor lists have their own section, rather than following the action in a
node's slice as first designed: the action's expressions consumed the slice, and
almost no graph had an edge. Separate sections also keep a mutation of an action
from changing the graph. The spine replaces uniform random successors, under which
the part of a graph reachable from node 0 had about 4 nodes even for N ≤ 30, and
forward edges are bounded because a long skip shortens every path behind it.

The edge and node sections were first split into N equal slices. `WeightedSlices`
gives each of N slices ⌊L/N⌋ bytes and the last the remainder, so a section shorter
than N gave its every byte to node N - 1, and admitted inputs are short: in a campaign
of 3000 entries, 51 bytes at the median. 1915 of its 2138 extra edges left the last
node, 1675 entries had none, and the median entry had 2 distinct node actions among 29
nodes. Read from node 0 on, a short input spends its bytes where every path to the
target starts, and a long one is divided as before.

Exhausted input decodes to the bare spine of `min_graph_nodes` nodes, one data
variable, no extra edge, k = ⌈N/2⌉ and B = ⌊N/2⌋, both 10 by default: a
counterexample of 10 transitions.

**Deviation from [ir-generators.md §1][generators].** `graph` is a third encoding,
independent of `expr` and `module` ([ir-generators.md §12][generators]). Requirement 3
holds within a node's slice; a mutation of N re-slices the node section, and a
mutation of the edge section that changes the length of one node's list shifts the
lists of the nodes after it.

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
Apalache checks the invariant after every step of its bounded search, so its first
violation is a shortest one as well; the probe confirmed it.

The workflow translates the prediction into a `corpus.ExpectedOutcome(CorpusVerdict
verdict, OptionalInt traceLength)`, so `corpus` depends on no type of `gen.graph`.

### 5. Graph oracle

`Oracle.GRAPH` passes an entry when all of the following hold:

1. The checkers agree, as under `CONFORMANCE`, including the state counts of
   [ADR 0019][adr-0019].
2. Every checker reports the expected verdict or `fail`. A `pass` on a reachable
   target, or a `counterexample` on an unreachable one, fails the entry.
3. Every recorded `traceLength` is at least dist(k), and equals dist(k) for a checker
   whose `CorpusStage.counterexampleSearch()` is `SHORTEST`: TLC and Apalache. TLC-n
   is `ANY`, since its workers need not report a shortest trace.

`fail` is admitted in rule 2 because a partial term makes TLC fail on any input, and
conformance already compares it with Apalache.

`Oracle.judge` takes the results per checker stage, which rule 3 needs, and the
expected outcome, which `CONFORMANCE` and `METAMORPHIC` ignore. The corpus judges an
aggregation at run time and again at recovery, but cannot decode. `CheckingPolicy`
therefore carries a `corpus.ExpectedOutcomes`, which `workflow.spec.GraphOutcomes`
implements by replaying the entry through `SpecDecoders`, and `AggregationInput`
holds the prediction for its entry; storage keeps only bytes. Because
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
`UnchangedPrime`, are not offered, nor are rules with a higher-order parameter or a
helper. `RuleInstantiation.instantiateFormula` instantiates A and B together.

The guard section of section 3 decodes, for each edge in edge order, a terminated list
of guards; each guard is a rule index, two bytes as in the rewriter, followed by its
operands, and reads at most 8 bytes. An exhausted section decodes no guard, so a short
section guards the edges leaving node 0, where every path to the target starts. The
section was first split into N slices aligned with the nodes. An equal split of a
section shorter than N gives every byte to the last node, and admitted inputs are
short, so Probe 3 found most paths to a reachable target unguarded. Without the cap,
the operands of the first guards drained a longer section.

The prediction of section 4 is unchanged. A guard that a checker evaluates to `FALSE`
disables its edge; when that edge lies on every shortest path to k, the checker
reports `pass` or a longer trace, and the oracle fails the entry. A violation
indicates a checker defect or an unsound rule, which triage tells apart as under
`mt`.

### 7. Configuration

A new optional `[graph]` table, read into `gen.GraphLimits`, a component of
`IrGenerationConfig`, so that every decoder registry built from a generator
configuration decodes graphs alike:

| Key | Default | Meaning |
| --- | --- | --- |
| `min_graph_nodes` | `20` | Minimum N, so a spine is at least 19 steps long. |
| `max_graph_nodes` | `32` | Maximum N. |
| `max_extra_successors` | `2` | Maximum successors of a node besides its spine successor. |
| `max_steps` | `max_graph_nodes` | Cap on the step bound B each input draws. |
| `max_guards` | `2` | Maximum tautological guards per edge. |
| `rules` | absent | Rule module of the guards; absent disables stage 2. |
| `weights` | `{}` | Rule weights, as in `[metamorphic]`. |

Both tables read `rules` and `weights` into one record, `config.RuleLibraryConfig`,
through one schema, `config.RuleLibrarySchema`, and one preparation,
`workflow.library.RuleLibraryPreparation`; the corpus file `.rewrite-library` pins the
prepared rules. The data variables and the expression limits come from `[generator]`;
`[generator] max_steps` does not apply to graph inputs.

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
   conformance oracle. *Implemented.*
2. `Oracle.GRAPH` with rules 1 and 2 of section 5, and `ExpectedOutcome`.
   *Implemented.*
3. Rule 3 of section 5, after a probe measures `traceLength` on reachable targets for
   each checker. *Implemented; see [Probes](#probes).*
4. Tautological guards, the shared `RuleLibraryConfig`, and the guard section.
   *Implemented; see [Probes](#probes).*

Each phase updates [ir-generators.md][generators] and
[fuzzing-workflows.md][workflows] when it lands, and types carrying a new `@Key`
get an `@ArchRole`.

## Probes

### Probe 1: random successors

**Setup.** One `run --how=gbt` of 600 graph entries in two generations of 300, seed
23, with TLC and Apalache at their default limits (30 s per Apalache check) and the
shipped `signatures/known-defects.toml`. The run used the decoder of FuzzTLA `39f0ef1`,
before node 0 had to have a successor; the expected distances were computed with that
decoder. 40 entries matched a known-defect signature, 443 were aggregated, and 157
crashed Apalache.

**Verdicts and lengths.** No checker contradicted a predicted verdict. Every
counterexample had exactly dist(k) transitions:

| Checker | Pass, expected pass | Counterexample, expected | Fail | Counterexamples by dist(k) = 0, 1, 2, 3 |
| --- | ---: | ---: | ---: | --- |
| TLC | 157 | 86 | 200 | 16, 57, 13, 0 |
| Apalache | 258 | 157 | 28 | 32, 103, 20, 2 |

This set Apalache's `counterexampleSearch()` to `SHORTEST`. `GraphModuleCheckersTest`
pins both facts on decoded graphs.

**Crashes.** 106 of the 107 graphs without edges crashed Apalache: 94 with
`Missing assignments` on `Next == FALSE \/ UNCHANGED vars`, which its manual
documents, and 12 by timeout. Node 0 now always has a successor. Of the 51 other
crashes, 47 were timeouts and 4 known Apalache defects
([apalache-bmc-004][apalache-bmc-004], [apalache-bmc-014][apalache-bmc-014],
[apalache-bmc-016][apalache-bmc-016]).

**Failures.** TLC failed on 200 of the 443 aggregated entries, on partial terms: a
function applied outside its domain (67), a value it cannot compute, such as a
`CHOOSE` without a witness (58), a `CASE` without a true arm (52), and 23 others.
Apalache picked values and continued, so 176 of the 180 aggregator failures pair a
TLC failure with an Apalache pass or counterexample. The other 4 are Apalache
failures on `ENABLED` (3) and a division by zero (1), which signatures of
`signatures/all-defects.toml` match. These are conformance differences of the kind a
`pbt` corpus has. A node action runs on every visit, so a partial term on a reachable
node is reached more often than in a `pbt` module.

**Decoder statistics.** After node 0 was given a successor, and before the spine,
over 400 random inputs per length the decoder yields 3.4–3.5 nodes on average and a target reachable within the bound for
24% (16 bytes), 48% (64), 55% (256) and 61% (1024 bytes) of the graphs; a target at
distance 2 or more for at most 7%.

### Probe 2: spines

**Setup.** One `run --how=gbt` of 300 graph entries, seed 29, with the spine decoder
of FuzzTLA `71f911a`, TLC and Apalache, `[workflow.apalache] timeout_sec = 120` and
`signatures/all-defects.toml`. The signatures quarantined 726 candidates while 300
were admitted; every admitted entry was aggregated, and no checker crashed.

**Verdicts and lengths.** 151 entries predicted a counterexample and 149 a pass. No
checker contradicted a prediction, and every counterexample had exactly dist(k)
transitions:

| Checker | Pass, expected pass | Counterexample, expected | Fail | Counterexample lengths |
| --- | ---: | ---: | ---: | --- |
| TLC | 139 | 133 | 28 | 11–30; 33 of 20 or more |
| Apalache | 149 | 151 | 0 | 11–30; 39 of 20 or more |

**Cost.** TLC took at most 2 s per entry and found at most 284 distinct states
(median 17): the node actions are deterministic, so the states are the walks of
length at most B, and back edges revisit few new values. Apalache took 2 s at the
median, 10 s at the 95th percentile and 45 s at most; 1 entry of 300 exceeded the
default 30 s.

**Failures.** All 28 aggregator failures pair a TLC failure on a partial term with
an Apalache pass (10) or counterexample (18).

### Probe 3: guards

**Setup.** One `run --how=gbt` of 600 graph entries in two generations of 300, seed
31, with FuzzTLA `e3fa311`, `[graph] rules` the shipped `Rewrites.tla` at its annotated
weights, `max_guards = 2`, TLC and Apalache, `[workflow.apalache] timeout_sec = 120`
and `signatures/all-defects.toml`. The signatures quarantined 1431 candidates, 881 of
them distinct. 599 entries were aggregated; Apalache timed out on the other.

**Verdicts and lengths.** 283 entries predicted a counterexample and 316 a pass. No
checker contradicted a prediction, and every counterexample had exactly dist(k)
transitions:

| Checker | Pass, expected pass | Counterexample, expected | Fail | Counterexample lengths |
| --- | ---: | ---: | ---: | --- |
| TLC | 288 | 268 | 43 | 10–30; 119 of 20 or more |
| Apalache | 316 | 283 | 0 | 10–30; 119 of 20 or more |

So no checker evaluated a guard on a shortest path to the target to `FALSE`.

**Guards.** This probe used the first layout: the guard section split into N slices
aligned with the nodes. The 599 entries have 15955 edges and 1676 guards, 0.11 per
edge; 219 entries have a guard, and one has 22. Of the 283 entries with a reachable
target, 228 have no guard on the first shortest path. Guards are sparse because the
admitted inputs are short, 58 bytes at the median and 645 at most, and an equal split
of a guard section shorter than N gives every byte to the last node. Of the distinct
inputs over 256 bytes, 658 matched a known-defect signature and 6 were admitted.

**Cost.** TLC took at most 4 s per entry and found at most 187 distinct states (median
18). Apalache took 3 s at the median, 16 s at the 95th percentile and 49 s at most,
against 2 s, 10 s and 45 s in Probe 2.

**Failures.** All 43 aggregator failures pair a TLC failure on a partial term, a
function applied outside its domain (21), a `CASE` without a true arm (17), a value it
cannot compute (4) and a tuple index (1), with an Apalache pass (28) or
counterexample (15).

### Probe 4: guards read in edge order

**Setup.** As Probe 3, with FuzzTLA `8fec038`: the guard section is one cursor over
the edges in edge order, and each guard reads at most 8 bytes. The signatures
quarantined 1504 candidates, 852 of them distinct. 599 entries were aggregated; TLC
crashed on the other, on `Head(<<>>)` in `Next`, which it reports with exit status 255
([tlc-002][tlc-002]).

**Verdicts and lengths.** 274 entries predicted a counterexample and 325 a pass. No
checker contradicted a prediction, and every counterexample had exactly dist(k)
transitions:

| Checker | Pass, expected pass | Counterexample, expected | Fail | Counterexample lengths |
| --- | ---: | ---: | ---: | --- |
| TLC | 300 | 259 | 40 | 10–30; 94 of 20 or more |
| Apalache | 325 | 274 | 0 | 10–30; 97 of 20 or more |

**Guards.** 580 of the 599 entries have a guard, against 219 in Probe 3, and 9 of the
274 entries with a reachable target have none on the first shortest path, against 228
of 283. The entries hold fewer guards in total, 990 on 15931 edges, at most 8 in one
entry: a guard section of a few bytes guards the first edges and stops. The admitted
inputs are shorter, 31 bytes at the median and 323 at most.

**Cost.** TLC took at most 1 s per entry and found at most 187 distinct states (median
19). Apalache took 3 s at the median, 15 s at the 95th percentile and 49 s at most.

**Failures.** All 40 aggregator failures pair a TLC failure on a partial term, a
function applied outside its domain (17), a `CASE` without a true arm (17) and a value
it cannot compute (6), with an Apalache pass (25) or counterexample (15).

### Probe 5: node and edge sections read from node 0

**Setup.** Two campaigns of 3000 `gbt` entries in ten generations of 300, seed 37,
with TLC and Apalache (four workers, `timeout_sec = 120`), `[graph] rules` the shipped
`Rewrites.tla`, and `signatures/all-defects.toml`. corpus71 ran FuzzTLA `ab9b847`, with
equal node and edge slices; corpus72 ran FuzzTLA `9c40de3e`, which reads both sections
from node 0 on. TLC `d2e1472`, Apalache 0.62.3 `3eb15b2`. corpus72 was interrupted
once and resumed with the same build, so its generator statistics cover only the
second session.

| | corpus71 | corpus72 |
| --- | ---: | ---: |
| Aggregated entries | 2997 | 2952 |
| Predicted counterexample / pass | 1479 / 1518 | 1846 / 1106 |
| Contradicted predictions | 0 | 0 |
| Input bytes, median | 51 | 37 |
| Extra edges | 2138 | 3181 |
| Extra edges leaving the last node | 1915 | 1 |
| Entries without an extra edge | 1675 | 979 |
| Distinct node actions per entry, median | 2 | 2 |
| Entries with one node action on every node | 607 | 506 |
| Node actions that read a data variable | 2.8% | 3.6% |
| TLC distinct states, median / 95th percentile | 21 / 32 | 42 / 497 |
| Apalache time, median / 95th percentile / max | 5 / 21 / 37 s | 7 / 49 / 116 s |
| Apalache timeouts | 1 | 40 |
| Disagreements, all documented | 205 (6.8%) | 440 (14.9%) |
| Paths to a reachable target without a guard | 33 of 1479 | 370 of 1846 |

**Edges.** Extra edges now leave the first nodes, so the graphs have cycles and skips
where every path starts: TLC's state counts double at the median and grow by an order
of magnitude at the 95th percentile. Forward skips near node 0 shorten paths, so more
targets fall within the bound: 62% of the entries predict a counterexample, against
49%.

**Node actions.** They barely change. The median entry still has 2 distinct actions,
because an admitted input has a few dozen bytes, of which the node section receives
about ten: enough for one or two actions whichever nodes they go to. The node
section is limited by the known-defect quarantine of longer inputs, not by its split.

**Cost and disagreements.** More edges make Apalache's bounded search harder: 40
entries timed out at 120 s, against 1. Disagreements doubled, all of documented kinds
in which TLC fails on a partial term and Apalache continues, since more actions run
on more paths.

**Guards.** More paths lack a guard. Node 0 now has two or three edges, which read
the guard section first, so fewer guard bytes reach the later edges of a shortest
path.

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
- **Traces are long, and the outcomes balanced.** In Probe 2, predicted
  counterexamples had 11 to 30 transitions, about a quarter of them 20 or more, and
  the per-input bound makes about half the entries predict a pass ([Probes](#probes)).
- **Partial terms are frequent.** A node action runs on every visit. With the shipped
  `known-defects.toml`, TLC failed on 45% of graph entries; with
  `signatures/all-defects.toml`, on 9%, at the cost of quarantining more than two
  candidates per admitted entry. These are conformance differences, not graph
  violations.
- **Checking stays cheap.** Deterministic node actions keep TLC's state space small,
  and Apalache's bounded search of 10 to 31 steps rarely approaches its default
  timeout.
- **`pc` counts as state change.** ADR 0008 projects out only `step`, so an entry
  whose data never changes still has projected depth. Whether to project out `pc`
  too was left open until a GBT corpus showed how the gate ranks such entries;
  [ADR 0024][adr-0024] projects it out and ranks `gbt` entries by action coverage.
- **Graph entries were not rewritten.** `mt` rejected the `graph` kind until the
  amendment above.
- **Guard rules are part of the encoding.** Setting `[graph] rules` re-slices every
  graph input, so a corpus keeps the rule module it started with.
- **Guards are few on short inputs.** Admitted inputs are short, 31 bytes at the
  median in Probe 4, because the known-defect signatures quarantine almost every long
  graph module. Read in edge order, a short guard section still guards the start of
  almost every path to the target, but with one or two guards rather than many.

[adr-0008]: 0008-exploration-metrics.md
[apalache-bmc-004]: ../../findings/apalache-bmc/apalache-bmc-004.md
[apalache-bmc-014]: ../../findings/apalache-bmc/apalache-bmc-014.md
[apalache-bmc-016]: ../../findings/apalache-bmc/apalache-bmc-016.md
[tlc-002]: ../../findings/TLC/tlc-002.md
[adr-0016]: 0016-metamorphic-testing.md
[adr-0017]: 0017-rewrite-rule-library.md
[adr-0019]: 0019-state-count-agreement.md
[adr-0024]: 0024-graph-action-coverage.md
[generators]: ../architecture/ir-generators.md
[manual]: ../manual/graph-based-testing.md
[workflows]: ../architecture/fuzzing-workflows.md
