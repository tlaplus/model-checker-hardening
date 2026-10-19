# 0018: A TLC stage with n workers

**Authors:** Igor Konnov and Claude

**Status:** Proposed

**Date:** 2026-10-19

## Context

Every checker stage judges one input on its own, and the aggregator compares the
verdicts of the configured stages ([ADR 0016][adr-0016] §5). TLC runs with
`workflow.tlc.workers = 1` by default. Campaigns therefore never exercise the code
that TLC runs only with several workers:

- the state queue that workers share;
- concurrent inserts into the fingerprint set;
- liveness checking over a behavior graph that several workers build;
- counterexample reconstruction after one worker reports an error while the others
  keep exploring.

Raising `workflow.tlc.workers` alone tests that code but has no oracle: a wrong
verdict agrees or disagrees with Apalache for reasons unrelated to concurrency. TLC
with one worker is a closer reference. Both checkers share the parser, the evaluator
and the value representation. A verdict difference between them points to the code
that only n workers run.

## Decision

### 1. A second TLC stage

A new checker stage `tlcn` runs TLC with n workers next to `tlc`:

| Fact | `tlc` | `tlcn` |
| --- | --- | --- |
| `[workflow] checkers` name, metadata name | `tlc` | `tlcn` |
| Configuration table | `[workflow.tlc]` | `[workflow.tlcn]` |
| Display name | `TLC` | `TLC-n` |
| Directories | `02tlc-*`, `.work/tlc-tmp` | `02tlcn-*`, `.work/tlcn-tmp` |
| Default `workers` | 1 | 4 |

`tlcn` takes the same four settings as every checker table. Its default heap, 512 MB,
equals TLC's. Its default number of workers is fixed rather than derived from the
host, so a configuration that `fuzztla init` writes checks the same way on every
machine. The CPU budget is unchanged: each `tlcn` process reserves `workers` permits,
and `floor(max-cpus / workers)` processes run at once. A run refuses a TLC table
whose `workers` exceeds `--max-cpus`.

Both TLC stages run the same backend, worker process and configuration file. Only
the stage and its settings differ.

### 2. Default checker set

`[workflow] checkers` keeps the default `["tlc", "apalache"]`, and a corpus without
`.checkers` still runs these two checkers. `tlcn` runs only when listed, for example:

- `checkers = ["tlc", "tlcn"]`: TLC against itself, without Apalache's cost;
- `checkers = ["tlc", "tlcn", "apalache"]`: both comparisons in one corpus.

The set is recorded in `.checkers` and must match on later runs, as before.

### 3. Oracles

The oracles are unchanged. Under `--how=pbt`, conformance passes when `tlc` and
`tlcn` agree. Under `--how=mt`, the metamorphic oracle also fails a counterexample
that both report.

### 4. Progress screen

With three checker stages, side-by-side checker cards need more than 117 columns.
The flow view instead shows each checker branch in two lines on a vertical spine
below the parser: the queue and work time, then the verdicts. Its width does not depend
on the number of checkers, and three branches fit the 80×24 minimum.

### 5. Architecture

This ADR revises [fuzzing-workflows.md §1][workflows]: the parser fans out to the
configured checker branches, which now include `tlcn`, and the corpus layout has the
directories `02tlcn-{inputs,pass,counterexample,fail,crash}`.

## Consequences

- **Nondeterministic first error.** With n workers, TLC explores states in an order
  that the scheduler decides. When an input reaches both an invariant violation and
  an evaluation error, `tlc` and `tlcn` may report different ones. Such an entry
  fails the aggregator with `counterexample` against `fail` without a defect. Triage
  separates such entries from defects; the oracle does not.
- **Quality gate.** The gate ranks entries by `tlc` exploration metrics. A checker
  set without `tlc` remains unsupported.
- **Triage.** `script/triager.py` does not classify `02tlcn-crash` or `tlc`/`tlcn`
  disagreements. This is follow-up work.
- **Existing corpora.** The new directories are required, and runs do not migrate
  corpus layouts. An existing corpus needs
  `mkdir <corpus>/02tlcn-{inputs,pass,counterexample,fail,crash}`.
- **Corpus database.** The `verdictPair` view gains `tlcn`, `tlcnCode` and
  `tlcnTraceLength`, so the schema version increases ([ADR 0009][adr-0009]).

## Alternatives considered

- **A worker count per entry.** Run TLC once per entry with a drawn number of
  workers. It has no reference verdict for the same input.
- **Two runs inside one TLC stage.** Run TLC twice in one stage and record both
  verdicts in one stage map. It would need a second set of verdict and metrics
  fields for one stage, while a second stage reuses fan-out, aggregation, recovery,
  occupancy limits and export without change.

[adr-0009]: 0009-corpus-database.md
[adr-0016]: 0016-metamorphic-testing.md
[workflows]: ../architecture/fuzzing-workflows.md
