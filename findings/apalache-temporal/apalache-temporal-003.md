---
state: open
labels: [apalache]
---

# A quantifier around a temporal formula leaves its bound variable unassigned

## Summary

When a temporal property is a bounded quantifier whose bound variable occurs under
a temporal operator, such as `\E q \in 0..1 : <>(x = q)` or
`\A q \in 0..1 : [](x >= q - 1)`, `apalache-mc check --temporal` fails in the
bounded checker with "SubstRule: Variable q$1 is not assigned a value", preceded
by a hint about uninitialized `CONSTANTS`. It exits with status 255. The
same quantifiers are checked when their body is a state predicate, or when the
temporal formula under them does not use the bound variable.

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
Next == (x < 2 /\ x' = x + 1) \/ UNCHANGED x
Inv == TRUE
Prop == \E q \in 0..1: <>(x = q)
====
```

```sh
apalache-mc check --init=Init --next=Next --inv=Inv --temporal=Prop --length=3 FuzzInput.tla
```

```text
State 0: Checking 2 state invariants
State 0: state invariant 0 holds.
State 0: state invariant 1 holds.
Step 0: picking a transition out of 1 transition(s)
This error may show up when CONSTANTS are not initialized.
Check the manual: https://apalache-mc.org/docs/apalache/parameters.html
Input error (see the manual): SubstRule: Variable q$1 is not assigned a value
EXITCODE: ERROR (255)
```

TLC checks the same module with `SPECIFICATION Spec` and `PROPERTY Prop`, where
`Spec == Init /\ [][Next]_x`, and reports no error: `x = 0` holds initially.

Varying the property, with the module otherwise unchanged:

| `Prop` | Apalache | TLC |
| --- | --- | --- |
| `\E q \in 0..1: <>(x = q)` | exit 255, `SubstRule` | no error |
| `\E q \in {0}: <>(x = q)` | exit 255, `SubstRule` | no error |
| `\E q \in {}: [](x = q)` | exit 255, `SubstRule` | property violated |
| `\A q \in 0..1: [](x >= q - 1)` | exit 255, `SubstRule` | no error |
| `\E q \in 0..1: <>(x = 1)` | counterexample | property violated |
| `[](\E q \in 0..1: x >= q)` | `NoError` | no error |
| `\A q \in {}: (x = q)` | `NoError` | rejected as a tautology |

Only the rows where the bound variable occurs under `<>` or `[]` fail.
[ADR 0007](../../docs/decisions/0007-levels-and-temporal-properties.md) recorded
the same diagnostic for `\E k \in 0..1 : <>(x = k)` when probing temporal
properties.

## Corpus evidence

In the `module` corpus23, generated with the `action` and `temporal` categories,
two Apalache crashes report this diagnostic, `99aa1906` and `f2916e1d`, and
corpus22 has one, `8381df40`. In each, `Prop` starts with a bounded quantifier over
the empty set whose body contains temporal operators: `\E q27 \in {}:` with `<>`
and `~>` below it, `\A q346 \in {}: [](...)`, and `\A q31 \in {}:` with `[]` and
`<>`. The corpus inputs were not reduced further.

## Expected behavior

Apalache checks the property, as it does for the same quantifier over a state
predicate, or reports the quantified temporal formula as unsupported with a
diagnostic that names it.

## Impact

Temporal properties that quantify over a finite set, a common way to state a
property for each process or value, cannot be checked. The diagnostic points to
uninitialized constants, which the specification does not have. The fuzzing
workflow records these inputs as Apalache crashes.
