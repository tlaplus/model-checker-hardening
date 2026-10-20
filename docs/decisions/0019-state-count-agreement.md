# 0019: State-count agreement of completed searches

**Authors:** Igor Konnov and Claude

**Status:** Implemented

**Date:** 2026-10-20

**Implementation.** Branch `igor/tlcn-state-count` (FuzzTLA `e365236`, TLC
`210df3a` from tla2tools snapshot `1.8.0-20261013.012418-104`, Apalache 0.62.3
`3eb15b2`).

## Context

[ADR 0018][adr-0018] runs TLC with one worker (`tlc`) and with n workers (`tlcn`)
on the same input, and the aggregator compares their verdicts. A verdict is a
coarse oracle for the code that only n workers run. A worker that loses a state
in the shared queue, or a fingerprint set that drops or duplicates a concurrent
insert, changes the verdict only when the affected states violate the invariant
or the property. Otherwise both runs pass.

Every TLC run records `distinctStates`, the number of initial states plus new
successors ([ADR 0008][adr-0008]). When the search completes, this number is the
size of the reachable state space. It does not depend on the number of workers
or on the exploration order. Two completed searches of one input that report
different numbers of states expose a defect that the verdicts do not show.

### Measurement

`corpus66` runs `["tlc", "tlcn"]` with 1 and 4 workers and holds 303,993 entries.
Its 115,515 pass/pass entries agree on `distinctStates`; 46,503 of them reach
more than 10 states, 1,615 more than 100, and the largest reaches 216,513.
Runs that stop while exploring successors do not agree: 7,512 of 28,717 such
counterexample pairs and 558 of 7,923 such fail pairs report different counts.

## Decision

### 1. Agreement

Both oracles of [ADR 0016][adr-0016] §4 require the checkers to *agree*. Two
results agree when:

- the checkers report the same verdict, as before; and
- every checker whose metrics record `phase = complete` and `distinctStates`
  reports the same `distinctStates`.

A conformance entry passes when the checkers agree. A metamorphic entry passes
when they agree and none reports a counterexample. Otherwise the entry moves to
`03aggregator-fail`.

### 2. Scope

The rule reads the stored metrics rather than checker names:

- **Completed searches only.** TLC's phase is `complete` exactly for the `pass`
  verdict. A run stopped by a counterexample or an evaluation error explored a
  prefix that depends on the search order (see the measurement).
- **Checkers that count states.** Apalache records only `traceLength`, so a
  `["tlc", "apalache"]` corpus is judged by verdicts alone, as before. A
  checker that records `distinctStates` for a completed search joins the
  comparison without a change to the oracle.
- **Distinct states only.** `depth` depends on the order in which n workers
  first reach a state. `initStates`, `generatedStates` and `projectedStates` are
  determined by the state graph and agree on every pass/pass entry of `corpus66`,
  but they are not compared yet.

### 3. Recovery and triage

The aggregator records no reason for a failure; the stage records of the merged
entry carry both counts. Startup recovery re-judges every aggregated entry from
its stored records, including the metrics.

`script/triager.py` labels an aggregator entry `STATE_COUNT` when its completed
searches report different `distinctStates`. The label takes precedence over the
signatures and `FIRST_ERROR`, so a failing third checker cannot hide the
mismatch.

The corpus database needs no change: the `stage` table has `phase` and
`distinctStates` ([corpus-database.md][database]).

## Consequences

- **Existing corpora.** An aggregated entry whose completed searches disagree
  fails validation on the next run with "aggregator verdict does not match
  checker results". `corpus66` has no such entry.
- **Fingerprint collisions.** TLC identifies states by 64-bit fingerprints. A
  collision in one run lowers its count and fails the entry. For the state
  spaces above, the probability is negligible, and the triager does not
  distinguish this case.
- **Architecture.** This ADR revises [fuzzing-workflows.md][workflows] §1.3 and
  §1.4: the aggregator compares the state counts of completed searches.

## Alternatives considered

- **Compare TLC's own statistics.** TLC prints the number of distinct states it
  found. Parsing that message adds a second source for a number the state
  writer already measures.
- **A separate oracle for TLC-n.** The comparison is meaningful for any pair of
  exhaustive checkers and costs nothing for others, so both oracles apply it.

[adr-0008]: 0008-exploration-metrics.md
[adr-0016]: 0016-metamorphic-testing.md
[adr-0018]: 0018-parallel-tlc-stage.md
[database]: ../manual/corpus-database.md
[workflows]: ../architecture/fuzzing-workflows.md
