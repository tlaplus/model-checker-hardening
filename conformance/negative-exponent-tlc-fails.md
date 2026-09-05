# TLC reaches a negative exponent

Observed twice in corpus3 (<0.01% of its 435,265 aggregator deviations); TLC
failed and Apalache passed.

The natural-number exponentiation operator does not define a result for a
negative exponent. TLC reached such an operation in the generated source,
whereas Apalache's rewriting of the complete typed expression avoided it.

## Representative MWE

```tla
---- MODULE NegativeExponent ----
EXTENDS Integers
VARIABLE value
Init == value = 2 ^ (-1)
Next == UNCHANGED value
Inv == TRUE
====
```

TLC exits with status 75 and reports that the second argument of `^` should be a
natural number. This is an evaluation-order deviation over an undefined
expression, not a disagreement over a defined value.
