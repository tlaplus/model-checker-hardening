---
state: open
labels: [apalache]
---

# A conjunction with an `<>` operand under a temporal operator gets wrong verdicts

## Summary

Apalache's temporal check gives wrong verdicts when a conjunction with an `<>`
operand occurs under `[]` or `~>`. It reports a counterexample to the valid
property `[]((x /\ <>x) => x)` from `--length=4` on. It misses the violation of
`(x /\ <>~x) ~> []x` at `--length=3`, although TLC reports a 3-transition lasso.
In Apalache's counterexample, the loop-finding encoding gives the subformula
`x ∧ ◇¬x` the value `TRUE` in a state where `x = FALSE`.

Observed with Apalache 0.62.3 (build `3eb15b2`), FuzzTLA `4feb6f8e`, and TLC
commit `309e489` (tla2tools `2026.11.03`).

## Reproduction

`EventuallyInConjunction.tla`:

```tla
---- MODULE EventuallyInConjunction ----
EXTENDS Integers
VARIABLES
  \* @type: Bool;
  x,
  \* @type: Int;
  step
Init == x \in BOOLEAN /\ step = 0
Next == (step < 2 /\ x' \in BOOLEAN /\ step' = step + 1) \/ UNCHANGED <<x, step>>
\* Valid: wherever x /\ <>x holds, x holds.
Valid == []((x /\ <>x) => x)
\* Violated, as x ~> []x is, since x /\ <>~x implies x.
Missed == (x /\ <>~x) ~> []x
====
```

```sh
apalache-mc check --init=Init --next=Next --temporal=Valid --length=4 --no-deadlock EventuallyInConjunction.tla
apalache-mc check --init=Init --next=Next --temporal=Missed --length=3 --no-deadlock EventuallyInConjunction.tla
```

```text
Valid:  The outcome is: Error      EXITCODE: ERROR (12)
Missed: The outcome is: NoError    EXITCODE: OK
```

Verdicts by length:

| `--temporal=` | TLC | Apalache, lengths 1–3 | Apalache, lengths 4–6 |
| --- | --- | --- | --- |
| `Valid` | no error | `NoError` | `Error` |
| `Missed` | violated, 3 transitions | `NoError` | `Error` |

TLC reports `Missed` violated by `x = TRUE`, `x = FALSE`, `x = FALSE`, then
stuttering. With `--length=3`, every lasso of this module is reachable.

In the counterexample to `Valid` at `--length=4`, the comment that `violation.tla`
prints above `State1` contains:

```text
  __InLoop = FALSE
    /\ (x ∧ ◇¬x) ⇒ ◇x = TRUE
    /\ x ∧ ◇¬x = TRUE
    /\ ◇¬x = TRUE
    /\ ◇x = FALSE
    /\ step = 1
    /\ x = FALSE
```

`x ∧ ◇¬x` is `TRUE` with `x = FALSE`. In the next state, `(x ∧ ◇¬x) ⇒ ◇x` is
`FALSE` with `x ∧ ◇¬x = FALSE`.

**Which shapes are affected.** On the same module at `--length=6`, Apalache reports
a counterexample to these valid properties:

- `[]((x /\ <>~x) => x)`
- `[]((x /\ <>~x) => <>x)`
- `[]((<>~x /\ x) => x)`
- `[](~(x /\ <>~x) \/ x)`
- `[]((TRUE /\ <>~x) => <>~x)`

These valid properties pass:

- `(x /\ <>~x) => x`, without `[]`
- `<>((x /\ <>~x) => x)`
- `[]((x \/ <>~x) => (x \/ <>~x))`
- `[]((x /\ []x) => x)`
- `[]((x /\ <>~x) => <>~x)`

Other violations are reported only past the lasso length. `([]x \/ x) ~> []x` and
`<>(x /\ <>~x) => <>[]x` are first reported at length 4, and
`([]x \/ (x /\ <>~x)) ~> []x` at length 5.

## Corpus evidence

corpus79 is a metamorphic corpus with temporal rewrites
([ADR 0027](../../docs/decisions/0027-temporal-metamorphic-testing.md)) and
`max_steps = 5`. In entry `235cdb54`, the rules `AlwaysOrBreak`, `F` to
`[]F \/ (F /\ <>~F)`, and `AlwaysOrIdem` rewrite the antecedent `var0` of
`var0 ~> [][](...)` into a `CASE` arm that contains `var0 /\ <>(~var0)`. The two
properties are equivalent. Apalache checked each under its own side's `Init` and
`Next` at `--length=6`. It reported the original violated and the rewrite
`NoError`, and reported the rewrite violated only at `--length=8`.

## Expected behavior

Equivalent temporal formulas have the same counterexamples. A valid property has
none at any length, and a lasso of length `k` is reported at `--length=k`.

## Impact

A spurious counterexample to a valid property misleads users of `--temporal`, for
any property with a conjunction such as `requested /\ <>granted` inside `[]` or
`~>`. A missed violation at the model's diameter breaks the usual way to
size `--length`. In FuzzTLA's temporal metamorphic tests, `AlwaysOrBreak` and
`EventuallyWeaken`, `F` to `F /\ <>F`, produce this shape whenever they rewrite a
property.
