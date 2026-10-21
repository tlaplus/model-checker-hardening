# The corpus

A corpus is the directory of one campaign. It holds the configuration, every input
generated so far, and the verdict of every tool on it. Each stage of the pipeline
reads entries from one directory and moves them to the directory of its verdict, so
the place of a file tells you how far it got and what happened to it.

## Directory layout

`fuzztla init` creates this layout:

| Directory | Contents |
|---|---|
| `config.toml` | The campaign's [configuration](configuration.md). |
| `00-inputs/` | Generated inputs waiting for the parser. |
| `00-known-defects/` | Candidates that match a [known-defect signature](known-defect-signatures.md), kept as samples instead of being checked. Created on the first match. |
| `01parser-pass/`, `-fail/`, `-crash/` | Parser verdicts. SANY must accept every generated module, so a `fail` or `crash` here is a generator bug or a SANY defect. |
| `02tlc-inputs/`, `02apa-inputs/`, `02tlcn-inputs/` | Parsed inputs waiting for TLC, Apalache and TLC-n, one copy per configured checker. |
| `02tlc-<verdict>/`, `02apa-<verdict>/`, `02tlcn-<verdict>/` | Checker verdicts: `pass`, `counterexample`, `fail` or `crash`. |
| `03aggregator-pass/` | Entries on which all checkers agree. |
| `03aggregator-fail/` | **Disagreements.** These are the main output of a conformance campaign. |
| `04quality-pass/`, `04quality-fail/` | Agreeing entries that the quality gate kept or dropped as parents for [mutation](mutation.md). |
| `.work/` | Temporary files and generator crash artifacts. Not part of the corpus. |

Other tools add files at the corpus root: triage CSV files, `corpus.sqlite` from
[`export-db`](corpus-database.md), and `report.md` from the
[campaign report](campaign-report.md).

`02tlcn-*` is used only when `tlcn` is among the checkers ([Running campaigns](running.md#comparing-tlc-with-itself)).

## Entries

An entry is a CBOR file named by the SHA-256 hash of its content, for example
`00-inputs/13d4df05….cbor`. Equal inputs have equal names, so the corpus never holds
duplicates. An entry stores:

- the input bytes from which FuzzTLA regenerates the TLA<sup>+</sup> module
  deterministically;
- how it was made: its generation, richness cohort and score, and for a mutant
  its parent and mutation operators;
- one record per stage, with the verdict, a failure code and message, timestamps,
  and [exploration metrics](exploration-metrics.md).

The module itself is not stored. Use [`fuzztla print`](inspecting.md) to see it. An
entry can be replayed only with the generator settings of its corpus.

A crash also writes a `.stacktrace` file next to the entry, with the exception or
the [timeout diagnostic](timeout-diagnostics.md).

## Verdicts

| Verdict | Meaning |
|---|---|
| `pass` | The checker completed its search and found no violation. |
| `counterexample` | The checker found a state or behaviour that violates the invariant or property. |
| `fail` | The checker reported an error, such as an evaluation error, a type error or an unsupported operator. The entry records a failure code that classifies it. |
| `crash` | The checker process threw an unexpected exception, ran out of memory, or exceeded its timeout. |

The aggregator waits until every configured checker has a verdict other than
`crash`. It moves an entry to `03aggregator-pass` when all verdicts are equal: all
`pass`, all `counterexample` or all `fail`. Failure codes are not compared. Any other
combination goes to `03aggregator-fail`, including a counterexample from only one
checker. Two TLC runs that both complete must also report the same number of
distinct states.

An entry that crashes a checker stays in that checker's `crash` directory and is not
compared.

## What the checkers are asked

The parser and TLC receive a TLA<sup>+</sup> module, and Apalache receives typed
Apalache IR JSON for the same module. Both check the generated `Init`, `Next` and
invariant `Inv` with deadlock checking disabled, and the generated temporal property
when there is one ([Actions and temporal properties](temporal-properties.md)).
A step counter bounds every behaviour, so both tools explore the same finite
state space.
