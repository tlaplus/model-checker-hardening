# Empty-domain function-set equality

Observed share: 0.06% of aggregator deviations; TLC passed and Apalache returned
a counterexample.

The literal function set `[{} -> {}]` contains the empty function. Apalache
incorrectly reports that a variable initialized to this value is unequal to the
same expression.

## Representative MWE

```tla
---- MODULE EmptyFunctionSet ----
VARIABLE
\* @type: Set((Str -> Bool));
functions
Init == functions = [{} -> {}]
Next == UNCHANGED functions
Inv == functions = [{} -> {}]
====
```

TLC proves the invariant. Apalache 0.62.0 reports a violation at state zero.
This soundness defect is filed as
[`apalache-bmc/apalache-bmc-002`](../findings/apalache-bmc/apalache-bmc-002.md).
Apalache 0.62.3 fixes it and proves the invariant; checked with Apalache commit
`3eb15b2`, TLC `341472c` and FuzzTLA `b7caaf2`.
