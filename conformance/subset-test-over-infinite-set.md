# Subset test with an infinite left operand

Observed 103 times in corpus3 (0.02% of its 435,265 aggregator deviations); TLC
failed and Apalache passed.

TLC evaluates `S \subseteq T` by enumerating `S`. It cannot use that procedure
when the left operand is an infinite symbolic set. Apalache can reason about
some such sets without materializing them.

## Representative MWE

```tla
---- MODULE InfiniteSubset ----
EXTENDS Integers, Naturals
VARIABLE ok
Init == ok = (Nat \subseteq Int)
Next == UNCHANGED ok
Inv == TRUE
====
```

TLC exits with status 75 and reports that `S` was not enumerable. This is a TLC
capability limit rather than disagreement about a finite computed result.
