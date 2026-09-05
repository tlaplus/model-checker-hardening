# Intersection of two infinite sets

Observed four times in corpus3 (<0.01% of its 435,265 aggregator deviations);
TLC failed and Apalache passed.

TLC can compute an intersection by enumerating either operand. When both are
infinite symbolic sets, neither strategy is available. Apalache retained the
sets symbolically in the observed generated expressions.

## Representative MWE

```tla
---- MODULE InfiniteIntersection ----
EXTENDS Integers, Naturals
VARIABLE values
Init == values = Nat \cap Int
Next == UNCHANGED values
Inv == TRUE
====
```

TLC reports that neither operand is enumerable. This is a representation limit;
the mathematical intersection is well defined.
