# Function set with an infinite component

Observed once in corpus3 (<0.01% of its 435,265 aggregator deviations); TLC
failed and Apalache passed.

TLC materializes a function set `[D -> R]`. It cannot do so when the range (or
the domain) is an infinite symbolic set. This differs from constructing one
specific function over an infinite domain, which is documented in
[function-over-infinite-domain.md](function-over-infinite-domain.md).

## Representative MWE

```tla
---- MODULE InfiniteFunctionSet ----
EXTENDS Naturals
VARIABLE functions
Init == functions = [{0} -> Nat]
Next == UNCHANGED functions
Inv == TRUE
====
```

The corpus instance used `STRING` as the range and TLC reported that the range
could not be enumerated. The failure is a finite-materialization limit, not a
semantic defect.
