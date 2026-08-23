# TLC reaches `0^0`; Apalache does not

Observed share: 0.86% of aggregator deviations; TLC failed and Apalache passed.

Both tools classify evaluated `0^0` as undefined. These cases differ because
TLC's evaluation of the printed TLA+ reaches the expression while Apalache's
typed-IR rewriting eliminates the surrounding unreachable computation.

## Representative MWE

```tla
---- MODULE ZeroPowerZeroTlcFails ----
EXTENDS Integers
VARIABLE
\* @type: Int;
result
Init == result = 0 ^ 0
Next == UNCHANGED result
Inv == TRUE
====
```

This module isolates the undefined operation that TLC reached. The corpus
versions place it in larger callbacks or generated subexpressions that
Apalache's typed-IR input path eliminates. Feeding this isolated module to
Apalache also produces its ordinary undefined-arithmetic error; the corpus
difference is evaluation order, not disagreement over the value of `0^0`.
