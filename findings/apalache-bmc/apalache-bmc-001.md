---
state: open
---

# `SetFilterRule` throws `NotImplementedError` for symbolic sets

## Summary

Apalache's bounded checker throws an unhandled `scala.NotImplementedError` when
it evaluates a set filter over several symbolic-set representations. The
inspected corpus contains 20 instances: five over `InfSet`, twelve over
`PowSet`, and three over `FinFunSet`. The tool exits with status 255 and asks the
user to report a bug instead of returning a classified unsupported or
input-evaluation result.

Observed with Apalache 0.62.0.

## Representative reproduction

Save this module as `SetFilterInt.tla`:

```tla
---- MODULE SetFilterInt ----
EXTENDS Integers

VARIABLE
\* @type: Set(Int);
x

Init == x = { y \in Int : y = 0 }
Next == UNCHANGED x
Inv == TRUE

====
```

Run the pinned checker:

```sh
java -Xmx1g -jar target/apalache.jar check --init=Init --next=Next --inv=Inv \
  --length=0 --no-deadlock SetFilterInt.tla
```

The run reaches `BoundedChecker` and terminates with:

```text
scala.NotImplementedError: A set filter over InfSet[CellTFrom(Int)] is not implemented
    at at.forsyte.apalache.tla.bmcmt.rules.SetFilterRule.apply(SetFilterRule.scala:32)
```

## Root cause and expected behavior

`SetFilterRule` deliberately leaves this symbolic-set shape unimplemented but
signals it with `NotImplementedError`. That exception escapes the rewriting
pipeline and becomes an unhandled checker crash.

The other corpus instances report the same exception from `SetFilterRule` with
`PowSet` and `FinFunSet` domains. Small literal powersets and function sets may
be materialized before this rule, so the `InfSet` module above is the stable
standalone reproduction. The three arena signatures are missing branches of
the same rule, not independent issues.

Either implement filtering over the supported infinite-set representation or
reject the expression through Apalache's ordinary unsupported-input mechanism.
An unsupported construct must not escape as an implementation exception.
