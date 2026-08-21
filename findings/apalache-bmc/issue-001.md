# `SetFilterRule` throws `NotImplementedError` for an infinite set

## Summary

Apalache's bounded checker throws an unhandled `scala.NotImplementedError` when
it evaluates a set filter whose domain is `Int`. The tool exits with status 255
and asks the user to report a bug instead of returning a classified unsupported
or input-evaluation result.

Observed with Apalache 0.62.0.

## Reproduction

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

Either implement filtering over the supported infinite-set representation or
reject the expression through Apalache's ordinary unsupported-input mechanism.
An unsupported construct must not escape as an implementation exception.
