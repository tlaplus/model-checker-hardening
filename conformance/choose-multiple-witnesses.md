# `CHOOSE` with more than one witness

Observed once among corpus9's 40 unclassified aggregator deviations; TLC passed
and Apalache returned a counterexample.

TLA+ fixes `CHOOSE x \in S : P` to one unspecified but deterministic value.
Apalache implements it as a non-deterministic symbolic choice, so whenever the
predicate has more than one witness Apalache may pick a different element than
TLC's evaluator and report a counterexample for an invariant TLC proves. This is
the same intentional deviation recorded for
[`CHOOSE` without a witness](choose-without-witness.md), observed in the
opposite verdict direction: there the predicate has no witness and TLC fails,
here it has several and Apalache adds behavior TLA+ does not permit.

The corpus input is
[`df8826b1...881db.cbor`](../corpus9/03aggregator-fail/df8826b15d8cbedcc09000f130e037830dd441976641101f8f999c76d206881b.cbor),
whose invariant is
`(IF FALSE THEN FALSE ELSE CHOOSE b \in BOOLEAN : {} \subseteq {}) <=> FALSE`.

## Representative MWE

```tla
---- MODULE ChooseWitness ----
VARIABLE
\* @type: Bool;
flag
Init == flag = (CHOOSE b \in BOOLEAN : TRUE)
Next == UNCHANGED flag
Inv == flag = FALSE
====
```

TLC completes with no error: its `CHOOSE` yields `FALSE` for this set and
predicate, and does so consistently. Apalache reports a counterexample in which
`flag` is `TRUE`. Both readings satisfy the predicate; only one specification
value is admitted by TLA+.

Specifications that must be checked by both tools should avoid relying on the
identity of a `CHOOSE` result, or should constrain the predicate so that exactly
one element satisfies it.
