# `CHOOSE` over `Int` or `Nat`

Observed share: 0.15% of aggregator deviations; TLC failed and Apalache passed.

TLC reached a `CHOOSE` over `Int` or `Nat` and could not enumerate the domain.
In the observed instances, Apalache's typed-IR rewriting eliminated the
surrounding computation before that operation was evaluated.

## Representative MWE

```tla
---- MODULE ChooseOverInfiniteSet ----
EXTENDS Integers
VARIABLE
\* @type: Int;
result
Init == result = CHOOSE i \in Int : TRUE
Next == UNCHANGED result
Inv == TRUE
====
```

The module isolates TLC's non-enumerable-domain diagnostic. A standalone
Apalache source check also exposes its unsupported symbolic-set filter path;
the corpus pass depends on the surrounding generated context. This group is
distinct from `CHOOSE` without a witness.
