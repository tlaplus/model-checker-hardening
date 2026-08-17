# `PrettyWriter` renders unbounded binders as internal operator calls

## Summary

Apalache's `PrettyWriter` renders the IR operators for unbounded `CHOOSE`,
universal quantification, and existential quantification as ordinary operator
applications. The resulting names are internal IR identifiers, not TLA+
operators, so SANY rejects the output.

Observed with `org.apalache-mc:tla-io_2.13:0.61.1-SNAPSHOT`.

## Minimal examples

The writer emits:

```tla
CHOOSE2(x, TRUE)
FORALL2(x, FALSE)
EXISTS2(x, FALSE)
```

SANY reports `Unknown operator` for `CHOOSE2`, `FORALL2`, and `EXISTS2`.
The corresponding TLA+ syntax is:

```tla
CHOOSE x : TRUE
\A x : FALSE
\E x : FALSE
```

The corrected forms pass SANY parsing and semantic analysis.

## Root cause

`PrettyWriter.exToDoc` has a dedicated case for the three-argument bounded
operators `chooseBounded`, `forall`, and `exists`, but no case for their
two-argument unbounded counterparts. The unbounded expressions therefore reach
the generic operator fallback, which prints the IR names and comma-separated
arguments as a function-style application.

The internal names describe IR arity; they are not declarations supplied by a
standard module and have no meaning in the rendered TLA+ module.

## Scope and impact

A deterministic replay of 5,000 evenly spaced entries from a corpus containing
85,486 parser failures found 2,793 entries (55.86%) whose diagnostics named at
least one of `CHOOSE2`, `FORALL2`, or `EXISTS2`. This was the largest observed
failure group.

## Expected behavior

`PrettyWriter` should render each unbounded binder with its canonical TLA+
syntax and preserve the represented binding structure. Add SANY round-trip
tests for unbounded `CHOOSE`, `\A`, and `\E`, including nested expressions that
require grouping against a parent expression.
