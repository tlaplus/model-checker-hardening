# 0001: Stages and workers

**Author:** OpenAI Codex GPT 5.6-sol max

**Status:** Accepted

**Date:** 2026-08-14

## Context

The fuzzing pipeline must process independently owned corpus directories while
allowing expensive stages to run concurrently. Richness rejection can make
input generation CPU-bound. SANY is not thread-safe within one JVM, and TLC may
crash or exhaust its heap. Workflow runs must terminate predictably and recover
from interruption without relying on volatile state.

## Decision

A workflow invocation runs four implemented stages concurrently:

- The input stage owns `00-inputs` and runs up to `run --max-cpus`
  property-based generation workers. It starts no more workers than missing
  corpus entries. Workers dynamically claim target ordinals from a shared
  atomic counter.
- The parser stage owns `01parser-pass`, `01parser-fail`, and
  `01parser-crash`. It uses persistent child JVMs because SANY has process-global
  state. Each JVM handles one request at a time and is replaced after a timeout,
  crash, or unexpected exit.
- The TLC stage consumes `02tlc-inputs` and owns `02tlc-pass`, `02tlc-fail`,
  and `02tlc-crash`. Every input runs in a fresh child JVM, which bounds leaked
  process state and permits recovery after a JVM crash or out-of-memory exit.
  The stage calls `TLC.handleParameters` and `TLC.process`; it does not call
  `TLC.main`.
- The Apalache stage consumes `02apa-inputs` and owns `02apa-pass`,
  `02apa-fail`, and `02apa-crash`. Each FuzzTLA worker lazily starts one isolated
  Apalache JVM and invokes `Tool.run` sequentially for multiple inputs. The
  release is pinned to version 0.62.0, downloaded from the official GitHub
  release with a verified SHA-256 digest, and staged beside FuzzTLA's JAR.

The implementation mirrors these responsibilities in `workflow.input`,
`workflow.parser`, `workflow.tlc`, and `workflow.apalache`. The
`workflow.checker` package owns orchestration shared by TLC and Apalache. Shared
scheduling and lifecycle machinery lives in `workflow.execution`; child-process
supervision and protocol code lives in `workflow.worker`; generated-specification
construction lives in `workflow.spec`.
The `workflow` package retains the runner, progress and result records, and the
workflow exception exposed to the CLI.

Parser and checker scratch storage is transient corpus state under
`.work/{parser,tlc,apalache}-tmp/<worker>`. Each child uses its worker directory
as `java.io.tmpdir`. A parser process creates one reusable SANY module resolver.
Each TLC process gets a fresh job directory; a persistent Apalache process gets
a fresh directory for every `Tool.run` invocation and retains at most the current
one. An isolated worker carries its binary control protocol over a private,
token-authenticated loopback connection. The parent drains stdout and stderr
separately because native checker output can bypass JVM stream redirection. The
parent removes all scratch trees before releasing the corpus lock. Startup
removes scratch data left by an interrupted invocation.

The main seed deterministically produces one seed per generator worker in worker
ID order. Each worker derives separate cohort and candidate streams from its
seed. Worker-local streams are reproducible, but dynamic target claiming,
duplicate races, and stage-capacity timing may change the aggregate corpus.
Persisted raw inputs remain exactly replayable.

Files move between stage-owned directories, with one exception: a parser pass is
copied to both `02tlc-inputs` and `02apa-inputs` before the
source is removed. The two copies retain one logical identity and count once
towards the global limit. Startup completes partial fan-out transactions and
checks that both copies have identical input, generation metadata, and parser
metadata. The directories are the durable source of truth.

A corpus is valid only when the complete current directory layout and every
configuration table are present. Runs do not migrate older layouts or formats.

Close-aware multi-producer, multi-consumer queues accelerate the input-to-parser
and parser-to-checker handoffs. A downstream-priority logical CPU budget, configured
with `run --max-cpus`, bounds simultaneously active work across all stages. TLC
has priority over parsing, which has priority over generation. Requests are FIFO
within one priority. A waiting higher-priority request reserves partially
available permits until it can start, so smaller upstream requests cannot starve
a multi-permit checker request. Already-running work is not preempted; strict
reservation may therefore leave some CPUs briefly idle while permits accumulate.

Generator and parser work reserve one permit. Each TLC invocation reserves
`workflow.tlc.workers` permits, matching its internal TLC worker count; that
value defaults to one and must not exceed `--max-cpus`. The number of concurrent
TLC child processes is `floor(max-cpus / workflow.tlc.workers)`. Each Apalache
invocation reserves one permit. `workflow.apalache.workers` configures the
number of FuzzTLA workers. Corpus initialization sets it to half the processors
visible to the JVM, rounded down with a minimum of one. The persisted value must
not exceed `--max-cpus` when the workflow runs.
TLC and Apalache requests have equal checker priority and FIFO ordering in the
shared CPU budget. Stage capacity limits bound current result-directory
occupancy, while a global limit bounds unique corpus entries.

The runner may sample in-memory counters once per second for a best-effort
progress listener. These snapshots are observational and do not replace corpus
directories as the durable source of truth. Progress is `RUNNING` while stage
workers are active and changes to `FINALIZING` before the final inventory.

For an expression `E`, the parser stage constructs this typed `TlaModule`,
renders it with Apalache's annotation-aware pretty writer, and checks it with
SANY:

```tla
----------------------------- MODULE FuzzInput -----------------------------
EXTENDS Integers, Sequences, FiniteSets, TLC, Apalache, Variants

VARIABLE
    \* @type: <type of E>;
    exprValue

Init ==
    exprValue = E

Next ==
    UNCHANGED exprValue

Inv ==
    exprValue = E

=============================================================================
```

The expression subtree is deep-copied for `Inv`. A parser worker records its
result in `stages.parser`, atomically updates the CBOR entry, and atomically moves
it to the matching parser directory. A crash also writes a UTF-8
`<sha256>.stacktrace` sidecar. The sidecar is staged before the metadata commit
so startup can complete an interrupted transition.

A SANY return value, including its generic `ERROR` result, is a parser failure.
The crash verdict is reserved for an exception that escapes SANY, a timeout, an
abrupt worker exit, or a worker that dies while accepting an input.

TLC receives a fixed configuration containing `INIT Init`, `NEXT Next`, and
`INVARIANT Inv`; deadlock checking and trace-exploration specification generation
are disabled. Since `Inv` repeats the expression used by `Init`, a reachable
invariant violation is a TLC failure. TLC's error constant is first mapped with
`EC.ExitStatus.errorConstantToExitStatus`, except `TLC_INTEGER_TOO_BIG`, which is
explicitly a failure because it reports an unsupported input value rather than a
tool crash. Property violations map to shared failure code 12, evaluation
failures and `TLC_INTEGER_TOO_BIG` to 75, and specification or configuration
parse failures to 150. Other statuses are crashes. An exception, timeout, abrupt
child exit, stack overflow, or out-of-memory exit is also a crash. Worker startup,
protocol, corpus, and orchestration errors are workflow infrastructure failures.
[ADR 0003](0003-checker-failure-codes.md) defines the shared checker taxonomy and
bounded diagnostic detail.

Apalache receives the same `Init`, `Next`, and `Inv` names, checks length zero,
and disables deadlock checking. Exit status 0 is a pass. Exit statuses 12, 75,
120, and 150 are failures with the corresponding shared code. Any other exit
status, timeout, abrupt exit, or fatal JVM error is a crash. A process-start
failure is workflow infrastructure failure. Each FuzzTLA worker calls `Tool.run`
one input at a time in its persistent child JVM. Different workers use separate
JVMs because Logback remains process-global. A crash retires the child; the next
input lazily starts a replacement. This preserves per-worker heap limits and
isolates the FuzzTLA host from fatal errors.

An unexpected host-process failure while generating an expression or preparing
a specification stops the workflow. The input and stack trace are retained
under `.work/generator-crash`; these files are diagnostic artifacts, not stage
entries.

Generation stops at the global entry limit. The last generator worker closes the
parser queue; the last parser worker closes both checker queues. The run finishes
when all four stages have no queued or in-flight jobs. If parser or checker result
capacity is exhausted, the workflow succeeds with a capacity-limited result and
leaves unclaimed upstream inputs in their owning directories. An infrastructure
failure stops all stages and returns a failure.

## Consequences

Parallel generation amortizes high rejection rates but no longer provides an
exact whole-corpus replay guarantee. Parser concurrency costs one JVM per active
worker but avoids SANY's shared-state races and repeated startup. TLC pays one
JVM startup per checked input in exchange for isolation and bounded heap use.
Apalache amortizes JVM startup across healthy inputs while retaining one isolated
JVM per configured worker. A timeout or crash pays the startup cost again for the
replacement worker.
Fan-out doubles storage for parser passes until aggregation is implemented. Disk
transitions make runs resumable, while queues and the CPU budget remain
process-local execution optimizations. Downstream priority drains checker
backlogs before spending more CPU on raw inputs. Generation or parsing may wait
indefinitely while downstream work remains continuously queued; this starvation
is intentional.
