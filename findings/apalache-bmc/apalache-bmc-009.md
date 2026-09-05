---
state: open
labels: [apalache]
---

# Function sets over infinite domains escape as `IllegalArgumentException`

## Summary

Apalache's `FunSetCtorRule` aborts with an unhandled
`IllegalArgumentException` when it constructs a function set whose domain is
`Int`. The internal `FinFunSetT` constructor requires what its diagnostic calls
the right-hand side to be a finite set or powerset, even though `[Int -> {0}]`
is a well-typed TLA+ set of functions.

The `corpus3` run contains six instances with this exception. Observed and
reproduced with Apalache 0.62.2, build `f0dec98`. See the representative
[`3a4ee860...` input](../../corpus3/02apa-crash/3a4ee860309811766ae469e92fc9548f7d1ccb76c9ebea5de234cf83374976c3.cbor)
and its [stacktrace](../../corpus3/02apa-crash/3a4ee860309811766ae469e92fc9548f7d1ccb76c9ebea5de234cf83374976c3.stacktrace).

## Reproduction

```tla
---- MODULE FunSetInfiniteDomain ----
EXTENDS Integers

VARIABLE
\* @type: Set((Int -> Int));
sets

Init == sets = [Int -> {0}]
Next == UNCHANGED sets
Inv == TRUE

====
```

Run:

```sh
java -Xmx1g -jar target/apalache.jar check \
  --init=Init --next=Next --inv=Inv --length=0 --no-deadlock \
  FunSetInfiniteDomain.tla
```

The module passes Snowcat and reaches the bounded checker, which terminates
with:

```text
java.lang.IllegalArgumentException: requirement failed:
The right-hand side of a function set should be: a finite set or a powerset
    at at.forsyte.apalache.tla.bmcmt.types.package$FinFunSetT.<init>(package.scala:167)
    at at.forsyte.apalache.tla.bmcmt.rules.FunSetCtorRule.apply(FunSetCtorRule.scala:31)
EXITCODE: ERROR (255)
```

## Expected behavior

If function sets over infinite domains are unsupported, `FunSetCtorRule` should
reject them through Apalache's classified unsupported-input path. An
implementation precondition must not escape as an unhandled exception.

## Impact

A well-typed function-set expression aborts model checking and is
indistinguishable from an implementation crash to automated consumers.
