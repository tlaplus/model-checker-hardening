# Infinite set used as a state value

Observed share: 0.06% of aggregator deviations; TLC failed and Apalache passed.

TLC cannot fingerprint a state containing `Int` or `Nat` as a value. Apalache
represents these sets symbolically.

## Representative MWE

```tla
---- MODULE InfiniteSetAsStateValue ----
EXTENDS Integers
VARIABLE
\* @type: Set(Int);
values
Init == values = Int
Next == UNCHANGED values
Inv == TRUE
====
```

This is a TLC state-representation limit, not an undefined TLA+ expression.
