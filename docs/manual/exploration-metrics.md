# Exploration metrics

> **Status:** Implemented.
> [ADR 0008](../decisions/0008-exploration-metrics.md) records the design and
> the calibration measurements behind it. Entries checked before this feature
> have no metrics.

Two checkers agreeing on a verdict says little when neither of them explored
anything. Most agreeing pairs in current corpora are like that: TLC finds no
initial state, the invariant fails in the initial state, or both checkers stop
on an evaluation error before a state exists. Exploration metrics record *how
much* a checker explored to reach its verdict. The quality gate of
[ADR 0010](../decisions/0010-mutation.md) uses them to keep only agreements that
exercise the checkers.

The TLC stage measures its run and stores the result in the corpus entry. The
Apalache stage stores the length of its counterexample. The stages do not accept
or reject anything based on these metrics, and no configuration key controls
them.

## 1. Where metrics are stored

Each checker stores its metrics in a `metrics` map under its own stage record:

```cbor
{
  "kind": "module",
  "input": h'…',
  "stages": {
    "tlc": {
      "verdict": "counterexample",
      "startTime": 1(1789459200),
      "endTime": 1(1789459202),
      "metrics": {
        "phase": "explore",
        "initStates": 1,
        "distinctStates": 4,
        "generatedStates": 7,
        "projectedStates": 3,
        "depth": 3,
        "projectedDepth": 2,
        "actions": 2,
        "actionsFired": 2,
        "actionsDiscovering": 1,
        "maxStateNodes": 9,
        "maxCardinality": 3,
        "maxNesting": 2,
        "traceLength": 3
      }
    },
    "apalache": {
      "verdict": "counterexample",
      "startTime": 1(1789459202),
      "endTime": 1(1789459205),
      "metrics": { "traceLength": 3 }
    }
  }
}
```

Fields appear in the order of the tables in section 2, with `saturated` last. A count the checker did
not measure is absent; for example, `actions` is absent when TLC stops before
model checking starts. `saturated` appears only when it is true.

The map is written for the `pass`, `counterexample` and `fail` verdicts. A
`crashed` entry has no metrics: a timeout or a dead worker JVM returns nothing to
measure. On `fail` and `counterexample`, the counts cover what TLC had explored
when it stopped.

The conformance aggregator copies both stage records into the merged entry
unchanged, so metrics reach `03aggregator-pass` and `03aggregator-fail`.

## 2. Metric reference

### 2.1. TLC

| Field | Meaning |
| --- | --- |
| `phase` | How far TLC got: `complete` if the search finished, otherwise `init` if it stopped before its initial states were complete and `explore` if it stopped afterwards. |
| `initStates` | Distinct initial states. |
| `distinctStates` | Distinct reachable states, initial states included. |
| `generatedStates` | Initial states plus transitions, including transitions to states already found. |
| `projectedStates` | Distinct states after removing `step`, the step counter of a generated module. In an `expr` entry, equal to `distinctStates`. |
| `depth` | Length of the longest shortest path from an initial state to a found state. |
| `projectedDepth` | The largest depth at which a new projected state appeared. |
| `actions` | Sub-actions TLC split `Next` into, including the skeleton's `UNCHANGED` disjunct. |
| `actionsFired` | Disjuncts of `Next`, by source location, that produced at least one transition. |
| `actionsDiscovering` | Disjuncts of `Next`, by source location, that produced at least one new state. |
| `maxStateNodes` | Largest state by value node count: every scalar, collection, record, tuple and function node counts one. |
| `maxCardinality` | Largest set, sequence, tuple, record or function domain in any state. |
| `maxNesting` | Deepest value nesting in any state. A scalar variable has nesting 0. |
| `saturated` | `true` if a state exceeded the node cap of the size walk. The three size fields are then lower bounds. |
| `traceLength` | Transitions in TLC's counterexample. A liveness trace counts its closing stuttering step or step back to an earlier state. A violation in an initial state has length 0. Present only for `counterexample`. |

### 2.2. Apalache

| Field | Meaning |
| --- | --- |
| `traceLength` | States in `violation1.itf.json` minus one. Present only for `counterexample`, and absent if Apalache wrote no readable trace. |

## 3. Reading metrics

`fuzztla print --envelope` lists the metrics under each stage, one field per
line:

```text
stages:
  tlc:
    verdict: counterexample
    startTime: 2026-09-15T08:00:00Z
    endTime: 2026-09-15T08:00:02Z (duration: 2s)
    metrics:
      phase: explore
      initStates: 1
      distinctStates: 4
      generatedStates: 7
      projectedStates: 3
      depth: 3
      projectedDepth: 2
      actions: 2
      actionsFired: 2
      actionsDiscovering: 1
      maxStateNodes: 9
      maxCardinality: 3
      maxNesting: 2
      traceLength: 3
```

## 4. Recognizing shallow entries

These patterns show that an agreement does not exercise the model checkers.
[ADR 0008](../decisions/0008-exploration-metrics.md) estimates that 99.6% of
the agreeing entries of corpus22 match at least one of them.

| Pattern | Metrics | What happened |
| --- | --- | --- |
| Vacuous pass | verdict `pass`, `initStates: 0` | `Init` is unsatisfiable. TLC checks nothing and passes. |
| Initial-state violation | verdict `counterexample`, `phase: init` | `Inv` or `Prop` fails on an initial state. `Next` is never evaluated. |
| Early failure | verdict `fail`, `phase: init` | TLC fails before its initial states are complete. `Next` is never evaluated. |
| No discovering action | `actionsDiscovering: 0` | No disjunct of `Next` reaches a new state. |
| Counter-only progress | `projectedStates` at most 1 | Only `step` changes. Every other variable keeps its initial value. |

The gate will combine metrics from both stages. Agreement on `traceLength`, and
on the failure code of a fail/fail pair, is derived from the stored values
rather than stored separately.

## 5. Limits

- Metrics come from one TLC run with the stage's settings. They describe the
  bounded state graph that `max_steps` allows, not the specification in general.
- The TLC metrics add a callback per transition and a bounded value walk per new
  state. ADR 0008 measured no difference on corpus entries and about 15% on a
  module with 1.7 million transitions.
- Apalache reports no state counts. It explores symbolic executions of fixed
  length, so `traceLength` is its only comparable metric.
