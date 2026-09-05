# Finite set containing an infinite set

Observed once in corpus4 (0.02% of its 4,282 aggregator deviations) and 37
times in corpus3 (<0.01% of its 435,265 deviations); TLC failed and Apalache
passed. Corpus3 also compares `Int`, `STRING`, and `Seq(S)` representations with
materialized finite sets.

While materializing the finite set `{Nat, {}}`, TLC compares its elements and
rejects the comparison between the finite empty set and `Nat`. Both values have
the same TLA+ set type, so this is a finite-value representation limit rather
than a type error.

The corpus input is
[`26e22b5e...0479.cbor`](../corpus4/03aggregator-fail/26e22b5ef7a4cd4d13dd9d2dd1b29e7dd8b0438ceb4d0f7fdf0bf386fb8b0479.cbor).

## Representative MWE

```tla
---- MODULE CompareNatWithEmpty ----
EXTENDS Naturals
VARIABLE value
Init == value = (CHOOSE s \in {Nat, {}} : FALSE)
Next == UNCHANGED value
Inv == TRUE
====
```

TLC exits with status 75 and reports: `Attempted to compare the set {} with the
value: Nat`.

The MWE isolates TLC's limitation. The observed Apalache result applies to the
complete generated expression and does not claim that every operation on a
finite set containing `Nat` is supported.
