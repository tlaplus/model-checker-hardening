---
state: open
labels: [apalache]
---

# `SetMapRule` throws `NotImplementedError` for a powerset

## Summary

Apalache's bounded checker throws an unhandled `scala.NotImplementedError` when
it evaluates a set map whose source set is a `PowSet` arena representation.
`MapBase.findSetCellAndElemType` has no branch for that representation, so the
exception escapes the rewriting pipeline, the tool exits with status 255, and it
asks the user to file a bug instead of returning a classified unsupported-input
result.

The other two symbolic-set representations are diagnosed rather than crashing:
a set map over `InfSet` reports `Input error (see the manual): Found a set map
over an infinite set`, and a set map over a function set reports `rewriter
error: Trying to expand a set of functions`. Both are covered by
[`apalache-cli-001.md`](../apalache-cli/apalache-cli-001.md). `PowSet` is the
one source representation that reaches the unimplemented branch.

This is the same class of defect as
[`apalache-bmc-001.md`](apalache-bmc-001.md), which reports it for
`SetFilterRule`, but a different rule in a different file and a separate fix.

Observed with Apalache 0.62.2, build `f0dec98`. The `corpus9` run contains one
instance. See the [`7d1513fa...` input](../../corpus9/02apa-crash/7d1513fa63addf9265897a0dfcb32c39b8c85aca03e0e4f0418a56aff3da6051.cbor)
and its [stacktrace](../../corpus9/02apa-crash/7d1513fa63addf9265897a0dfcb32c39b8c85aca03e0e4f0418a56aff3da6051.stacktrace).

## Reproduction

A powerset is materialized before this rule wherever the analysis marks it for
expansion, so the map source must be a powerset that stays symbolic. Choosing
the sole member of `{SUBSET {"a"}}` keeps it in its `PowSet` representation.
Save this module as `SetMapPowerset.tla`:

```tla
---- MODULE SetMapPowerset ----

VARIABLE
\* @type: Set(Set(Set(Str)));
result

\* @type: (() => Set(Set(Set(Str))));
Rhs == {{y} : y \in (CHOOSE sets \in {SUBSET {"a"}} : TRUE)}

Init == result = Rhs
Next == UNCHANGED result
Inv == TRUE

====
```

Run the pinned checker:

```sh
java -Xmx1g -jar target/apalache.jar check \
  --init=Init --next=Next --inv=Inv --length=0 --no-deadlock \
  SetMapPowerset.tla
```

Snowcat reports that all expressions are typed. The run reaches
`BoundedChecker` and terminates with:

```text
scala.NotImplementedError: A set map over PowSet[Set(Str)] is not implemented
    at at.forsyte.apalache.tla.bmcmt.rules.support.MapBase.findSetCellAndElemType$1(MapBase.scala:48)
    at at.forsyte.apalache.tla.bmcmt.rules.support.MapBase.rewriteSetMapManyArgs(MapBase.scala:52)
    at at.forsyte.apalache.tla.bmcmt.rules.SetMapRule.apply(SetMapRule.scala:29)
EXITCODE: ERROR (255)
```

The corpus instance reaches the same rule through a longer path. Its map source
is `SUBSET {var0} \union {}`, recovered through `Head(Head(Append(<<>>, CHOOSE
...)))` and consumed by a `CHOOSE`, an `EXCEPT` and a function application, so
the analysis never marks it for expansion either. It reports
`A set map over PowSet[Set(MODEL)] is not implemented` from the same two frames.

## Expected behavior

Either map over the unexpanded powerset representation, or reject the
expression through Apalache's ordinary unsupported-input mechanism, as the
`InfSet` and function-set sources already do. An unsupported construct must not
escape as an implementation exception.

## Impact

A small, well-typed set expression passes every front-end phase and then
terminates the run with a bug-report request rather than a checking result. An
automated consumer records it as a checker crash and excludes the specification
from verdict comparison.
