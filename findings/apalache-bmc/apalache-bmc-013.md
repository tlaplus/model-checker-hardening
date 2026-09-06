---
state: open
labels: [apalache]
---

# `ApaFoldSet` feeds a raw arena cell into `x \in {y}`, crashing `SetInRule`

## Summary

`FoldSetRule` substitutes the fold accumulator into the combinator body as a
raw arena cell (`NameEx("$C$<n>")`). When the body then tests that cell for
membership in a syntactic singleton set literal, `SetInRule` takes its
`x \in {y}` fast path and looks the cell name up in the symbolic binding with
`state.binding(name)`. Arena-cell names are not binding keys, so the lookup
throws `java.util.NoSuchElementException: key not found: $C$<n>` and the bounded
checker aborts.

`SetInRule.isApplicable` accepts the term because `ArenaCell.isValidName` is
true for `$C$<n>`, but `SetInRule.apply` then assumes the left operand is a
bound variable.

This is upstream issue
[`apalache-mc/apalache#3479`](https://github.com/apalache-mc/apalache/issues/3479),
fixed by [PR #3480](https://github.com/apalache-mc/apalache/pull/3480) (merged
2026-09-04). The fix guards the `SetInRule` and `PrimeRule` optimization so a
cell name is never looked up in the binding. It is not in a release yet: the
pinned build is Apalache 0.62.2, build `f0dec98`, released 2026-08-26, which
still crashes. Found in one `corpus7` input. See the
[`569471e2...` input](../../corpus7/02apa-crash/569471e2774b2e45f67dfc7d2123129f9a29c8ffac0161cd754d655f0ef50824.cbor)
and its [stacktrace](../../corpus7/02apa-crash/569471e2774b2e45f67dfc7d2123129f9a29c8ffac0161cd754d655f0ef50824.stacktrace).

## Reproduction

```tla
---- MODULE FoldAccumIn ----
EXTENDS Apalache

VARIABLE
\* @type: Bool;
result

\* @type: (Bool, Bool) => Bool;
Step(acc, x) == acc \notin {acc}

Init == result = ApaFoldSet(Step, FALSE, {TRUE})
Next == UNCHANGED result
Inv == TRUE
====
```

Run:

```sh
java -Xmx1g -jar target/apalache.jar check \
  --init=Init --next=Next --inv=Inv --length=0 --no-deadlock \
  FoldAccumIn.tla
```

`\notin` desugars to `~(acc \in {acc})`. `FoldSetRule` has already replaced
`acc` with the accumulator cell, so `SetInRule` evaluates `$C$0 \in {$C$0}`:

```text
java.util.NoSuchElementException: key not found: $C$0
    at at.forsyte.apalache.tla.bmcmt.Binding.apply(Binding.scala:11)
    at at.forsyte.apalache.tla.bmcmt.rules.SetInRule.apply(SetInRule.scala:40)
    at at.forsyte.apalache.tla.bmcmt.rules.FoldSetRule.$anonfun$apply$1(FoldSetRule.scala:111)
    at at.forsyte.apalache.tla.bmcmt.rules.FoldSetRule.apply(FoldSetRule.scala:96)
====
```

Issue #3479 reproduces the same crash through `IfThenElseRule`
(`Count(acc, i) == IF i \in {1} THEN acc + 1 ELSE acc`); this corpus instance
reaches `SetInRule` through `NegRule`. Both are the same defect in the
`SetInRule` fast path, and PR #3480 guards that path rather than a single
caller.

## Expected behavior

`SetInRule` should handle a left operand that is already an arena cell instead
of assuming a bound-variable name. A fold whose combinator only refers to its
own parameters must evaluate without an internal crash.

## Impact

A small, well-typed `ApaFoldSet` expression crashes the bounded checker with an
internal `NoSuchElementException` and a spurious "report an issue" request. Any
folded combinator that performs a set-membership test on its accumulator or
element against a singleton set literal is affected in the pinned build.

## Resolution

Merged upstream as [PR #3480](https://github.com/apalache-mc/apalache/pull/3480)
on 2026-09-04, after the 0.62.2 release. Close this finding once FuzzTLA pins an
Apalache build that contains commit `8f0113e`, and drop the
`apalache-bmc-013.md` signature from `script/triager.py` at the same time.
