# 0033: A generic checker for TLC- and Apalache-compatible tools

**Authors:** Igor Konnov and Claude

**Status:** Implemented

**Date:** 2026-11-24

**Implementation.** Stacked branches:
- `make manyan-build` and `signatures/manyan-defects.toml`: `igor/generic-checker-build`;
- the `[generic]` table and `init --generic FILE`: `igor/generic-checker-config`;
- `CorpusStage.GENERIC`, `workflow.generic`, the database view, the campaign report
  and the triager: `igor/generic-checker-stage`.

A 60-entry pbt campaign with `checkers = ["tlc", "generic"]` and Manyan ran end to
end: Manyan took 2 s of worker time against TLC's 37 s, the aggregator agreed on 55
entries and differed on one, a `spec_eval` failure of Manyan where TLC passes.

The first instance is [Manyan](https://github.com/soasme/manyan), `v0.17.0`
(`a9ca4f38`).

## Context

FuzzTLA runs three checker branches: `tlc`, `tlcn` and `apalache`
([ADR 0016][adr-0016] §5, [ADR 0018][adr-0018]). Each one is a `CorpusStage` constant,
a `CheckerProfile`, a `CheckerBackends` factory, and an adapter package that drives
the tool inside a child JVM.

Some independent model checkers claim compatibility with TLC or Apalache. They accept
the same inputs, configuration and command line, and they report verdicts through the
same exit statuses. Manyan is one of them. It is an MIT-licensed checker written in C11,
and its README describes it as follows:
- TLC 1.8.0 is its oracle for explicit-state checking, and Apalache 0.63.0 is its oracle
  for `-symbolic`.
- It reads TLC's `.cfg` and accepts TLC's `-config`, `-workers` and `-deadlock`.
- It exits with TLC's statuses: 0, 10–14, 75–77, 150, 151 and 255.
- Safety counterexamples are shortest.
- `-symbolic -length N` is bounded model checking. It supports no fairness.

A tool like this is a cheap conformance target. If it is compatible, FuzzTLA needs no
tool-specific adapter to compare it with the checker it claims to match. Giving each
such tool its own stage would repeat the stage, profile, paths and adapter every time.

## Decision

### 1. One configurable checker branch, `generic`

`CorpusStage.GENERIC` (`metadataName` `generic`) is a checker branch like the others.
It owns `02gen-{inputs,pass,counterexample,fail,crash}` and `.work/generic-tmp`. A corpus
runs it when `[workflow] checkers` lists `generic`, for example `["tlc", "generic"]`.
`[workflow.generic]` takes the usual limits: `max_entries`, `timeout_sec` and
`workers`. The generic checker ignores `max_heap_mb`.

The tool is described by a new top-level table:

```toml
[generic]
name = "Manyan"
compatibility = "tlc"
executable = "tools/manyan"
arguments = ["-workers", "1", "-deadlock", "-config", "{config}", "{module}"]
```

| key | meaning |
| --- | --- |
| `name` | The tool's name in progress output, diagnostics and reports. |
| `compatibility` | `tlc` or `apalache`: the checker whose conventions the tool follows (section 2). |
| `executable` | The tool's path, relative to the configuration file. `init --generic` copies it into `tools/` (section 4). |
| `arguments` | The command-line arguments. Each one may contain placeholders (section 3). |

The four keys go together: a table that sets some of them is a configuration error.
Loading also rejects an unknown `compatibility` or placeholder. Listing `generic` without
a `[generic]` table is a configuration error. A corpus runs at most one generic checker.
Comparing two compatible tools takes two corpora.

### 2. Compatibility selects conventions, not code paths

`GenericCompatibility` is an enum in `config`, which reads it from the table. The
conventions below live in `workflow`, above `config`, so the backend keys them by the
enum in one map, and loading fails if a constant has none, as `CheckerBackends` does
for the checker stages. No `switch` elsewhere names a compatibility:

| fact | `tlc` | `apalache` |
| --- | --- | --- |
| configuration written as `{config}` | `TlcConfiguration.text`: `SPECIFICATION`, `INVARIANT Inv`, `PROPERTY` | `INIT`, `NEXT`, `INVARIANT Inv`, `PROPERTY Liveness` (or the side's property in a property run), the entry points `ApalacheArguments` passes |
| exit status → verdict | `TlcOutcomeClassifier.classifyCommandLine`: TLC's exit statuses, plus integer overflow at 255 as `spec_eval` (probe P2) | `ApalacheOutcomeClassifier.classify` |
| deadlock | the tool's own flag; TLC's is `-deadlock` | not checked, as with `--no-deadlock` |
| unrolling length `{length}` | not meaningful | `CheckRequest.unrollingLength()` |

Both conventions write the module as TLA<sup>+</sup> source (`SpecText.render`), and
neither uses Apalache's JSON IR. Both classifiers are reused as they are. They become
visible to the new package, and a test pins the exit statuses Manyan documents to TLC's
table.

A metamorphic module whose properties are checked apart takes up to three invocations
([ADR 0027][adr-0027] §4, [ADR 0029][adr-0029] §1). `PropertyRunsVerdict.fold` combines
them, as it does for TLC.

### 3. Placeholders are a closed set

`GenericPlaceholder` is an enum. `{name}` in an argument expands as follows:

| placeholder | value |
| --- | --- |
| `{dir}` | the invocation's fresh working directory |
| `{module}` | `{dir}/FuzzInput.tla` |
| `{module_name}` | `FuzzInput` |
| `{config}` | `{dir}/FuzzInput.cfg` |
| `{init}`, `{next}` | the requested side's entry points (`FuzzInputModule.entryPoints`) |
| `{inv}` | `Inv`, or `Inv,Step` for a relation with an action invariant |
| `{temporal}` | `Liveness`, or the side's property in a property run |
| `{length}` | `CheckRequest.unrollingLength()` |
| `{ir}` | `{dir}/FuzzInput.json`, the module as Apalache's JSON IR (`ApalacheIrJson`), written only when an argument names it (probe P4) |

Loading the configuration rejects an unknown placeholder, and arguments that name both
`{module}` and `{ir}`: a stage passes one rendering of the module to its tool, TLA<sup>+</sup>
source or, when an argument names `{ir}`, Apalache's JSON IR. A placeholder whose value
the request lacks, such as `{temporal}` for an invariant-only request, expands to an
empty argument. Placeholders are expanded inside a single argument and never split one into
several, so there is no quoting to get wrong.

### 4. One native process per input

`GenericCheckerBackend` implements `ToolBackend` with the one-process-per-input
lifecycle that TLC uses, except that the process is the tool itself rather than a child
JVM. For each invocation, it:
1. creates a fresh directory under the stage's scratch directory, and writes into it the
   module, `{config}`, and the bundled `Apalache.tla` and `Variants.tla` (probe P1);
2. runs the executable through `OneShotTool.attempt`, which bounds the captured output,
   kills the process on timeout or interruption, and reports a timeout in its result;
3. classifies the exit status by the compatibility.

A timeout becomes a crash whose diagnostic reads `<name> timed out after PT30S`, the
same form as the JVM workers' ([ADR 0021][adr-0021]), so the triager files it under
`TIMEOUT`. A native tool leaves no thread dump.

`ToolBackend`'s javadoc names this lifecycle. `cpuPermits` is 1, so the tool's own
parallelism has to be set to one worker through `arguments`.

`fuzztla init --generic FILE` reads a file that holds exactly a `[generic]` table, with
`executable` relative to FILE. It copies the executable into `tools/` under its own
name, keeping its permissions, and writes the table with the copied path, as `init`
does for the JARs ([ADR 0025][adr-0025] §3). `init` refuses an executable that is
missing, is not executable, or shares the name of a tool JAR. A table added to
`config.toml` by hand is read like any other, but its executable is not copied. A
corpus with a native tool runs only on the platform that built the tool.

### 5. Oracle participation

A generic checker records only a verdict and a failure code. It records no
`ExplorationMetrics`. Every oracle therefore compares its verdict, and two checks are
skipped:
- **The state-count agreement of [ADR 0019][adr-0019].** `Oracle.agree` skips a
  checker without a `DISTINCT_STATES` count.
- **The trace-length check of the graph oracle ([ADR 0023][adr-0023] §5).**
  `ExpectedOutcome.admits` accepts a counterexample without a recorded length.

`GENERIC` declares `CounterexampleSearch.ANY`, which is the right declaration for a
tool whose search order is unknown. The quality gate ranks on TLC's metadata and
needs TLC in the checker set, as it does now.

### 6. Building Manyan

`make manyan-build` clones `MANYAN_REPO` at the pinned `MANYAN_REV` into
`target/checkers/manyan-src` and builds it with `make manyan PGO_TOOLS=`. This is a
plain build: no profile-guided training run, and none of Manyan's own test suites. The
target stages the result as `target/checkers/manyan-<rev12>`. Findings cite the
revision, as they cite TLC's and Apalache's commits.

The repository ships two files for `init --generic`, pre-filled for Manyan:
- `generic/manyan.toml` compares it with TLC;
- `generic/manyan-symbolic.toml` compares its bounded mode with Apalache, which rejects
  most modules (probe P4).

Their `executable` is the staged binary, `../target/checkers/manyan-a9ca4f38028f`, and
follows `MANYAN_REV`. A `config.toml` without a generic checker shows the table
commented out, as Manyan in TLC mode:

```toml
[generic]
# name = "Manyan"
# compatibility = "tlc"
# executable = "tools/manyan-a9ca4f38028f"
# arguments = ["-workers", "1", "-deadlock", "-config", "{config}", "{module}"]
```

## Probe

**Setup.**
- Manyan `v0.17.0` (`a9ca4f38`), a plain build (`make manyan PGO_TOOLS=`).
- FuzzTLA `f8e3696a`.
- TLC `7e64fd03` from corpus87's `tools/`, and TLC `d8fb231d` for the gbt corpus.
- Entries: 430 pbt entries of corpus87, drawn from the TLC verdict directories (pass
  100, counterexample 200, fail 100, crash 30). Also the 12 entries of a small gbt
  corpus. No mt corpus was available.

**Method.**
- A scratch class rendered each entry as the TLC stage does: `SpecText.render` and
  `TlcConfiguration.texts`.
- Manyan ran it with `-workers 1 -deadlock -config FuzzInput.cfg FuzzInput` under a
  60-second limit.
- Manyan's exit status was compared with the TLC verdict and failure code recorded in
  each entry's envelope.

**P1. Standard modules.** Manyan resolves neither `Apalache` nor `Variants`, and every
entry extends both. All 430 entries failed with exit 150 (`Cannot find source file for
module Variants`). After `Apalache.tla` and `Variants.tla` were copied from
`fuzztla.jar` into the working directory, all 430 entries parsed or failed on their own
merits. **Decision:** the backend writes `StandardModuleResources.BUNDLED` into `{dir}`
before every invocation.

**P2. TLC compatibility, pbt.** TLC's verdict against Manyan's exit status:

| TLC | Manyan agrees | Manyan differs |
| --- | --- | --- |
| pass (100) | 68 (exit 0) | 13 parse (150), 18 evaluation error (255), 1 tautology (77) |
| counterexample (200) | 156 (exit 12 or 13) | 27 parse (150), 10 evaluation error (255), 5 no error (0), 2 tautology (77) |
| fail, `spec_eval` (94) | 61 (exit 75) | 22 parse (150), 11 at exit 255 (10 `TLC can't handle a number this big.`) |
| fail, `parse` (6) | 5 (exit 150 or 151) | 1 evaluation error (75) |
| crash (30) | — | 18 parse (150), 10 at 255, 2 at 75 |

On the 12 gbt entries, Manyan agreed with TLC on 9. The other 3 were evaluation errors
at exit 255 where TLC reported `spec_eval`.

Most differences fall into a few groups:
- **Parse differences: 82 entries (19%).** Manyan rejects them at parsing, and SANY
  accepted all 82 in the parser stage.
  - 64 fail with `Encountered "FALSE": expected a subscript`. These are actions of the
    form `[A]_FALSE`.
  - 18 fail with `Label … must contain formal parameter …`.
- **Exit 255 for evaluation errors.** TLC's command line also exits 255 for integer
  overflow. FuzzTLA records `spec_eval` for it only because `TlcOutcomeClassifier`
  special-cases `EC.TLC_INTEGER_TOO_BIG`, a check it can make in process. **Decision:**
  the `tlc` compatibility classifies exit 255 with `TLC can't handle a number this big.`
  in its diagnostics as `spec_eval`, as `ApalacheOutcomeClassifier` matches input
  limitations by message. Every other exit 255 stays a crash.
- **Liveness: 5 entries.** TLC reports a temporal violation and Manyan reports no error
  (`026915cc`, `3e404d45`, `4d6da981`, `9f60da69`, `cc0c3efe`). A rerun of TLC's command
  line confirmed exit 13 on all five.
- **Evaluation strictness: 31 entries.** Manyan raises an evaluation error where TLC
  passes or finds a counterexample. In `01492b23`, TLC's command line prints the same
  `%` error as Manyan but exits 0, and FuzzTLA recorded a pass. That one may be a TLC
  defect.

**P3. State counts.** Of the 68 entries that both checkers passed, Manyan's
`distinct` count (from `-result`) equals TLC's recorded `distinctStates` on 65. The
other 3 (`27db0ae5`, `30f4921b`, `96a4ca0e`) check a temporal property, and there TLC's
recorded count is twice Manyan's (12 against 6, 2 against 1). On `27db0ae5` and
`30f4921b`, a rerun of TLC's command line prints Manyan's count. So FuzzTLA's TLC
metric differs from TLC's printed count on liveness runs, and Manyan does not. The
counts are a usable oracle, but restoring them is still deferred (section 5).

**P4. Apalache compatibility through source.** For `-symbolic -length N`, each entry
got a `.cfg` with `INIT Init`, `NEXT Next`, `INVARIANT Inv`, and `PROPERTY Liveness`
when the request has one. Manyan's type checker rejected 310 of 430 entries (exit 120),
most with `Annotation required … ((a, b) => <<a, b>>) or ((a, a) => Seq(a))` on a
`LET` operator or a `LAMBDA`. Another 82 failed parsing, for the reasons in P2. The
Apalache stage never typechecks source: it receives typed JSON IR (`ApalacheIrJson`).
The rendered source carries type annotations only on variables and constants, which is
not enough for a checker that infers types from source. **Decision:**
- `apalache` compatibility stays in the design, with one more placeholder, `{ir}`. It
  is the module as Apalache's JSON IR, written only when an argument names it.
- The Manyan example for this mode is documented as not yet usable. Annotating the
  rendered source's operators is out of scope.

The action-invariant question (whether `-symbolic` accepts `Step` as an `INVARIANT`)
was not reached: no metamorphic corpus was available, and typechecking failed first.

**P5. Instance-linked libraries.** Neither corpus links a library by `INSTANCE`
([ADR 0014][adr-0014]). This is still open. Until it is probed, loading a configuration
refuses `generic` in `checkers` together with a `link = "instance"` library.

**Consequence for campaigns.** About 19% of pbt entries would differ on parsing alone.
Known-defect signatures for `[A]_FALSE` and parameterized labels have to exist before
a campaign. Otherwise the aggregator's failures are dominated by these two causes.

## Implementation notes

- **Output capture.** `OneShotTool` read a tool's output before draining the capture
  thread, so a tool that ends at once, as Manyan does, could lose its output. It now
  drains first. The race affected `OneShotTool.run` before, too.
- **Bundled modules.** Only `Apalache.tla` and `Variants.tla` are written, from FuzzTLA's
  class path. `Integers` is left to the tool.
- **Shared Apalache entry points.** `ApalacheArguments.invariants` and `temporal` give the
  invariants and the temporal property of a request, which the Apalache command line,
  `{inv}`, `{temporal}` and the `apalache` configuration file all use.

## Alternatives considered

- **A stage per tool (`MANYAN`).** Each additional tool would repeat the stage, paths,
  profile and adapter. Tool-specific metrics parsing would come with it, but nothing
  else needs it yet.
- **A command template with one string.** Splitting a string into arguments would need
  quoting rules. An argument array has none.
- **Several generic checkers per corpus.** That would make stages dynamic, which
  `CorpusStage`, the corpus layout and the database views do not support. Two corpora
  serve the same purpose.
- **Metrics from the tool's JSON output.** Manyan's `-result` and `-dumpTrace json`
  would restore the ADR 0019 and ADR 0023 checks, but the formats are tool-specific.
  This is deferred: a later `metrics` key could name a format.

## Consequences

- **Adding a compatible tool needs no code,** only a `[generic]` table.
- **The comparison is weaker than TLC's against Apalache's.** It compares verdicts but
  not state counts or trace lengths.
- **The database's `verdictPair` view gains `generic` columns,** which bumps the schema
  version. The campaign report and the triager learn the stage.
- **Corpora become platform-specific** when they run a native tool.
- **Architecture.** [fuzzing-workflows.md][workflows] gains a checker branch whose tool
  comes from configuration. Every other branch is fixed at build time.

## Implementation phases

1. `make manyan-build`, and known-defect signatures for the parse differences of
   probe P2.
2. The `[generic]` table: `GenericCheckerConfig`, `GenericCompatibility`,
   `GenericPlaceholder`, the commented example, and the shipped examples.
3. `init --generic FILE` copies the executable into `tools/`.
4. `CorpusStage.GENERIC`, its paths, profile and backend.
5. The database view, the campaign report and the triager.

[adr-0014]: 0014-per-checker-library-linking.md
[adr-0016]: 0016-metamorphic-testing.md
[adr-0018]: 0018-parallel-tlc-stage.md
[adr-0019]: 0019-state-count-agreement.md
[adr-0021]: 0021-timeout-diagnostics.md
[adr-0023]: 0023-graph-based-testing.md
[adr-0025]: 0025-self-contained-corpora.md
[adr-0027]: 0027-temporal-metamorphic-testing.md
[adr-0029]: 0029-apalache-temporal-metamorphic-testing.md
[workflows]: ../architecture/fuzzing-workflows.md
