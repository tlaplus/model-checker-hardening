---
state: open
labels: [apalache]
---

# Skolemizable `\E` over an expression that evaluates to `Int` crashes `QuantRule`

## Summary

When a skolemizable existential quantifies over a set *expression* whose value
is the infinite `Int` (or `Nat`) arena set rather than the syntactic literal
`Int`, Apalache's `QuantRule` reaches its final `case tp` branch and throws an
unhandled `UnsupportedOperationException`.

This is a second, distinct crash path for the infinite-domain quantification
limitation already recorded in
[`apalache-bmc-005.md`](apalache-bmc-005.md). `apalache-bmc-005` covers the
non-skolemizable expansion path (`QuantRule.expandExistsOrForall`, message
`Expansion of InfSet[CellTFrom(Int)] is not supported yet`). This finding covers
the skolemizable path (`QuantRule.apply`, message
`Quantification over InfSet[CellTFrom(Int)] is not supported yet`), which is
reached only when the bounding set is an expression and not the literal `Int`,
`Nat`, or `a..b`.

Upstream issue [`apalache-mc/apalache#72`](https://github.com/apalache-mc/apalache/issues/72)
tracks the same `QuantRule` "... is not supported yet" throw for `FinFunSet`
domains; it does not cover the `InfSet[CellTFrom(Int)]` case reached here.

Observed with Apalache 0.62.2, build `f0dec98`. The `corpus7` run found five
instances. The corpus evidence is the
[`2028e045...` input](../../corpus7/02apa-crash/2028e045d518903066b2a55c6aa09787f29bb67a8a799bd1419191a4a9cc2353.cbor)
and its [stacktrace](../../corpus7/02apa-crash/2028e045d518903066b2a55c6aa09787f29bb67a8a799bd1419191a4a9cc2353.stacktrace).

## Reproduction

```tla
---- MODULE SkolemIntExpr ----
EXTENDS Integers

VARIABLE
\* @type: Bool;
result

Init == result = TRUE /\ (\E x \in (IF result THEN Int ELSE Nat) : x = 0)
Next == UNCHANGED result
Inv == TRUE
====
```

Run:

```sh
java -Xmx1g -jar target/apalache.jar check \
  --init=Init --next=Next --inv=Inv --length=0 --no-deadlock \
  SkolemIntExpr.tla
```

The existential is a positive top-level occurrence, so it is skolemized. The
bounding set `IF result THEN Int ELSE Nat` is not the literal `Int`, so
`QuantRule` rewrites it to a cell of type `InfSet[CellTFrom(Int)]`, which matches
none of the supported cases:

```text
java.lang.UnsupportedOperationException:
Quantification over InfSet[CellTFrom(Int)] is not supported yet
    at at.forsyte.apalache.tla.bmcmt.rules.QuantRule.apply(QuantRule.scala:59)
EXITCODE: ERROR (255)
```

## Expected behavior

If skolemizing an existential over an infinite domain remains unsupported,
`QuantRule` should return Apalache's ordinary unsupported-input diagnostic. A
deliberate capability boundary must not escape as an implementation exception or
ask the user to report an unknown bug. Where the surrounding computation makes
the quantifier value irrelevant, it should be evaluated like the literal-`Int`
case in [`quantification-over-infinite-set.md`](../../conformance/quantification-over-infinite-set.md).

## Impact

A well-typed specification that passes every front-end phase aborts the bounded
checker with an internal exception and a spurious "report an issue" request. The
crash is invisible to `apalache-bmc-005`'s signature because the message and the
throwing site differ.
