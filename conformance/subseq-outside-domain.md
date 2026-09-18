# `SubSeq` outside the sequence domain

Observed share: 2.15% of aggregator deviations; TLC failed and Apalache passed.

The standard `SubSeq` definition indexes the source sequence. Bounds that make
it access an index outside `DOMAIN s` cause a TLC evaluation failure; Apalache
can leave the resulting elements unconstrained.

## Representative MWE

```tla
---- MODULE SubSeqOutsideDomain ----
EXTENDS Sequences
VARIABLE
\* @type: Seq(Int);
result
Init == result = SubSeq(<<1>>, 0, 1)
Next == UNCHANGED result
Inv == TRUE
====
```

Portable uses constrain the requested interval to the source sequence's valid
indices.

An upper bound past the end fails the same way with `The third argument of SubSeq
must be in the domain of its first argument:`, for example
`SubSeq(<<1>>, 1, 2)`; TLC exits 75. corpus29 has two such deviations:
Apalache passed `343b4004` and reported a counterexample for `dcffb35f`. The
signature covers both messages.
