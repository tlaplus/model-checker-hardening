# 0003: Shared model-checker failure codes

**Authors:** Igor Konnov and OpenAI Codex

**Status:** Accepted

**Date:** 2026-08-17

## Context

TLC and Apalache must report failures through one corpus representation. TLC
often collapses distinct undefined expressions into `FAILURE_SPEC_EVAL`; the
specific cause may appear only in human-readable output. Examples include
function application outside the domain, an unsuccessful `CHOOSE`, `Head` of an
empty sequence, invalid `SubSeq` indices, an unmatched `CASE`, and division by
zero. Deriving stable fine-grained codes from these messages would couple the
corpus format to diagnostic wording.

[Apalache's exit-code registry][] already aligns a small subset with TLC and
supplements it with richer explanations. The corpus needs the same stable coarse
taxonomy while retaining enough bounded context for manual triage.

## Decision

Model-checker failures use these numeric codes:

| Code | Symbol | TLC source | Apalache source |
| ---: | --- | --- | --- |
| 12 | `counterexample` | Exit statuses 10–14 | 12 |
| 75 | `spec_eval` | Exit statuses 75–77 and `TLC_INTEGER_TOO_BIG` | 75 |
| 120 | `typecheck` | None | 120 |
| 150 | `parse` | Exit statuses 150–151 | 150 |

The registry belongs to neither checker. A checker worker returns a normalized
code only for a failure. Successes and crashes have no failure code; unexpected
or system-level TLC statuses remain crashes.

The corpus stores the numeric value as `stages.<checker>.code`. A failed TLC
entry requires a known code. Other TLC verdicts forbid it. There is no migration
or compatibility path for failed entries written before this decision.

A failure may also store `stages.<checker>.detail`. TLC derives it from the first
meaningful diagnostic line beginning with `Error:`, removes that prefix,
normalizes whitespace, and limits the result to 80 Unicode code points. Longer
text uses the first 79 code points followed by `…`. The detail is optional,
human-readable, and non-semantic.

The conformance aggregator compares checker verdicts. Any TLC failure and
Apalache failure still agree regardless of their codes. Codes support reporting
and triage; automated comparison and grouping must not use the detail.

## Consequences

The same coarse failure class has the same stored value for both checkers.
Undefined-expression variants remain distinguishable to a reader when TLC emits
a useful diagnostic, without creating unstable message-derived codes. Existing
TLC failure corpora must be regenerated because their envelopes lack the required
code.

[Apalache's exit-code registry]: https://github.com/apalache-mc/apalache/blob/main/mod-infra/src/main/scala/at/forsyte/apalache/infra/ExitCodes.scala
