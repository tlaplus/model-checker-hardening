# Quantification over an infinite set

Observed share: 0.09% of the original session's aggregator deviations; TLC
failed and Apalache passed. Corpus3 adds 375 instances (0.09% of its 435,265
deviations) under two versions of TLC's non-enumerable-bound diagnostic.

TLC reached a bounded quantifier whose domain was not enumerable. Apalache's
typed-IR rewriting eliminated the surrounding computation in the observed
instances.

## Representative MWE

```tla
---- MODULE QuantificationOverInfiniteSet ----
EXTENDS Integers
VARIABLE
\* @type: Bool;
ok
Init == ok = (\E i \in Int : i = 0)
Next == UNCHANGED ok
Inv == TRUE
====
```

The module isolates TLC's diagnostic. Checked directly as source, Apalache
0.62.0 also rejects expansion of the infinite domain; the corpus pass reflects
the larger expression's evaluation path rather than general quantifier support.
