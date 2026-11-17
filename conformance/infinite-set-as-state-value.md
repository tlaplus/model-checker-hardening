# Infinite set used as a state value

Observed share: 0.06% of aggregator deviations; TLC failed and Apalache passed.

TLC cannot fingerprint a state containing `Int` or `Nat` as a value. Apalache
represents these sets symbolically. The same holds for `Seq(S)`: corpus85
`84c93be7` and `a43550f4` assign `Seq({FALSE, TRUE})` and `Seq({1})` to a
variable in the initial state.

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
