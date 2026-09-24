---
state: open
labels: [apalache]
---

# `[][A]_v` under a Boolean connective needs one more step than at the top level

## Summary

Apalache finds a violation of `[][A]_v` within a shorter bound when the property
is checked on its own than when the same property is an operand of a Boolean
connective. For a module whose shortest counterexample lasso has 6 transitions,
`--temporal=Prop` with `Prop == [][step <= 3]_x` reports the violation at
`--length=6`. `--temporal=Liveness` with `Liveness == TRUE => Prop` reports
`NoError` and "Checker reports no error up to computation length 6". It finds the
violation only at `--length=7`. `Prop /\ TRUE` and `FALSE \/ Prop` behave like
`TRUE => Prop`. A state-level `[](step <= 3)` is found at `--length=5` in both
positions.

Observed with Apalache 0.62.2 (build `f0dec98`), FuzzTLA `1fd3fe9`, and TLC
commit `142d0ba` (tla2tools `1.8.0-20260917.033119-76`).

## Reproduction

`FuzzInput.tla`:

```tla
---- MODULE FuzzInput ----
EXTENDS Integers
VARIABLE
  \* @type: Bool;
  x
VARIABLE
  \* @type: Int;
  step
Init == x = TRUE /\ step = 0
Next == (step < 5 /\ x' = ~x /\ step' = step + 1) \/ UNCHANGED <<x, step>>
Spec == Init /\ [][Next]_<<x, step>>
Inv == TRUE
Prop == [][step <= 3]_x
Liveness == TRUE => Prop
====
```

```sh
apalache-mc check --init=Init --next=Next --inv=Inv --temporal=Liveness --length=6 FuzzInput.tla
```

```text
The outcome is: NoError
Checker reports no error up to computation length 6
EXITCODE: OK
```

The transition from `step = 4` to `step = 5` changes `x` while `step <= 3` is
false. A lasso needs that fifth transition and one stuttering transition that
closes the loop at `step = 5`, so 6 transitions suffice. The first length at which
Apalache reports the violation:

| `--temporal=` | First violating length |
| --- | ---: |
| `[][step <= 3]_x` | 6 |
| `TRUE => [][step <= 3]_x` | 7 |
| `~FALSE => [][step <= 3]_x` | 7 |
| `[][step <= 3]_x /\ TRUE` | 7 |
| `FALSE \/ [][step <= 3]_x` | 7 |
| `[](step <= 3)` | 5 |
| `TRUE => [](step <= 3)` | 5 |

In both cases the log reports `TemporalPass` "Found 1 temporal properties" and
"Adding logic for loop finding". With `SPECIFICATION Spec` and `PROPERTY Prop`,
TLC reports `Action property Prop is violated` after the transition from
`step = 4` to `step = 5`. TLC rejects `PROPERTY Liveness` with `Temporal formulas
containing actions must be of forms <>[]A or []<>A`. That limit is recorded in
[tlc-temporal-formula-limits](../../conformance/tlc-temporal-formula-limits.md).

## Corpus evidence

FuzzTLA checks a module's temporal property with Apalache as
`Liveness == Fairness => Prop`, where `Fairness == TRUE` without fairness
conditions, and with `--length` one more than the longest path of the step
counter (ADR 0007). In corpus49 entry `c0a82824`, `max_steps = 5` and

```tla
Prop == [][(LET LocalOp14(p) == ... IN SetMaxWith({1, 2, 3}, step)) \in {1, 2, 3}]_var0
```

where `SetMaxWith(S, d)` is the maximum of `S` and `d`. TLC reports that `Prop` is violated
by a 6-state behavior whose last transition changes `var0` at `step = 4`.
Apalache reports `NoError` at `--length=6`. The reduction above replaces the
expression by `step <= 3`, drops the unused `LET` and the other variable, and
keeps the result.

## Expected behavior

Equivalent temporal formulas have the same counterexamples, so `P` and
`TRUE => P` are violated at the same bound. When a lasso of length `k` violates
the property, `--length=k` reports it.

## Impact

`NoError` up to length `k` is a claim about all behaviors of that length. Here
the claim is false for any action property that is not the whole temporal
formula, which includes the usual `Fairness => Prop` pattern. A user who sizes
`--length` from the model's diameter misses the violation. FuzzTLA's temporal
checks use exactly this shape, so every generated `[][A]_v` violation that
needs the last transition is reported only by TLC.
