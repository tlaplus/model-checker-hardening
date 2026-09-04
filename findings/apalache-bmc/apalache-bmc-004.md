---
state: open
labels: [apalache]
---

# Symbolic exponentiation crashes while casting a Z3 real to an integer

## Summary

Apalache's bounded checker can represent an integer-typed power expression as a
Z3 `RealExpr`. When the expression is subsequently consumed by integer
arithmetic inside a record, `Z3SolverContext` casts it to `IntExpr` and throws
`ClassCastException`.

Observed with Apalache 0.62.2, build `f0dec98`. The later `corpus2` run found
one additional instance, and the `corpus1` run, the first to generate whole
modules, found three more
([`5c72450e...`](../../corpus1/02apa-crash/5c72450ecff8b92fa3fe8714c66936b12ff5cfc189e010c22db919620bc6cc05.stacktrace),
[`b5e62cf1...`](../../corpus1/02apa-crash/b5e62cf1669471b599f86339504f66c4f9149558eb7939a9d49ac7933a8bddc3.stacktrace),
[`e7413240...`](../../corpus1/02apa-crash/e741324025b855c12cd5435e489083d4e8fefa53f45c538021f68ae7c43dc3bb.stacktrace)),
each applying `^` and entering `Z3SolverContext.toArithExpr` on the path this
finding describes. The `corpus4` input reproduces deterministically; see
the
[`4144072b...` input](../../corpus4/02apa-crash/4144072b74b01543c182591aa03ea67ab37166c14d694623ebe023de5d41ca61.cbor)
and its [stacktrace](../../corpus4/02apa-crash/4144072b74b01543c182591aa03ea67ab37166c14d694623ebe023de5d41ca61.stacktrace).

## Reproduction

```tla
---- MODULE SymbolicExponentRecord ----
EXTENDS Integers

VARIABLE
\* @type: { field: Int };
result

Init == result = CHOOSE r \in {
  [field |-> 0 - 103 ^ (CHOOSE i \in {0, 1} : TRUE)]
} : FALSE
Next == UNCHANGED result
Inv == TRUE

====
```

Checking the invariant at length zero reaches `BoundedChecker` and terminates
with:

```text
java.lang.ClassCastException: class com.microsoft.z3.RealExpr cannot be cast to
class com.microsoft.z3.IntExpr
    at at.forsyte.apalache.tla.bmcmt.smt.Z3SolverContext.toArithExpr(...)
    at at.forsyte.apalache.tla.bmcmt.rules.IntArithRule.rewritePacked(...)
```

The original corpus trace reaches the same path through `RecCtorRule`,
`SetCtorRule`, and `SetFilterRule`.

## Expected behavior and impact

An expression tagged `Int` must remain integer-sorted in the SMT encoding.
Apalache should encode supported integer exponentiation without introducing a
real-sorted term, or reject symbolic exponents through its classified
unsupported-input path. It must not rely on a runtime cast between Z3 sorts.

The defect affects well-typed records and other compound values containing
symbolic exponentiation and aborts checking before any invariant result.
