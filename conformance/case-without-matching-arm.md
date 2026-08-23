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
