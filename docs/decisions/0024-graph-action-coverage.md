# 0024: Action coverage in the selection of graph entries

**Authors:** Igor Konnov and Claude

**Status:** Implemented; the validation campaign met two of the three criteria of
section 5, and known-defect quarantine rose (see [Validation](#validation))

**Date:** 2026-10-23

**Implementation.** Phases 1 and 2 of section 6 are implemented on the branch
`igor/gbt-action-coverage-gate`, merged as FuzzTLA `a981b4e8`. The review settled two questions: `dataActions`
counts edges rather than nodes, and entries with constant data cannot become parents.
Implementation fixed these details:

- `GeneratedGraph.CONTROL_VARIABLES` names a graph module's control variables, and
  `CheckRequest.controlVariables` carries them in worker protocol version 8. The TLC
  worker builds its state metrics from each request.
- `corpus.Ranking` holds the components of both keys, and each `Technique` constant
  names its ranking. The key's components are also the cell, as in ADR 0013, so
  `dataActions` is bucketed in both (section 2).
- The export adds the column `dataActions` and raises the schema version to 10.

## Context

The quality gate ([ADR 0010][adr-0010], [ADR 0013][adr-0013]) chooses the parents of
the mutator. It ranks entries by TLC's exploration metrics ([ADR 0008][adr-0008]),
depth of state change first, and it projects out only the step counter. In a graph
module ([ADR 0023][adr-0023]) every transition changes `pc`, so these metrics measure
the walk, not the node actions:

- `projectedDepth` is the length of the longest walk, and `projectedStates` grows
  with the number of walks.
- `actionsDiscovering` counts every edge that fires, since every transition reaches a
  new state.
- No graph entry matches a shallow pattern, since `pc` changes on every step. ADR 0023
  left open whether to project out `pc`.

A campaign of 3000 `gbt` entries (corpus71, seed 37, FuzzTLA `ab9b847`, TLC `d2e1472`,
Apalache 0.62.3 `3eb15b2`) shows the effect. The gate selected 142 entries, 84 of them
with a target 30 or more steps away, and mutants inherited the header: all 395
entries at distance 30 or 31 were mutants. The selected entries were not richer than
the rest: a median of 2 distinct node actions among 29 nodes, none reading a data
variable, and 2 of 142 with more than one data variable. TLC found 21 distinct states
at the median, about one per step of the walk.

The oracle of ADR 0023 does not need rich actions, but conformance does: a defect in
evaluating a node action surfaces only when the action computes something, and
mutation should breed such entries rather than longer walks.

**Scope.** This ADR decides what TLC measures for a graph module, how the gate ranks
and buckets `gbt` entries, and which graph entries are shallow. The byte encoding,
the oracle and the mutation operators are unchanged.

## Decision

### 1. Control variables

A module names its *control variables*: the variables that sequence its actions
rather than compute data. Every module has `step`; a graph module also has `pc`.
`SpecArtifact` derives them from the decoded input, and `CheckRequest` carries them to
the checker, so the projection follows the entry, not the technique. A graph entry in
a `pbt` corpus is projected alike.

TLC's state metrics project out every control variable, where ADR 0008 projects out
`step` alone:

- `projectedStates` counts the distinct valuations of the data variables, and
  `projectedDepth` is the deepest level at which a new one appears.
- A new count, `dataActions`, counts the disjuncts of `Next`, by source location, with
  a transition to a new projected state. In a graph module a disjunct is an edge, so
  `dataActions` counts the edges whose node action produced a data valuation not seen
  before. For any other module it differs from `actionsDiscovering` only when the step
  counter alone changes.

The corpus database exports `dataActions` beside the other counts.

### 2. Ranking `gbt` entries

`corpus.Technique` names the gate's ranking, a fact on the constant: `pbt` and `mt`
rank by depth (ADR 0010), `gbt` by action coverage. Under action coverage the key is

```
(bucket(dataActions), bucket(projectedStates), projectedDepth,
 bucket(maxStateNodes), bucket(maxCardinality), maxNesting)
```

compared lexicographically, higher first, with ADR 0010's tie-breaks. An entry with
more node actions that each reach new data outranks a longer walk over constant
actions; data depth decides only between equals.

`bucket` is ADR 0010's logarithmic bucket, `bucket(v) = floor(log2(v + 1))`. It maps
a count to the number of binary digits it needs:

| v | 0 | 1 | 2–3 | 4–7 | 8–15 | 16–31 | 32–63 | 64–127 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| `bucket(v)` | 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 |

ADR 0010 compares `actionsDiscovering` directly, because `[generator] max_actions`
bounds it by 3 by default. `dataActions` is bounded only by the edges of the graph,
at most three per node, so fewer than 96 under the default `[graph]` limits. Compared
directly, one more data action would outrank any difference in the data valuations,
since the key is lexicographic: 11 data actions over 3 valuations would beat 10 over
60. Bucketed, entries with 8 to 15 data actions tie, and `bucket(projectedStates)`
decides between them. An order-of-magnitude difference in data actions still
dominates.

### 3. Cells

The behaviour cell of ADR 0013 is the TLC verdict and the components of the key, so
under action coverage it is

```
(verdict, bucket(dataActions), bucket(projectedStates), projectedDepth,
 bucket(maxStateNodes), bucket(maxCardinality), maxNesting)
```

`projectedDepth` and `projectedStates` already exclude `pc` by section 1, so a cell
holds entries with the same data behaviour, and walk length alone no longer opens a
new cell. The bucket matters here as well: `[mutator] cell_capacity` admits 4 entries
per cell by default, and with `dataActions` compared directly almost every count
from 0 to 96 would open a cell of its own, so the capacity would bound nothing.

### 4. Shallow graph entries

With `pc` projected out, ADR 0008's patterns apply to graph entries as written. An
entry whose node actions never change the data has one projected state, so it
matches `counter_only_progress` and cannot become a parent. An entry whose data takes
two values, its initial one and one constant, is admissible, and the key of section
2 ranks it low. The expected outcome and the oracle do not depend on these metrics.

### 5. Validation

A campaign repeating corpus71's setup with the new gate is compared with corpus71 on
the selected entries and on the whole corpus: distinct node actions per entry, node
actions that read data, `projectedStates`, `dataActions`, and the distance
distribution. The change is kept if the selected entries have more distinct and
data-reading actions without fewer predicted counterexamples or a higher rate of
known-defect quarantine.

### 6. Implementation phases

1. Control variables in `CheckRequest`, TLC's projection, `dataActions` and its
   export. Every corpus's metrics for non-graph entries stay as they were.
2. The ranking on `Technique`, the action-coverage key and cell in
   `workflow.quality`, and the shallow patterns over `dataActions`.
3. The validation campaign, recorded in this ADR.

## Validation

**Setup.** Phase 3 compares two campaigns of 3000 `gbt` entries with the encoding of
ADR 0023 Probe 5, in ten generations of 300, seed 37, with TLC and Apalache
(`timeout_sec = 120`), `[graph] rules` the shipped `Rewrites.tla`, and
`signatures/all-defects.toml`. corpus72 ran FuzzTLA `9c40de3e`, which ranks by depth;
corpus74 ran FuzzTLA `12d0a3fd`, which ranks `gbt` entries by action coverage. TLC
`d2e1472`, Apalache 0.62.3 `3eb15b2`. Section 5 named corpus71 as the baseline, but
corpus71 predates the node and edge encoding of Probe 5, so corpus72 is the baseline.
corpus74 ran two Apalache workers instead of four, after memory pressure stopped an
earlier attempt, so its times are not comparable.

**Selected entries.** The gate now selects entries whose actions compute data:

| `04quality-pass` | corpus72 | corpus74 |
| --- | ---: | ---: |
| Entries | 130 | 129 |
| Distinct node actions, median | 2 | 5 |
| Entries with at least 4 distinct node actions | 10% | 98% |
| Node actions that read data, mean | 0.56 | 3.36 |
| Entries with more than one data variable | 1% | 70% |
| Extra edges, mean | 1.19 | 3.95 |
| Targets 30 or more steps away | 79% | 0% |
| `dataActions`, median | – | 3 |
| TLC distinct states, median | 497 | 181 |

**Mutants.** Mutation breeds the selected traits. In generations 5 to 9, the mutants
of corpus74 have 9 distinct node actions at the median, against 2 in corpus72; 98% of
them have a node action that reads data, against 52%; and 59% have more than one data
variable, against 3%.

**Whole corpus.**

| All aggregated entries | corpus72 | corpus74 |
| --- | ---: | ---: |
| Entries | 2952 | 2927 |
| Distinct node actions, median | 2 | 4 |
| Predicted counterexamples | 1846 | 1959 |
| Counterexamples of 20 or more steps | 999 | 532 |
| Contradicted predictions | 0 | 0 |
| Disagreements, all documented | 440 | 452 |
| TLC crashes, all tlc-002 | 8 | 30 |
| Apalache timeouts | 40 | 43 |
| Candidates that matched a known-defect signature | 48% | 54% |

corpus72's generator statistics cover only its second session, 3865 candidates; its
first session was stopped and did not record them. corpus71, with the earlier
encoding, quarantined 50%.

**Criteria.** The selected entries have more distinct and more data-reading node
actions, and the corpus predicts more counterexamples, so two criteria hold. Known-
defect quarantine rose from 48% to 54% of candidates: mutants of richer parents
contain more expressions, and so match a signature more often. The gate also stopped
selecting deep targets, as the Consequences expected: counterexamples of 20 or more
steps fell from 999 to 532, and none is longer than 29 steps.

## Alternatives considered

- **Static action features.** The gate could replay the decoder and count distinct
  node actions, or actions that read data, on the reachable part of the graph. It
  needs no checker change, but rewards actions that TLC never evaluates, or evaluates
  to the same data, and duplicates what the ADR 0013 construct features already
  reward.
- **Projecting `pc` but keeping the depth key.** It fixes the shallow patterns and the
  cells, but still ranks the deepest change of data first, which in a graph module is
  usually one long walk with one data-changing node.
- **Weighting parents in the mutator.** Drawing parents by a richness weight instead of
  uniformly from the pool would bias mutation, but leaves the pool, and so the
  cells and coverage, measured by walk length.
- **Counting node actions rather than edges.** All edges leaving a node run the same
  action. Counting nodes would need TLC to map each disjunct to its source node; edges
  differ only when a node has several successors, which section 1 accepts.

## Consequences

- **Selection follows the data.** Parents are entries whose actions compute new
  values, so mutation explores node actions rather than headers.
- **Metrics of graph entries change meaning.** `projectedStates` and `projectedDepth`
  of a graph entry exclude `pc` from the corpus that adopts this ADR on; an older
  corpus keeps its stored metrics, and its gate keeps ranking them as recorded.
- **Fewer admissible graph entries.** Entries with constant data become shallow. In
  corpus71, 607 of 2997 entries had one node action repeated on every node, and 1833
  had no node action that reads data; the gate may fill fewer than `select_fraction`
  slots until mutation breeds richer entries. In corpus74 it kept 129 entries,
  against 130 under the depth ranking.
- **Depth is no longer selected for.** Only mutation bred the deepest targets: in
  corpus72 all 590 targets 30 or more steps away were in mutants, and random inputs
  produced none. corpus74 has none, and half as many counterexamples of 20 or more
  steps ([Validation](#validation)).

[adr-0008]: 0008-exploration-metrics.md
[adr-0010]: 0010-mutation.md
[adr-0013]: 0013-behaviour-archive-and-operator-coverage.md
[adr-0023]: 0023-graph-based-testing.md
