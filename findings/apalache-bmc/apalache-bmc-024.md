---
state: open
labels: [apalache]
---

# A `CHOOSE` without a witness is free or fixed depending on whether the set's cells coincide

## Summary

Apalache encodes a bounded `CHOOSE x \in S : P` without a witness as an unconstrained
value of its type. An optimization in `CherryPick.pickByOracle` overrides this when
every candidate cell of the filtered set is the same arena cell: it returns that cell
and ignores the oracle's "no element" case. Whether a no-witness `CHOOSE` is free or
pinned to a value therefore depends on how the set's elements were computed, not on
their values. In the reproduction below, `{FALSE, x}` with `x = FALSE` yields `TRUE`
when `x` comes from a fold, and only `FALSE` when `x` was assigned the literal `FALSE`.

As a consequence, Apalache reports counterexamples that it refutes when it replays
them, and it misses behaviors that its intended semantics allows. Reproduced with
Apalache 0.62.1 (`a321771`), 0.63.0 (`67b8ec6`), and the source at `a2910d423`
(`v0.63.0-2-ga2910d423`).

## Reproduction

Save this module as `ChooseCells.tla`:

```tla
---- MODULE ChooseCells ----
EXTENDS Integers, Apalache
VARIABLES
  \* @type: Bool;
  x,
  \* @type: Int;
  s
\* @type: (Bool, Str) => Bool;
Keep(acc, e) == acc
Init == x = FALSE /\ s = 0
Next == \/ s = 0 /\ x' = (ApaFoldSet(Keep, x, {"a", "b"})) /\ s' = 1
        \/ s = 1 /\ x' = (CHOOSE b \in {FALSE, x} : FALSE) /\ s' = 2
Inv == ~(s = 2 /\ x)
====
```

```sh
java -jar apalache.jar check --length=2 --inv=Inv ChooseCells.tla
```

The fold returns its accumulator, so `x = FALSE` in state 1, and `{FALSE, x} = {FALSE}`.
Apalache reports a violation in which the `CHOOSE` returned `TRUE`:

```text
State 2: state invariant 0 violated.
The outcome is: Error
EXITCODE: ERROR (12)
```

The trace has `x = FALSE` in states 0 and 1, and `x = TRUE` in state 2.

Replace the first disjunct's assignment with `x' = FALSE`. The module now describes
the same states, but Apalache reports `NoError`: the `CHOOSE` can only be `FALSE`.
The variants differ only in the provenance of `x`:

| `x = FALSE` in state 1 is set by | Set of the `CHOOSE` | Can the result be `TRUE`? |
| --- | --- | --- |
| `x' = FALSE`, `x' = ~TRUE`, `x' = x /\ TRUE`, `x' = x` | `{FALSE, x}` | no |
| `x' = ApaFoldSet(Keep, x, {"a", "b"})` | `{FALSE, x}` | yes |
| `x' = ApaFoldSet(Keep, x, {"a", "b"})` | `{x}` or `{FALSE}` | no |
| `x' = FALSE /\ x' = ApaFoldSet(Keep, x, {"a", "b"})` | `{FALSE, x}` | no |
| `x' = ApaFoldSet(Keep, x, {"a", "b"}) /\ x' = FALSE` | `{FALSE, x}` | yes |

TLC rejects the module: `CHOOSE x \in S: P, but no element of S satisfied P.` That is
the documented deviation of [`choose-without-witness`](../../conformance/choose-without-witness.md).
This finding is about Apalache contradicting itself, not about the deviation from TLC.

## Expected behavior

A `CHOOSE` without a witness has one semantics, whatever cells represent the set's
elements. `ChooseOrGuessRule` intends that semantics to be an unconstrained value
(see the root cause below). So both modules should admit `TRUE`, and the second
should report the violation too. If a fixed value were intended instead, neither
module should admit it.

## Impact

- **Missed counterexamples.** When the set's candidate cells coincide, Apalache
  pins the result, so it misses behaviors its intended semantics allows.
- **Counterexamples that do not replay.** When they do not coincide, Apalache
  reports traces through a free `CHOOSE`. A module that fixes the trace's states by
  assignments makes the cells coincide, and Apalache then refutes the trace. FuzzTLA's
  counterexample replay ([ADR 0034](../../docs/decisions/0034-counterexample-replay.md))
  found the defect this way.

The `CHOOSE` must have no witness in a reachable state, so the defect matters only
where a specification relies on a partial `CHOOSE`.

## Root cause

`ChooseOrGuessRule.apply` (`tla-bmcmt/.../rules/ChooseOrGuessRule.scala`) rewrites
`CHOOSE x \in S : P` into a pick from `{x \in S : P}`. `SetFilterRule` keeps every
cell of `S` as a potential element, guarded by `P`. Then `guess` distinguishes:

- If the filtered set has no cells (line 59), the result is the type's default value.
- Otherwise an oracle ranges over `0..N` for the `N` cells (line 72).
  `OracleHelper.assertOraclePicksSetMembers` requires value `i < N` to pick a member,
  and `N` to mean that no cell is a member. For `N`, the rule leaves the result
  unconstrained. Its comment (line 80) reads: *"If oracle = N, the picked cell is
  not constrained. In the past, we used a default value here, but it sometimes
  produced conflicts."*

`CherryPick.pickByOracle` (`tla-bmcmt/.../rules/support/CherryPick.scala`, line 117)
begins with an optimization:

```scala
// Optimization 1: if the (multi-) set consists of identical cells, return the cell
// (e.g., this happens when all enabled transitions do not change a variable.)
if (elems.distinct.size == 1) {
  return state.setRex(elems.head.toBuilder)
}
```

It returns the single cell without consulting the oracle. That is sound when the
oracle can only pick one of `elems`, as in the transition selection the comment
names. `ChooseOrGuessRule` passes an oracle with `elems.size + 1` values, and the
shortcut drops the last one, "no element", together with its unconstrained result.

Apalache shares one arena cell for the constant `FALSE`. When `x` is assigned
`FALSE`, the elements of `{FALSE, x}` are that one cell twice, so the shortcut pins
the result to `FALSE`. A fold creates a fresh cell for its result, equal to `FALSE`
only in the model. With two distinct cells, `pickBasic` runs, and the oracle's last
value leaves the result free.

A possible fix is to apply the shortcut only when the oracle cannot take a value
beyond `elems`, for example `oracle.size == elems.size`. Alternatively,
`ChooseOrGuessRule` could pick without the shortcut. Neither has been tried.

## How it was found

FuzzTLA's `ReplayRoundTripTest` records the counterexamples that TLC and Apalache
report on generated inputs, and replays each against both checkers. With the sample
seed `0x7acf`, Apalache refuted its own 16-step counterexample of a graph module.
Replaying growing prefixes of the trace showed that Apalache accepted states 0 to 2
and rejected the step from state 2 to state 3, from `var0 = FALSE` to
`var0 = TRUE`. That step runs the node action

```tla
Node2 == var0' = CHOOSE bound35 \in {(LET LocalOp36 == FALSE IN var0), FALSE, var0, FALSE, var0, FALSE} : var0
```

in which `var0`, at state 2, is the result of a fold in the previous node's action.
In the replay module, the trace's equality `var0' = FALSE` precedes the node action
and becomes the assignment, so the cells coincide and the `CHOOSE` is pinned.
Bisecting the module's JSON IR led to the reproduction above.

## Signature

`signatures/all-defects.toml` quarantines the literal shapes of a `CHOOSE` without a
witness, such as the predicate `FALSE` of the reproduction, under
`choose-literal-without-witness`. A `CHOOSE` whose predicate is false only at run
time, as in the generated `Node2`, cannot be matched structurally without matching
nearly every `CHOOSE`.

## Verification

- FuzzTLA `88d0fd60`; TLC 2026.11.19 (`bc1b506`); Apalache 0.62.1 (`a321771`), 0.63.0
  (`67b8ec6`), and the source at `a2910d423` for the line references.
- The reproduction reports `ERROR (12)` on Apalache 0.62.1 and 0.63.0. The variant
  with `x' = FALSE` reports `OK` on 0.63.0. Every row of the table was run on 0.63.0
  with `--length=2`.
- TLC reports `CHOOSE x \in S: P, but no element of S satisfied P.` on the
  reproduction.
