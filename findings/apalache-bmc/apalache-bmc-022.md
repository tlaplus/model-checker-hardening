---
state: open
labels: [apalache]
---

# `VariantFilter` loses an element whose payload is `FALSE` or `0`

## Summary

Apalache's bounded model checker encodes `VariantFilter(tag, V)` with
constraints that contradict each other when `V` contains a variant with a
different tag and the selected payload is `FALSE` or `0`. Any predicate that
evaluates such a filter becomes unsatisfiable. In `Init`, Apalache finds no
initial state: it reports a deadlock, or with `--no-deadlock` it reports no
error while checking nothing. For example,
`VariantFilter("B", { Variant("A", TRUE), Variant("B", FALSE) }) = {FALSE}` has
no model, and neither has `FALSE \in VariantFilter("B", ...)`. TLC evaluates the
filter to `{FALSE}`.

Observed with Apalache 0.62.2 (build `f0dec98`), tla2tools 1.8.0-SNAPSHOT
(Maven snapshot `1.8.0-20260917.033119-76`, tlaplus/tlaplus commit `142d0ba`),
and FuzzTLA `91871fd`. The source cited below is unchanged between `f0dec98`
and Apalache commit `1371658a2`.

## Reproduction

`VariantFilterFalse.tla`:

```tla
---- MODULE VariantFilterFalse ----
EXTENDS Variants
VARIABLE
  \* @type: Bool;
  x
\* @type: Set(A(Bool) | B(Bool));
V == { Variant("A", TRUE), Variant("B", FALSE) }
Init == x = FALSE /\ VariantFilter("B", V) = {FALSE}
Next == UNCHANGED x
Inv == x
====
```

```sh
apalache-mc check --length=0 --inv=Inv VariantFilterFalse.tla
apalache-mc check --length=0 --inv=Inv --no-deadlock VariantFilterFalse.tla
```

```text
Found a deadlock.
EXITCODE: ERROR (12)

Checker reports no error up to computation length 0
EXITCODE: OK
```

TLC, with `INIT Init`, `NEXT Next` and `INVARIANT Inv`, reports
`Invariant Inv is violated by the initial state`, which is the expected
verdict.

With the same `V`, `VariantFilter("A", V) = {TRUE}` is satisfiable. Varying the
payloads of a two-tag set, each row filtered by `"B"`:

| `V` | `Init` conjunct | Apalache |
| --- | --- | --- |
| `{A(TRUE), B(FALSE)}` | `VariantFilter("B", V) = {FALSE}` | no initial state |
| `{A(FALSE), B(FALSE)}` | `VariantFilter("B", V) = {FALSE}` | no initial state |
| `{A(TRUE), B(TRUE)}` | `VariantFilter("B", V) = {TRUE}` | correct |
| `{A(1), B(FALSE)}` | `VariantFilter("B", V) = {FALSE}` | no initial state |
| `{A(1), B(0)}` | `VariantFilter("B", V) = {0}` | no initial state |
| `{A(1), B(5)}` | `VariantFilter("B", V) = {5}` | correct |
| `{A(1), B("")}` | `VariantFilter("B", V) = {""}` | correct |
| `{B(FALSE)}` | `FALSE \in VariantFilter("B", V)` | correct |
| `{A(TRUE), B(FALSE)}` | `x \in {VariantGetUnsafe("B", v) : v \in {w \in V : VariantTag(w) = "B"}}` | correct |

## Cause

A variant is a record with one field per tag. `makeVariant`
(`RecordAndVariantOps.scala`, lines 87-110) fills the fields of the other tags
with `DefaultValueFactory.makeUpValue`. For `Bool`, this is `arena.cellFalse()`,
and for `Int`, it is the cached cell of `0` (`DefaultValueFactory.scala`, lines
30-34). The literals `FALSE` and `0` also use these cells.

`variantFilter` (`RecordAndVariantOps.scala`, lines 241-272) reads the
`tagName` field of every variant in the set, whatever its tag, and asserts:

```scala
val values = variants.map(v => getUnsafeVariantValue(nextState.arena, v, tagName))
variants.zip(values).foreach { case (variant, value) =>
  ...
  val ifCond = tla.and(tagsEq, inOriginal)
  ...
  val storeIf = tla.ite(ifCond, inFiltered, notInFiltered)
  rewriter.solverContext.assertGroundExpr(storeIf)
}
```

For `Variant("B", FALSE)`, `ifCond` holds, so the constraint stores the `FALSE`
cell in the filtered set. For `Variant("A", TRUE)`, `ifCond` is false, and
`value` is the default `B` field, which is the same `FALSE` cell. The
constraint then asserts that this cell is not in the filtered set. Together,
the two constraints are unsatisfiable. A payload that is a different cell,
such as `5`, `""`, or `TRUE`, gives no conflict. With a single tag no
default field exists.

## Corpus evidence

The `module` corpus44 run has one aggregator deviation with this cause,
`41ca4840`, a PBT input of generation 4. Its `Init` contains

```tla
var0 \in VariantFilter("Tag3", { Variant("Tag3", FALSE),
                                 Variant("Tag2", << [i \in {TRUE} |-> TRUE], [i \in {FALSE} |-> FALSE] >>),
                                 ... })
/\ var1 = FALSE
```

with `Inv == var1`. TLC reports that the initial state violates `Inv`. Apalache,
run with `--no-deadlock` as the workflow does, finds no initial state and
passes. The entry applies neither `ApaFoldSet` nor `SetToSeq`, and no other
operator with a known deviation.
Five other `NEW` entries of the run apply `VariantFilter`. Their deviations
are explained by fold or `SetToSeq` order.

## Expected behavior

`VariantFilter("B", V)` is the set of payloads of the `B` variants in `V`. The
encoding must not assert that a cell is outside the filtered set only because
that cell is the default `B` payload of a variant with another tag. The fix
was not tested.

## Impact

This is a soundness defect. When the filter occurs in `Init`, Apalache
verifies nothing and still reports success with `--no-deadlock`. Elsewhere, it
disables actions or makes an invariant or a guard vacuous. The trigger is
common: a Boolean or integer variant payload with the default value, in a set
that contains at least two tags.
