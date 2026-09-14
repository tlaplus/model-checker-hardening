---
state: open
labels: [apalache]
---

# `ENABLED` in the guard of a `CASE` without `OTHER` makes a false invariant pass

## Summary

When an operator's body is a `CASE` without `OTHER` whose guard uses `ENABLED`,
Apalache neither reports `ENABLED` as unsupported nor checks the invariant that
applies the operator. An invariant `FALSE /\ L = 0` passes with `NoError` and
exit status 0 in every state, although VCGen splits it into two verification
conditions, one of them `FALSE`. The same operator with an `OTHER` arm, or with a
guard that does not use `ENABLED`, behaves as expected: Apalache rejects
`ENABLED` with exit 75, or reports the counterexample.

Observed with Apalache 0.62.2 (build `f0dec98`).

## Reproduction

`FuzzInput.tla`:

```tla
---- MODULE FuzzInput ----
EXTENDS Integers
VARIABLE
  \* @type: Int;
  x
Init == x = 0
Next == UNCHANGED x
L == CASE ENABLED FALSE -> 0
Inv == FALSE /\ L = 0
====
```

```sh
apalache-mc check --init=Init --next=Next --inv=Inv --length=0 FuzzInput.tla
```

```text
  > VCGen produced 2 verification condition(s)
The outcome is: NoError
Checker reports no error up to computation length 0
EXITCODE: OK
```

TLC, with `INIT Init`, `NEXT Next` and `INVARIANT Inv`, reports `Invariant Inv is
violated by the initial state` and exits 12. Three consecutive Apalache runs
report `NoError`.

Changing `L` and `Inv` in the module above:

| `L` | `Inv` | Apalache |
| --- | --- | --- |
| `CASE ENABLED FALSE -> 0` | `FALSE /\ L = 0` | exit 0, `NoError` |
| `CASE ENABLED FALSE -> 0` | `L = 0 /\ FALSE` | exit 0, `NoError` |
| `CASE ENABLED FALSE -> 0` | `FALSE` | exit 12, counterexample |
| `CASE ENABLED FALSE -> 0 [] OTHER -> 0` | `L = 0 /\ FALSE` | exit 75, `unsupported expression: ENABLED FALSE` |
| `CASE FALSE -> 0` | `L = 0 /\ FALSE` | exit 12, counterexample |
| `ENABLED FALSE` | `L /\ FALSE` | exit 75, `unsupported expression: ENABLED FALSE` |

The behavior is the same when `L` is a parameterized or `LET`-local operator, and
when the guard is an action such as `ENABLED (x' = 1)`. Written inline, as in
`Inv == FALSE /\ (CASE ENABLED FALSE -> TRUE)`, the expression is rejected with
exit 75.

## How it was found

The fuzzing workflow's conformance aggregator recorded a TLC counterexample and
an Apalache pass for a generated module. Its invariant,
`"default_OF_MODEL" \in (IF p THEN {} ELSE {})`, is false for every `p`. Here `p`
folded, over an empty sequence, a lambda whose body was
`VariantGetUnsafe("Tag10", CASE ENABLED ... -> ...)`. Reducing the module's typed
IR JSON while keeping that invariant shape led to the reproduction above.

## Expected behavior

Apalache either rejects `ENABLED` as unsupported, with exit 75, or checks the
invariant and reports the violation of `FALSE` in the initial state.

## Impact

Apalache reports a violated invariant as holding, without a diagnostic. A user
relying on the verdict concludes the property holds. An automated comparison
with TLC records the difference but cannot attribute it without reduction.
