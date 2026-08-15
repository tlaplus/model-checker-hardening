# 0001: Stages and workers

**Author:** OpenAI Codex GPT 5.6-sol max

**Status:** Accepted

**Date:** 2026-08-14

## Context

The fuzzing pipeline must process independently owned corpus directories while
allowing expensive stages to run concurrently. Individual input generation is
fast, but richness rejection can make the input stage CPU-bound. SANY is not
thread-safe within one JVM. Workflow runs must also terminate predictably and
recover from interruption without relying on volatile state.

## Decision

A workflow invocation runs both implemented stages concurrently:

- The input stage owns `00-inputs` and runs up to `run --max-cpus` property-based
  generation workers. It starts no more workers than missing corpus entries.
  Workers dynamically claim target ordinals from a shared atomic counter.
- The parser stage owns `01parser-pass`, `01parser-fail`, and
  `01parser-crash`. It uses persistent child JVMs because SANY has process-global
  state and is not thread-safe. Each JVM handles one request at a time and is
  replaced after a timeout, crash, or unexpected exit.

Parser scratch storage is transient corpus state under
`.work/parser-tmp/<run>/<worker>`. Each child JVM uses its worker directory as
`java.io.tmpdir` and creates one reusable SANY module resolver. The parent drains
and retains bounded child stderr, removes a worker directory after the process
exits, and removes the complete run directory before releasing the corpus lock.
Startup removes scratch data left by an interrupted invocation.

The main seed deterministically produces one seed per generator worker in worker
ID order. Each worker derives separate cohort and candidate streams from its
seed. Worker-local streams are reproducible, but dynamic target claiming,
duplicate races, and parser-capacity timing may change the aggregate corpus even
with the same worker count. Persisted raw inputs remain exactly replayable.

Files move between stage-owned directories; they are not copied as independent
jobs. The directories are the durable source of truth. A close-aware
multi-producer, multi-consumer queue accelerates handoff from generator workers
to parser workers. A fair logical CPU budget, configured with `run --max-cpus`,
bounds simultaneously active jobs across both worker pools; users do not
configure worker counts per stage. Stage capacity limits bound current directory
occupancy, while a global limit bounds unique corpus entries.
The runner may sample the stages' in-memory counters once per second for a
best-effort progress listener. These snapshots are observational and do not
replace the corpus directories as the durable source of truth. Progress is
`RUNNING` while stage workers are active and changes to `FINALIZING` before the
runner validates the complete corpus for its final summary.

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
the matching parser directory. For a crash, it also writes a UTF-8
`<sha256>.stacktrace` sidecar containing the exception stack trace or other
crash diagnostic. The sidecar is staged before the CBOR metadata commit so
startup recovery can complete an interrupted transition. Startup inventory
validation completes interrupted moves and rejects duplicate or inconsistent
entries.

A SANY return value, including its generic `ERROR` result, is a parser failure.
The crash verdict is reserved for an exception that escapes SANY, a timeout, an
abrupt worker exit, or a worker that dies while accepting an input.

An unexpected host-process failure while generating an expression or preparing
its parser specification stops the workflow. The input and stack trace are
retained under `.work/generator-crash`; these files are diagnostic artifacts,
not stage entries. When an existing corpus entry triggers the failure, the
diagnostic names both its stage path and the retained copy.

Generation stops at the global entry limit. The last generator worker closes the
handoff queue; the parser then drains it, and the run finishes when no input is
queued or in flight. Closing the input stage interrupts and joins every generator
worker. If parser result capacity is exhausted, the workflow succeeds with a
capacity-limited result and leaves unclaimed inputs in `00-inputs`. An
infrastructure failure stops all stages and returns a failure.

## Consequences

Parallel generation amortizes high rejection rates but no longer provides an
exact whole-corpus replay guarantee. Parser concurrency costs one JVM per active
parser worker but avoids SANY's shared-state races and repeated JVM startup. Disk
transitions make runs resumable, while the shared queue and CPU budget remain
process-local execution optimizations rather than persisted workflow state.
Corpus-owned scratch storage uses the corpus filesystem's capacity and bounds
temporary-directory growth by the number of active workers rather than the
number of parsed inputs.
