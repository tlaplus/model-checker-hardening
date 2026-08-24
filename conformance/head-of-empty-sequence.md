# `Head` of an empty sequence

Observed share: 17.40% of aggregator deviations; TLC failed and Apalache passed.

`Head(<<>>)` is undefined. TLC reports an evaluation error, whereas Apalache's
symbolic sequence encoding may leave the result unconstrained.

## Representative MWE

```tla
---- MODULE HeadOfEmptySequence ----
EXTENDS Sequences
VARIABLE
\* @type: Int;
result
Init == result = Head(<<>>)
Next == UNCHANGED result
Inv == TRUE
====
```

Portable specifications must establish `Len(s) > 0` before evaluating
`Head(s)`.
