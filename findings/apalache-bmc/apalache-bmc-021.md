---
state: open
labels: [apalache]
---

# A symbolic set reaching its use site through an expression is encoded as empty

## Summary

Apalache 0.62.2 represents `SUBSET S` and `[S -> T]` as symbolic arena cells,
`PowSet` and `FinFunSet`, rather than as enumerated set cells. When such a value
reaches its use site through an expression that yields a fresh result cell — an
`IF`, or the result of `ApaFoldSet` or `ApaFoldSeqLeft` — instead of being
written at that site, the cell is read as an ordinary finite set with no
elements. A bounded `\E` over it is then unsatisfiable, and an equality with
`{}` holds.

Both readings are silent. An action guarded by such an existential stays
disabled at every step and the run ends with `NoError` and exit status 0; an
invariant that compares the value with `{}` is reported violated although it
holds. Writing the same set at the use site is unaffected: the `\E` is encoded
correctly, and the equality is rejected by the type guard of
[apalache-bmc-003](apalache-bmc-003.md) rather than answered wrongly.

The defect is not specific to either value class or to folds.
[apalache-bmc-020](apalache-bmc-020.md) records the `SUBSET`-through-a-fold
corner of it; the `IF` rows below reproduce that corner with no fold, so the
mechanism proposed there — a fold result cell carrying the expanded result type
of its combinator — does not explain the whole defect. The shared condition is
the intermediate expression, whatever produces it. The mechanism remains
unconfirmed.

Observed with Apalache 0.62.2 (build `f0dec98`); TLC is tla2tools
`1.8.0-20260917.033119-76` (tlaplus commit `142d0ba`); FuzzTLA is `3f4ce33`.

## Reproduction

`IfFunSet.tla`:

```tla
---- MODULE IfFunSet ----
EXTENDS Integers, Apalache
VARIABLE
\* @type: Bool;
var0
VARIABLE
\* @type: Int;
step
Init == var0 = TRUE /\ step = 0
Next ==
  (\E p \in (IF step >= 0 THEN [{1, 2, 3} -> {TRUE, FALSE}] ELSE {[i \in {1} |-> TRUE]}):
      step < 5 /\ var0' = FALSE /\ step' = step + 1)
    \/ UNCHANGED <<var0, step>>
Inv == var0
====
```

The `IF` condition is true in every state, so the existential ranges over the
eight functions of `[{1, 2, 3} -> {TRUE, FALSE}]` and the action is enabled
while `step < 5`.

```sh
apalache-mc check --init=Init --next=Next --inv=Inv --length=5 \
  --no-deadlock IfFunSet.tla
```

```text
PASS #13: BoundedChecker
State 0: Checking 1 state invariants
State 0: state invariant 0 holds.
Step 0: picking a transition out of 1 transition(s)
Step 1: Transition #1 is disabled
...
Step 5: Transition #1 is disabled
Step 5: picking a transition out of 1 transition(s)
The outcome is: NoError
Checker reports no error up to computation length 5
EXITCODE: OK
```

TLC, with `Spec == Init /\ [][Next]_<<var0, step>>`, `SPECIFICATION Spec` and
`INVARIANT Inv`, fires the action and reports the violation with exit status 12:

```text
Error: Invariant Inv is violated.
Error: The behavior up to this point is:
State 1: <Initial predicate>
/\ step = 0
/\ var0 = TRUE

State 2: <Next line 11, col 4 to line 12, col 51 of module IfFunSet>
/\ step = 1
/\ var0 = FALSE
```

## Variants

Every row uses the arguments above. `[S -> T]` is
`[{1, 2, 3} -> {TRUE, FALSE}]` and `SUBSET S` is `SUBSET {"1", "2"}`; the `ELSE`
branch of each `IF` is an unreachable nonempty set of the same type, and each
fold combinator is constant, so the value is the same in any iteration order.

| Set at the use site | Apalache 0.62.2 | TLC |
| --- | --- | --- |
| `\E p \in [S -> T]`, written at the binder | violation found | violation |
| `\E p \in FS` with `FS == [S -> T]` | violation found | violation |
| `\E p \in IF c THEN [S -> T] ELSE …` | **NoError** | violation |
| `\E p \in ApaFoldSet(L, …)`, `L` returning `[S -> T]` | **NoError** | violation |
| `\E p \in IF c THEN SUBSET S ELSE …` | **NoError** | violation |
| `~(\E p \in IF c THEN [S -> T] ELSE …: p[1])` as the invariant | **NoError** | violation |
| `[S -> T] # {}`, written at the comparison | crash, [apalache-bmc-003](apalache-bmc-003.md) | holds |
| `(IF c THEN [S -> T] ELSE …) # {}` | **violated** | holds |
| `(IF c THEN SUBSET S ELSE …) # {}` | **violated** | holds |
| `ApaFoldSet(L, …) # {}`, `L` returning `[S -> T]` | **violated** | holds |
| `\E p \in IF c THEN {1, 2, 3} ELSE {0}`, an enumerated set | violation found | violation |
| `fn \in (IF c THEN [S -> T] ELSE …)` in `Init` | violation found | violation |
| `\A p \in IF c THEN [S -> T] ELSE …` | crash, `Trying to expand a set of functions` | — |

Three rows bound the defect. An enumerated set through the same `IF` is
correct, so the intermediate expression alone is not enough: the value has to
be one of the symbolic classes. Membership of a single function,
`fn \in (IF …)`, is correct, so the fault is at the binder and the comparison,
not in the `IF` itself. A `\A` over the same expression reaches the documented
expansion guard
([guarded expansion of a function set](../../conformance/function-set-expansion-guard.md))
instead of answering silently, which is the behavior the other rows should
have when the encoding cannot proceed.

## Corpus evidence

corpus36 has one aggregator deviation with this cause, `eebf3287`, in the
TLC-counterexample/Apalache-pass direction. Its next-state action is

```tla
Next ==
  (\E actionParam5 \in ApaFoldSet(Lambda8, S0, {"1", "2", "3"}):
      step < 5 /\ var0' = FALSE /\ UNCHANGED var1 /\ step' = step + 1)
    \/ UNCHANGED <<var0, var1, step>>
```

where `Lambda8` ignores both parameters and returns
`[VariantGetUnsafe("Tag2", Variant("Tag2", {1, 2, 3})) -> {TRUE, FALSE}]`, so
the fold is that function set in any order. `Init` fixes `var0 = TRUE` and
`Inv` reduces to `var0`. TLC reports `Invariant Inv is violated` and exits 12.
Apalache, rerun on the entry's IR with the workflow's arguments (`--length=5`,
`--no-deadlock`), keeps the transition disabled at every step and reports
`NoError`.

The combinator is constant, so this entry is not
[an order-sensitive fold](../../conformance/order-sensitive-set-fold.md): it is
the only one of corpus36's 34 unclassified aggregator deviations that is
neither that class, [tlc-008](../TLC/tlc-008.md), nor
[`CHOOSE` with more than one witness](../../conformance/choose-multiple-witnesses.md).

## Expected behavior

Apalache encodes the symbolic set the same way wherever it reaches its use
site, so that the `IF` and fold rows above agree with the rows that write the
set at the binder. Where the encoding cannot proceed, it reports the limitation,
as the `\A` row does, rather than answering as if the set were empty.

## Impact

A soundness defect. Apalache misses reachable invariant violations whenever a
`PowSet` or `FinFunSet` value passes through an intermediate expression on its
way to a bounded existential, and reports spurious ones whenever such a value is
compared with `{}`. Neither direction leaves a diagnostic, and the exit status
is the one a checked specification produces. The conformance aggregator records
the disagreement as TLC's, since TLC is the side that reports the violation.
