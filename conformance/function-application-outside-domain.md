# Function application outside its domain

Observed share: 28.17% of aggregator deviations; TLC failed and Apalache passed.

TLC treats application outside a function's domain as a specification-evaluation
error. Apalache's symbolic encoding can assign an unconstrained value to the
application instead. The expression is semantically undefined, so this is a
checker-policy difference rather than a soundness claim about a defined value.

## Representative MWE

```tla
---- MODULE FunctionOutsideDomain ----
EXTENDS Integers
VARIABLE
\* @type: Int;
result
Init == result = [i \in {1} |-> i][2]
Next == UNCHANGED result
Inv == TRUE
====
```

TLC reports that argument `2` is not in the function domain. Apalache accepts
the typed expression. Generators that require portable positive tests must
guard the application with `2 \in DOMAIN f`.
