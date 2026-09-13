---
state: open
labels: [apalache]
---

# A computed empty co-domain crashes `CherryPick` on a function set

## Summary

For a nonempty `S`, the function set `[S -> R]` is empty when `R` is empty, so
`fn \in [S -> R]` has no model. Apalache 0.62.2 handles this when `R` is the
literal `{}` or a set filter, but crashes when `R` is an empty set produced by
`ApaFoldSet`:

```text
java.lang.RuntimeException: The set $C$7 is statically empty. Pick should not be called on that.
	at at.forsyte.apalache.tla.bmcmt.rules.support.CherryPick.pick(CherryPick.scala:81)
	at at.forsyte.apalache.tla.bmcmt.rules.support.CherryPick.$anonfun$pickFunFromFunSet$6(CherryPick.scala:998)
	at at.forsyte.apalache.tla.bmcmt.rules.support.CherryPick.pickFunFromFunSet(CherryPick.scala:997)
	at at.forsyte.apalache.tla.bmcmt.rules.support.CherryPick.pick(CherryPick.scala:52)
	at at.forsyte.apalache.tla.bmcmt.rules.QuantRule.skolemExistsByPick(QuantRule.scala:293)
```

`CherryPick.pickFunFromFunSet` picks one result from the co-domain for every
domain element, under the comment "the co-domain should be non-empty". The
generic `pick` rejects a co-domain cell that has no elements in the arena. The
checker exits with status 255 instead of reporting that there is no initial
state.

| Initial predicate | Apalache 0.62.2 | TLC |
| --- | --- | --- |
| `fn \in [{"a"} -> {}]` | no initial state | no initial state |
| `fn \in [{"a"} -> {x \in {"a"} : FALSE}]` | no initial state | no initial state |
| `fn \in [{"a"} -> ApaFoldSet(Keep, {}, {})]` | crash | no initial state |

This is the co-domain counterpart of
[`apalache-bmc-017`](apalache-bmc-017.md), which misencodes a computed *empty
domain*. The two fail differently, silently versus with an exception, and in
different code.

The `corpus16` run contains one Apalache crash with this cause. Its initial
predicate is `var0 \in [{"yQt"} -> ApaFoldSet(Lambda90, {}, {})]`, where the
fold body builds a large set expression that is never used. See the
[`e25a75d2...` input](../../corpus16/02apa-crash/e25a75d22792e9ce6f579b0f9513e3b3be543e39a951ae66c1bcaae340558446.cbor)
and its [stacktrace](../../corpus16/02apa-crash/e25a75d22792e9ce6f579b0f9513e3b3be543e39a951ae66c1bcaae340558446.stacktrace).

Observed with Apalache 0.62.2, build `f0dec98`.

## Reproduction

```tla
---- MODULE EmptyCodomainPick ----
EXTENDS Integers, Apalache

VARIABLE
\* @type: (Str -> Str);
fn

\* @type: (Set(Str), Int) => Set(Str);
Keep(acc, x) == acc

Init == fn \in [{"a"} -> ApaFoldSet(Keep, {}, {})]
Next == UNCHANGED fn
Inv == TRUE
====
```

```sh
java -Xmx1g -jar apalache.jar check \
  --init=Init --next=Next --inv=Inv --length=1 --no-deadlock EmptyCodomainPick.tla
```

```text
PASS #13: BoundedChecker
Unhandled exception
java.lang.RuntimeException: The set $C$7 is statically empty. Pick should not be called on that.
```

TLC, with `INIT Init`, `NEXT Next`, `INVARIANT Inv`, reports
`Finished computing initial states: 0 distinct states generated` and completes
without error. Replacing the co-domain with `{}` or with
`{x \in {"a"} : FALSE}` makes Apalache find no initial state as well.

## Expected behavior

`[S -> R]` with a nonempty `S` and an empty `R` is the empty set. Picking a
function from it should constrain the membership to be false, as Apalache does
for the literal and filter co-domains, instead of throwing.

## Impact

A well-formed specification crashes the model checker when a function set's
co-domain is computed rather than written literally. The run ends as an
unclassified internal error, and a user is asked to file a bug report.
