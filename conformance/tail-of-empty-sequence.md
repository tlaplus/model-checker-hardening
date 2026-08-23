# `Tail` of an empty sequence

Observed share: 2.06% of aggregator deviations; TLC failed and Apalache passed.

`Tail(<<>>)` is undefined. TLC rejects it during state evaluation, while
Apalache's symbolic encoding may produce an unconstrained empty-sequence tail.

## Representative MWE

```tla
---- MODULE TailOfEmptySequence ----
EXTENDS Sequences
VARIABLE
\* @type: Seq(Int);
result
Init == result = Tail(<<>>)
Next == UNCHANGED result
Inv == TRUE
====
```

Portable specifications guard `Tail(s)` with `Len(s) > 0`.
