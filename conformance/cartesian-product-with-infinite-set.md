# Cartesian product with an infinite operand

Observed twice in corpus3 (<0.01% of its 435,265 aggregator deviations); TLC
failed and Apalache passed.

TLC materializes Cartesian products and therefore rejects a product containing
an infinite operand. Apalache kept the corresponding set symbolic in the
observed generated expressions.

## Representative MWE

```tla
---- MODULE InfiniteProduct ----
EXTENDS Integers
VARIABLE pairs
Init == pairs = Int \X {0}
Next == UNCHANGED pairs
Inv == TRUE
====
```

TLC reports `Attempted to enumerate a set of the form s1 \X s2 ... \X sn`.
The product is mathematically defined; the deviation records TLC's finite-value
representation limit.
