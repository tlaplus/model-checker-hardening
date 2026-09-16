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

## Recurring in `corpus22`

The `module` corpus22, generated with the `action` and `temporal` categories,
has 63 aggregator deviations where TLC reports "Invariant Inv is violated by the
initial state" and Apalache passes. 59 of them contain `ENABLED`; the other 4 are
[apalache-bmc-017](apalache-bmc-017.md). Six of the 59 were rerun with Apalache
0.62.2 on their IR, and each ended with `NoError`. In four of the six (`05676e5c`,
`469e02e6`, `7014ee9c`, `de1d28ee`) the invariant has `ENABLED` in a `CASE` guard.
In `de25b1a9` `ENABLED` occurs in the predicate of a `CHOOSE`, and in `a8c6f937`
in a `LET` definition, `LocalOp10 == ENABLED (var1 = step)`; these two were not
reduced, so whether a `CASE` guard is involved there is open. The remaining 53
were not rerun.

## Recurring in `corpus25`

The `module` corpus25 run, generated like corpus24 with twice its entry budget,
has 115 aggregator deviations where TLC reports "Invariant Inv is violated by
the initial state" and Apalache passes. All 115 were rerun, TLC on the printed
module and Apalache 0.62.2 on the entry's IR with the workflow's arguments,
including `--temporal=Liveness`.

107 have `ENABLED` in the invariant. 106 of them end with `NoError` and exit
status 0, so Apalache explored states, neither rejected `ENABLED` with exit 75
nor reported the violation TLC sees. This is the shape of the summary at the
scale of a whole run, and the first time every deviation in the group was rerun
rather than sampled. The remaining eight deviations are
[apalache-bmc-017](apalache-bmc-017.md).

Where `ENABLED` sits inside the invariant, counting each context that occurs in
an entry: a `CASE` guard in 62, an `IF` condition in 24, a `CHOOSE` predicate in
8, and 25 entries reach it only through a `LET` definition or another
subexpression. The contexts were read off the printed module, not reduced, so
the counts locate the occurrence and do not attribute the pass to it. The
`CASE`-guard group is the reproduction above.

One entry does not fit. `ba60e0c4` has `ENABLED` in a `CHOOSE` predicate and was
recorded as an Apalache pass during the run, but the rerun of its IR exits 255
with `internal error in type checking: FoldSet argument ... found Bool`, the
diagnostic of
[apalache-temporal-002](../apalache-temporal/apalache-temporal-002.md). The same
input therefore passed once and crashed once. Whether the difference is in
Apalache or in the IR the worker feeds it was not established.

## Recurring in `corpus26`

The `module` corpus26 run, generated like corpus25 with half its entry budget,
has 62 aggregator deviations where TLC reports a counterexample and Apalache
passes. All 62 were rerun as in corpus25. Each TLC rerun reports
`Invariant Inv is violated by the initial state` and exits 12, and each Apalache
0.62.2 rerun ends with `NoError` and exit status 0. All 62 have `ENABLED` in the
invariant.

None is [apalache-bmc-017](apalache-bmc-017.md): no rerun reports
`All executions are shorter than the provided bound`. A `[D -> {}]` function set
occurs somewhere in 49 of the 62, but, as in corpus25, Apalache explores states
and answers the invariant. The position of `ENABLED` inside the invariant was
not counted, and no entry was reduced.

## Expected behavior

Apalache either rejects `ENABLED` as unsupported, with exit 75, or checks the
invariant and reports the violation of `FALSE` in the initial state.

## Impact

Apalache reports a violated invariant as holding, without a diagnostic. A user
relying on the verdict concludes the property holds. An automated comparison
with TLC records the difference but cannot attribute it without reduction.
