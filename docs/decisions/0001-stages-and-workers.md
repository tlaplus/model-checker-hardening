# 0001: Stages and workers

**Author:** OpenAI Codex GPT 5.6-sol max

**Status:** Accepted

**Date:** 2026-08-14

## Context

The fuzzing pipeline must process independently owned corpus directories while
allowing expensive stages to run concurrently. Input generation is cheap, but
SANY is not thread-safe within one JVM. Workflow runs must also terminate
predictably and recover from interruption without relying on volatile state.

## Decision

A workflow invocation runs both implemented stages concurrently:

- The input stage owns `00-inputs` and has one property-based generation worker.
  Generation remains sequential, making the candidate stream independent of
  parser concurrency.
- The parser stage owns `01parser-pass`, `01parser-fail`, and
  `01parser-crash`. It uses persistent child JVMs because SANY has process-global
  state and is not thread-safe. Each JVM handles one request at a time and is
  replaced after a timeout, crash, or unexpected exit.

Files move between stage-owned directories; they are not copied as independent
jobs. The directories are the durable source of truth. A close-aware in-memory
queue accelerates handoff from the input stage to parser workers. A fair logical
CPU budget, configured with `run --max-cpus`, bounds simultaneously active jobs;
users do not configure worker counts per stage. Stage capacity limits bound
current directory occupancy, while a global limit bounds unique corpus entries.

For an expression `E`, the parser stage constructs this typed `TlaModule`,
renders it with Apalache's pretty writer, and checks the result with SANY's
default syntax, semantic, level-checking, and linting settings:

```tla
----------------------------- MODULE FuzzInput -----------------------------
EXTENDS Integers, Sequences, FiniteSets, TLC, Apalache, Variants

VARIABLE exprValue

Init ==
    exprValue = E

Next ==
    UNCHANGED exprValue

Inv ==
    exprValue = E

=============================================================================
```

The expression subtree is deep-copied for `Inv`; it is not factored through
another operator. A parser worker records its result in
`stages.parser`, atomically updates the CBOR entry, and atomically moves it to
the matching parser directory. Startup inventory validation completes an
interrupted move and rejects duplicate or inconsistent entries.

Generation stops at the global entry limit. The parser then drains the closed
queue, and the run finishes when no input is queued or in flight. If parser
result capacity is exhausted, the workflow succeeds with a capacity-limited
result and leaves unclaimed inputs in `00-inputs`. An infrastructure failure
stops all stages and returns a failure.

## Consequences

Parser concurrency costs one JVM per active parser worker but avoids SANY's
shared-state races and repeated JVM startup. Disk transitions make runs
resumable, while the shared queue and CPU budget remain process-local execution
optimizations rather than persisted workflow state.
