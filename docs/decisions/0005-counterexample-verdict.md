# 0005: Separate counterexamples from checker failures

**Author:** OpenAI Codex GPT 5.6-sol max

**Status:** Accepted

**Date:** 2026-09-04

## Context

TLC and Apalache previously recorded every expected non-success result as
`fail`. Failure code 12 identified a property violation, while codes 75, 120,
and 150 identified evaluation, typechecking, and parsing failures. The
conformance aggregator compared only verdicts and deliberately ignored codes.
Consequently, a TLC counterexample and an Apalache evaluation failure formed a
fail/fail pair and incorrectly counted as checker agreement.

Whether a checker found a counterexample is a semantic result needed by the
aggregator, not diagnostic failure metadata.

## Decision

Model-checker stages record four verdicts: `pass`, `counterexample`, `fail`, and
`crashed`. `counterexample` means that the checker reported a property
violation. TLC exit statuses 10 through 14 and Apalache exit status 12 produce
this verdict.

The checker stages own `02tlc-counterexample` and
`02apa-counterexample` result directories. A counterexample carries no failure
code. Codes 75 (`spec_eval`), 120 (`typecheck`), and 150 (`parse`) remain the
shared taxonomy for the `fail` verdict.

The aggregator continues to compare complete non-crash verdicts. Equal
pass/pass, counterexample/counterexample, and fail/fail pairs pass aggregation.
Any unequal pair fails aggregation. In particular, counterexample/pass and
counterexample/fail fail because exactly one checker found a counterexample.
Failure codes and details remain non-semantic and do not participate in the
comparison. A pair containing `crashed` is not aggregated.

Verdict-indexed directory and counter tables are keyed by `CorpusVerdict`; the
new alternative is not represented by another parallel field.

## Consequences

A checker failure no longer implies that the checker found a counterexample.
Reports and progress output count counterexamples separately from failures.

The corpus layout and envelope format changed. Existing corpora lack the two
required counterexample directories, and entries using verdict `fail` with code
12 are invalid under this build. Per repository policy, there is no migration or
compatibility path.
