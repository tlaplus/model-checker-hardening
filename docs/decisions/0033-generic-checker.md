# 0033: A generic checker for TLC- and Apalache-compatible tools

**Authors:** Igor Konnov and Claude

**Status:** Proposed

**Date:** 2026-11-24

Nothing in this ADR is implemented yet. The first instance is
[Manyan](https://github.com/soasme/manyan), `v0.17.0` (`a9ca4f38`).

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
| `executable` | The tool's path, relative to the configuration file. `init` copies it into `tools/` (section 4). |
| `arguments` | The command-line arguments. Each one may contain placeholders (section 3). |

Listing `generic` without a `[generic]` table is a configuration error. A corpus runs at
most one generic checker. Comparing two compatible tools takes two corpora.

### 2. Compatibility selects conventions, not code paths

`GenericCompatibility` is an enum. Each constant holds the facts that distinguish it,
so no `switch` elsewhere needs to know about them:

| fact | `tlc` | `apalache` |
| --- | --- | --- |
| configuration written as `{config}` | `TlcConfiguration.text`: `SPECIFICATION`, `INVARIANT Inv`, `PROPERTY` | `INIT`, `NEXT`, `INVARIANT Inv`, `PROPERTY Liveness` (or the side's property in a property run), the entry points `ApalacheArguments` passes |
| exit status → verdict | `TlcOutcomeClassifier.classifyExitStatus` | `ApalacheOutcomeClassifier.classify` |
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

Loading the configuration rejects an unknown placeholder. A placeholder whose value the
request lacks, such as `{temporal}` for an invariant-only request, expands to an empty
argument. Placeholders are expanded inside a single argument and never split one into
several, so there is no quoting to get wrong.

### 4. One native process per input

`GenericCheckerBackend` implements `ToolBackend` with the one-process-per-input
lifecycle that TLC uses, except that the process is the tool itself rather than a child
JVM. For each invocation, it:
1. creates a fresh directory under the stage's scratch directory, and writes the module
   and `{config}` into it;
2. runs the executable through `OneShotTool.run`, which bounds the captured output and
   kills the process on timeout or interruption;
3. classifies the exit status by the compatibility.

A timeout becomes a crash whose diagnostic reads `<name> timed out after PT30S`, the
same form as the JVM workers' ([ADR 0021][adr-0021]), so the triager files it under
`TIMEOUT`. A native tool leaves no thread dump.

`ToolBackend`'s javadoc names this lifecycle. `cpuPermits` is 1, so the tool's own
parallelism has to be set to one worker through `arguments`.

`init` copies `executable` into `tools/` and rewrites the path, as it does for the JARs
([ADR 0025][adr-0025] §3), keeping the executable bit. A corpus with a native tool runs
only on the platform that built the tool.

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
revision, as they cite TLC's and Apalache's commits. `init` writes the `[generic]`
table commented out, pre-filled for Manyan in both modes:

```toml
# [generic]
# name = "Manyan"
# compatibility = "tlc"
# executable = "../target/checkers/manyan-a9ca4f38028f"
# arguments = ["-workers", "1", "-deadlock", "-config", "{config}", "{module}"]
#
# Manyan's bounded mode, compared with Apalache:
# compatibility = "apalache"
# arguments = ["-symbolic", "-length", "{length}", "-config", "{config}", "{module}"]
```

## Open questions for the probe

These need a run of Manyan on corpus entries before implementation, about 20 entries
per technique:
1. Rendered modules `EXTEND Apalache` and `Variants`. Manyan's README does not list
   either among its built-in modules. If they don't resolve, the backend writes
   `StandardModuleResources.BUNDLED` into `{dir}`.
2. In a `.cfg`, does `-symbolic` accept an action invariant (`Step`) as an `INVARIANT`?
   Apalache accepts one in `--inv`.
3. Does Manyan agree with TLC's exit status on the corpus87 TLC verdicts?
4. Instance-linked libraries ([ADR 0014][adr-0014]) `INSTANCE` the Community Modules,
   which Manyan reimplements in C. Probe whether they resolve. If they don't, refuse
   `generic` together with instance-linked libraries.

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

1. `make manyan-build` and the probe, answering the open questions.
2. `init` copies a native executable into `tools/`.
3. The `[generic]` table: `GenericCheckerConfig`, `GenericCompatibility`,
   `GenericPlaceholder`, and the commented example `init` writes.
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
