# Cardinality of an infinite set

Observed 51 times in corpus3 (0.01% of its 435,265 aggregator deviations); TLC
failed and Apalache passed.

`Cardinality(S)` is defined only for finite sets. TLC rejects `Cardinality(Nat)`
when it reaches the operation. The observed Apalache passes come from larger
expressions whose typed-IR rewriting does not require the same evaluation; they
do not establish a value for the undefined expression.

## Representative MWE

```tla
---- MODULE CardinalityOfNat ----
EXTENDS Naturals, FiniteSets
VARIABLE value
Init == value = Cardinality(Nat)
Next == UNCHANGED value
Inv == TRUE
====
```

TLC exits with status 75 and reports `Attempted to compute cardinality of the
value Nat`. Portable specifications call `Cardinality` only after establishing
that its argument is finite.
