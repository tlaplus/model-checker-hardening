---
state: open
labels: [apalache]
---

# A leads-to nested in a leads-to fails `TemporalPass` with an empty diagnostic

## Summary

When one operand of `~>` contains another `~>`, as in `(P ~> Q) ~> R` or
`P ~> (Q ~> R)`, Apalache's `TemporalPass` reports `<unknown>: unexpected
expression:` with nothing after the colon, followed by "Unexpected expressions in
the specification", and exits with status 255. A single `~>`, also under `[]`,
`<>`, `~` or `\/`, and `<>` or `[]` inside `~>`, are checked. TLC checks the
nested forms and reports their counterexamples.

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
Prop == (x = 0 ~> x = 1) ~> FALSE
====
```

```sh
apalache-mc check --init=Init --next=Next --inv=Inv --temporal=Prop --length=3 FuzzInput.tla
```

```text
PASS #5: TemporalPass
  > Rewriting temporal operators...
  > Found 1 temporal properties
<unknown>: unexpected expression:
Unexpected expressions in the specification (see the error messages)
EXITCODE: ERROR (255)
```

Replacing `Prop`, with the same command:

| `Prop` | Apalache |
| --- | --- |
| `(x = 0 ~> x = 1) ~> FALSE` | exit 255, empty unexpected expression |
| `x = 0 ~> (x = 1 ~> x = 2)` | exit 255, empty unexpected expression |
| `(x = 0 ~> x = 1) \/ (x = 1 ~> x = 2)` | `NoError` |
| `[](x = 0 ~> x = 1)` | counterexample |
| `<>(x = 0 ~> x = 1)` | counterexample |
| `~(x = 0 ~> x = 1)` | counterexample |
| `x = 0 ~> [](x = 2)` | counterexample |
| `<>(x = 0) ~> FALSE` | counterexample |

With `SPECIFICATION Spec` and `PROPERTY Prop`, where
`Spec == Init /\ [][Next]_x`, TLC reports a counterexample for both nested forms.

## Corpus evidence

In the `module` corpus22, generated with the `action` and `temporal` categories,
42 Apalache crashes end in `TemporalPass` with the empty unexpected expression.
The property of every one contains at least two `~>`; `9e4e2256`, for example,
has `(~var0 ~> var1) ~> FALSE`, and `4ad6243b` has
`FALSE ~> ([field6 |-> FALSE, field7 |-> FALSE]["field6"] ~> FALSE)`.

## Expected behavior

Apalache checks `(P ~> Q) ~> R` like any other temporal formula, or rejects it as
unsupported with a diagnostic that names the expression.

## Impact

Specifications with a nested leads-to cannot be checked, and the diagnostic does
not say which expression was rejected. The fuzzing workflow records these inputs
as Apalache crashes.
