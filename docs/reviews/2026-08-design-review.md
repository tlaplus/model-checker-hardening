# Design review, August 2026

**Author:** Claude Opus 5

**Scope:** All of `src/main/java` at commit `04cb42d` (~10.7k lines, 156 files).
This document records design and coupling findings and the refactoring programme they
motivate. It describes the state before the refactoring; the ADRs and architecture documents
remain the normative description of the system.

## 1. Assessment

The code is disciplined. Configuration and result types are records with compact-constructor
invariants. There is no static mutable state outside the child-process entry points, which
own their JVM by construction. No `catch (Throwable)` exists; `InterruptedException` always
restores the interrupt flag; cleanup failures are attached with `addSuppressed`. Top-level
packages are strictly layered `checker`, `gen` → `config` → `corpus` → `workflow` → `cli`
with no cycles, and `workflow`'s subpackages layer cleanly onto `workflow.worker` and
`workflow.execution`. `CheckerBackend`/`CheckerWorker`, `WorkflowStage`, `ToolWorkerProtocol`
and the `Generator`/`Draw` combinator core are well-chosen abstractions.

The findings below are therefore not about correctness hygiene. They are about *where
knowledge lives*: which facts a maintainer must find, and how many unrelated files one
conceptual change touches.

## 2. Stage identity is not a value

`parser`, `tlc` and `apalache` appear as named fields, named accessors, or triplicated
expressions in `CorpusInventory`, `CorpusRunStatistics`, `CorpusRunStatisticsCodec`,
`WorkflowMetrics`, `WorkflowControl`, `WorkflowProgress`, `WorkflowRunSummary`,
`WorkflowRunner`, `RunTable`, `CorpusPath` and the `config` records.

`CorpusInventory` flattens three stages by three verdicts into twelve positional counters and
nine hand-written accessors. `WorkflowMetrics` exposes four named `ElapsedTimeAccumulator`
getters. `WorkflowRunner.runStages` spans 143 lines and eleven parameters largely because it
writes the same wiring three times.

`CorpusStageLayout` already is the stage abstraction — it owns the metadata name, display
name, and input, scratch and result paths — but it is package-private in `corpus` and unused
elsewhere. `fuzzing-workflows.md` describes aggregator, quality-gate and mutator stages as
future work; adding one today is shotgun surgery across roughly twelve types.

**Direction.** Promote the stage to a public enum and key inventories, clocks, progress and
summaries by it.

## 3. Two classes hold four responsibilities each

`CorpusDirectory` is 1123 lines. It is simultaneously the layout bootstrap, the config
reader, the statistics store, the cross-process lock, the scratch factory, the entry store
and hasher, a stage-transition transaction coordinator (`completeStage`, 97 lines, seven
parameters, a hand-tracked rollback flag), and a corpus recovery and validation engine
(roughly 500 lines from `recoverTransitions` onward). The "create temp file, write,
`ATOMIC_MOVE`, delete on failure" sequence is written out five times, only one of which is
the existing `replaceAtomically` helper.

`IsolatedWorkerProcess` is 552 lines covering process lifecycle, socket protocol framing,
timeout supervision and filesystem cleanup; its `start` factory alone is 187 lines with six
parameters.

**Direction.** Split each along its responsibility seams, leaving a façade.

## 4. Stage worker loops are duplicated three ways

`ParserStage` (204 lines) and `CheckerStage` (182 lines) share roughly 70 % of their code:
byte-identical summary records, an identical verdict-counter `switch`, an identical
optimistic occupancy CAS loop, and the same worker-loop skeleton — take from the queue,
acquire CPU permits, time the job, release in `finally`, restore the interrupt flag,
otherwise report to `WorkflowControl`. `PbtStage` carries a third copy of the failure
handling. `WorkflowControl` offers three predicates — `shouldStopProducing`,
`shouldAbortParsing`, `shouldStopChecking` — with one identical body.

Duplication of this kind drifts. `CheckerStage` closes a crashed worker explicitly while
`ParserStage` only drops the reference; both are correct today only because
`IsolatedWorkerProcess` closes itself on a crash verdict.

The three child-process entry points (`ParserWorkerMain`, `TlcWorkerMain`,
`ApalacheWorkerMain`) likewise reimplement one connect–handshake–loop–classify skeleton with
no shared template.

**Direction.** Extract the shared machinery into `workflow.execution`; give the stage
constructors a shared environment record instead of 8, 12 and 13 positional parameters.

## 5. Copied helpers

`diagnostic(Throwable)` — five lines returning a message or the exception's simple name —
exists independently in seven places across four packages. `deleteRecursively(Path)` exists
in three. `ParserStageSummary` and `CheckerStageSummary` differ only in an exception message.
`TlcStageConfig` and `ApalacheStageConfig` differ only in a default worker count. The
nonnegative-`maximumEntries` guard is written out five times.

**Direction.** One home each, in a leaf package that everything may depend on.

## 6. Stringly-typed and positionally-fragile boundaries

`CorpusVerdict` is an enum, but it is package-private, so `completeParser` and
`completeChecker` take a `String` verdict and convert it back. `StageOutcome` carries both a
wire protocol code and a corpus verdict *string*.

The CBOR codecs hand-write definite-length map sizes — `writeStartObject(null, 3 +
failureFields + extraFields)` — so a field added without matching arithmetic produces
undecodable output with no compile-time signal. `withStageMetadata` mixes Jackson's tree
model with the streaming API in one operation. Nested field names such as `"verdict"`,
`"code"` and `"detail"` are string literals repeated in up to four places, including a
fourth independent list in `isStageMetadataField`. `TomlConfig.render` formats a text block
with 27 positional arguments.

**Direction.** Type the boundaries; share one required-field CBOR object reader; name the
nested fields once.

## 7. Extending the generator is not local

Adding one expression form requires editing the family enum, the family factory's `switch`,
and — for anything scope-sensitive — an enum-identity `if` chain inside
`IrExprGenFactory.isApplicable` that names `GeneralExpressionKind.NAME` and
`OPERATOR_APPLICATION` directly. `ir-generators.md` §9 documents the first two and not the
third. The six families are enumerated in three separate places: the `permits` clause,
`ExpressionKinds.buildCatalog`, and the dispatch `switch` in `IrExprGenFactory`, which cannot
be a virtual call because each family's `mkGen` has a different signature.

The catalog's concatenation order *is* the corpus byte encoding. Only a comment protects it,
so a routine enum reorder silently reinterprets every stored input.

`IrExprGenFactory.mkGen` scans the ~120-entry catalog twice per generated node, evaluating
applicability each time, on the path that rejection sampling makes CPU-bound.

**Direction.** Move per-kind facts onto the kind, give the families one interface, pin the
catalog order with a test, and memoize applicability per type.

## 8. Smaller observations

- `workflow.execution.WorkflowMetrics` imports `workflow.input.PbtStageSummary` — the only
  upward dependency in the tree — because it bundles per-stage clocks with generator
  statistics.
- `corpus` depends on `gen` solely so `recoverAndValidate` can re-decode bytes to validate an
  entry, and on `config` solely for `readConfig`. Both are validation and parsing policy
  living in the storage layer.
- `CheckerBackend` hides two different worker lifecycles: TLC starts a fresh JVM per input,
  Apalache keeps one until it crashes. `CheckerStage` depends on the difference; the interface
  does not document it.
- `CpuBudget.acquire` polls its cancellation predicate every 100 ms because `WorkflowControl`
  never signals the budget, costing up to 100 ms of shutdown latency per waiting worker.
- `PrintCommand` renders TLA+ and formats corpus envelopes inline, so neither is reusable or
  testable without picocli. `RunTable.progress` and `RunTable.finished` are line-for-line
  twins. Seven CLI call sites hand-write the same `printf`-then-`return SOFTWARE` block.
- `PbtStage.generateInputs` is 105 lines at five levels of nesting with a boolean flag
  threaded through three nested `finally` blocks to decide one semaphore release.

## 9. Deliberately left alone

`WorkQueue` and `CpuBudget` are hand-rolled concurrency primitives, but each earns it:
close-then-drain semantics and reservation-based priority are not available from
`java.util.concurrent`. The `Generator`/`Draw`/`BasicGenerators` core is small, documented and
free of engine coupling. `WorkflowRunner` as the single composition root is the right shape;
only its size is a problem.
