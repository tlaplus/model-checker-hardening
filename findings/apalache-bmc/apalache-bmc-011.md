---
state: open
labels: [apalache]
---

# `LazyEquality` asserts on a function set with an undefined domain value

## Summary

Apalache's `LazyEquality` throws `AssertionError` while comparing a function
set whose domain came from applying an empty function outside its domain. The
TLA+ expression is undefined, but the evaluator carries the resulting symbolic
set into `mkFunSetEq`, where `subsetEq` asserts instead of returning a
classified input-evaluation failure.

Observed in one `corpus3` input and reproduced with Apalache 0.62.2, build
`f0dec98`. See the [`bfe432e8...` input](../../corpus3/02apa-crash/bfe432e8c7c1da7467ba138b4227e1cc48ab86d080dac4abdc5bcb3baeb29e2a.cbor)
and its [stacktrace](../../corpus3/02apa-crash/bfe432e8c7c1da7467ba138b4227e1cc48ab86d080dac4abdc5bcb3baeb29e2a.stacktrace).

## Reproduction

```tla
---- MODULE LazyUndefinedSetEquality ----
EXTENDS Integers

VARIABLE
\* @type: Set((Str -> Int));
value

\* @type: (() => (Str -> Set(Str)));
EmptyFunction == [i \in {} |-> {}]

\* @type: (() => Set(Str));
BadDomain == EmptyFunction["missing"]

\* @type: (() => Set((Str -> Int)));
Rhs == [BadDomain -> Int]

Init == value = Rhs
Next == UNCHANGED value
Inv == value = Rhs

====
```

Run:

```sh
java -Xmx1g -jar target/apalache.jar check \
  --init=Init --next=Next --inv=Inv --length=0 --no-deadlock \
  LazyUndefinedSetEquality.tla
```

The bounded checker terminates with:

```text
java.lang.AssertionError: assertion failed
    at at.forsyte.apalache.tla.bmcmt.LazyEquality.subsetEq(LazyEquality.scala:302)
    at at.forsyte.apalache.tla.bmcmt.LazyEquality.mkSetEq(LazyEquality.scala:248)
    at at.forsyte.apalache.tla.bmcmt.LazyEquality.mkFunSetEq(LazyEquality.scala:274)
EXITCODE: ERROR (255)
```

## Expected behavior

Applying `EmptyFunction` to `"missing"` should produce a classified
input-evaluation error. No symbolic value produced after that error may reach an
unchecked invariant in `LazyEquality`.

## Impact

An undefined function application becomes an internal assertion failure whose
message loses the source-level cause and asks the user to report an Apalache
bug.
