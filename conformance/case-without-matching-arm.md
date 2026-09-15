# `CASE` without a matching arm

Observed share: 11.36% of aggregator deviations; TLC failed and Apalache passed.

A `CASE` with no true guard and no `OTHER` arm is undefined. TLC reports an
evaluation error. Apalache may encode the result as an unconstrained symbolic
value.

## Representative MWE

```tla
---- MODULE CaseWithoutMatchingArm ----
EXTENDS Integers
VARIABLE
\* @type: Int;
result
Init == result = CASE FALSE -> 1
Next == UNCHANGED result
Inv == TRUE
====
```

Adding an `OTHER` arm makes the expression portable.

TLC words the error by where it evaluates the `CASE`: `Attempted to evaluate a
CASE with no conditions true.`, `In computing next states, TLC encountered a
CASE with no conditions true.`, or, inside the action of `ENABLED`, `In computing
ENABLED, TLC encountered a CASE with no conditions true.` Corpus22 has 11
deviations with the `ENABLED` wording, 5 with an Apalache pass and 6 with a
counterexample. Apalache does not support `ENABLED` and should have rejected
these modules; that it completed is consistent with
[apalache-bmc-019](../findings/apalache-bmc/apalache-bmc-019.md), but this was not
confirmed for each entry.
