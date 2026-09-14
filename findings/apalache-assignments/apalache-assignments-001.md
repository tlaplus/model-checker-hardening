---
state: open
labels: [apalache]
---

# `UNCHANGED` of an assigned variable is rejected as an assignment

## Summary

Apalache treats `UNCHANGED x` as an assignment to `x'`, and rejects an action
that has already assigned `x'` and then mentions `UNCHANGED x`. At the top of the
conjunction it reports "Manual assignment is spurious, x is already assigned!";
under `~`, or in the condition of `IF`, it reports "Illegal assignment inside an
assignment-free expression". Both exit with status 255. The same action written
with `x' = x` in place of `UNCHANGED x` is accepted, although the Apalache
manual (`features.md`, "The Action Operators") states that `UNCHANGED` is
"Always replaced with `e_1' = e_1 /\ ... /\ e_k' = e_k`". TLC checks every
variant below without error.

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
Next == x < 3 /\ x' = x + 1 /\ (~UNCHANGED x)
Inv == x <= 3
====
```

```sh
apalache-mc check --init=Init --next=Next --inv=Inv --length=3 --no-deadlock FuzzInput.tla
```

```text
Assignment error: FuzzInput.tla:7:34-7:44: Illegal assignment inside an assignment-free expression. See https://apalache-mc.org/docs/apalache/principles/assignments.html
EXITCODE: ERROR (255)
```

Replacing the parenthesized conjunct of `Next`, with the same command and
`INIT Init`, `NEXT Next`, `INVARIANT Inv` for TLC:

| Conjunct | Apalache | TLC |
| --- | --- | --- |
| `~UNCHANGED x` | exit 255, illegal assignment | no error |
| `~(x' = x)` | `NoError` | no error |
| `UNCHANGED x \/ x > 5` | exit 255, spurious manual assignment | no error |
| `IF UNCHANGED x THEN TRUE ELSE TRUE` | exit 255, illegal assignment | no error |
| `CASE UNCHANGED x -> TRUE [] OTHER -> TRUE` | exit 255, spurious manual assignment | no error |
| `CASE x' = x -> TRUE [] OTHER -> TRUE` | `NoError` | no error |
| `UNCHANGED x` | exit 255, spurious manual assignment | no error |

In the last row the action `x' = x + 1 /\ x' = x` is unsatisfiable, and TLC treats
it as disabled.

## Expected behavior

`UNCHANGED x` behaves as `x' = x`: after `x'` has been assigned, it is a
Boolean test on the assigned value, as the manual describes.

## Impact

Specifications that use `UNCHANGED` in a guard, a negation or a disjunction after
the variable is assigned cannot be checked. The fuzzing workflow generates such
guards once the `action` generator category is enabled, and records them as
Apalache crashes.
