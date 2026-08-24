# `CHOOSE` without a witness

Observed share: 24.48% of aggregator deviations; TLC failed and Apalache passed.

TLC cannot evaluate a bounded `CHOOSE` whose predicate has no satisfying
element. Apalache implements `CHOOSE` as a non-deterministic symbolic choice;
this intentionally differs from TLA+'s deterministic choice semantics and from
TLC's evaluator.

## Representative MWE

```tla
---- MODULE ChooseWithoutWitness ----
EXTENDS Integers
VARIABLE
\* @type: Int;
result
Init == result = CHOOSE i \in {1} : i = 2
Next == UNCHANGED result
Inv == TRUE
====
```

TLC reports that no element satisfies the predicate. Apalache permits a
symbolic value. This row records the corpus-confirmed manifestation of the
known `CHOOSE` semantic deviation; it is not a new Apalache defect.
