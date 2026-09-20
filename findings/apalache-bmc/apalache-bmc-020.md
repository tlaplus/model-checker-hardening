---
state: open
labels: [apalache]
---

# A fold whose combinator returns `SUBSET` evaluates to the empty set

## Summary

When the combinator of `ApaFoldSet` or `ApaFoldSeqLeft` returns a `SUBSET`
expression, Apalache 0.62.2 treats the fold result as the empty set: an
equality `fold # {}` does not hold, and an `\E` over the result is disabled.
TLC computes the powerset. A combinator returning any other constant set is
unaffected, and the same `SUBSET` expression outside a fold is handled
correctly — comparing it to `{}` crashes with the
`Unexpected equality test over types PowSet[Set(Str)] and CellTFrom(...)` error
of [apalache-bmc-003](apalache-bmc-003.md). Through a fold, the same value
silently compares equal to `{}` instead.

A plausible mechanism is that the fold's result cell carries the expanded
result type of the combinator, `Set(Set(Str))`, while the combinator produces a
`PowSet` arena cell; reads of the result then misinterpret the cell. The
mechanism is unconfirmed, and it does not cover the whole defect:
[apalache-bmc-021](apalache-bmc-021.md) reproduces the rows below with an `IF`
in place of the fold, and with `[S -> T]` in place of the `SUBSET`. This
finding is that general defect restricted to a `SUBSET`-valued fold.

Observed with Apalache 0.62.2 (build `f0dec98`); TLC is tla2tools
`1.8.0-20260917.033119-76` (tlaplus commit `142d0ba`); FuzzTLA is `80f63a5`.

## Reproduction

`FuzzInput.tla`:

```tla
---- MODULE FuzzInput ----
EXTENDS Integers, Apalache
VARIABLE
  \* @type: Int;
  step
(* @type: (Set(Set(Str)), Str) => Set(Set(Str)); *)
L(a, b) == SUBSET { "1", "2" }
Init == step = 0
Next == (step < 5 /\ step' = step + 1) \/ UNCHANGED <<step>>
Inv == ApaFoldSet(L, {}, {"1", "2", "3"}) # {}
====
```

```sh
apalache-mc check --init=Init --next=Next --inv=Inv --length=2 --no-deadlock FuzzInput.tla
```

```text
The outcome is: Error
EXITCODE: ERROR (12)
```

`L` is constant, so the fold is `SUBSET {"1", "2"}` — a four-element set — in
any iteration order, and the invariant holds. TLC reports no error. Apalache
reports the invariant violated, that is, its fold compares equal to `{}`.

Variants:

| Combinator and context | Apalache | TLC |
| --- | --- | --- |
| `L(a, b) == SUBSET {"1", "2"}`, `ApaFoldSet` over `{"1", "2", "3"}`, invariant `fold # {}` | violated | holds |
| the same combinator in `ApaFoldSeqLeft` over `<<"1", "2", "3">>` | violated | holds |
| `L(a, b) == SUBSET {...}`, nonempty base `{{<<"9">>}}`, invariant `fold = {{<<"9">>}}` | violated: the result is not the base either | holds |
| `L(a, b) == {<<"1">>}` (constant, not a powerset) | holds | holds |
| `(SUBSET {"1", "2"}) # {}` without a fold | crash, [apalache-bmc-003](apalache-bmc-003.md) | holds |
| the fold as the domain of `\E p \in fold` in `Next` | transition disabled from the initial state | fires |

The `\E` row is the corpus shape. The `corpus30` deviation
[`05b268ce...`](../../corpus30/03aggregator-fail/05b268ced06dd896abd4885e614fe8c5ca3613355f4892df232f300674f26e3b.cbor)
has

```tla
Next == (\E p \in ApaFoldSet(Lambda10, S0, {"1", "2", "3"}): step < 5 /\ ...) \/ UNCHANGED ...
Inv == 1 >= step
```

with a constant, `SUBSET`-valued `Lambda10`. TLC fires the action and reports
`Invariant Inv is violated` at `step = 2` (error 2110). Apalache checks the IR
with the workflow's arguments (`--length=6`, `--no-deadlock`), keeps the `\E`
transition disabled at every step, and reports `NoError`.

## Expected behavior

Apalache computes the powerset, or rejects the fold with the same
diagnostic it gives a direct `SUBSET` comparison. It must not silently
evaluate the fold to the empty set.

## Impact

A soundness defect: Apalache misses reachable invariant violations whenever a
`SUBSET`-valued fold result guards an action or appears in the invariant. The
conformance aggregator records the disagreement as TLC's, since TLC is the side
that reports the violation.
