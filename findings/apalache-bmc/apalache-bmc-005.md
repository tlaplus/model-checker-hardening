---
state: open
---

# Quantification over `Int` escapes as `UnsupportedOperationException`

## Summary

When a bounded quantifier over `Int` occurs below negation, Apalache's
`QuantRule` attempts to expand the infinite arena set and throws an unhandled
`UnsupportedOperationException`.

Infinite-domain quantification is a known Apalache limitation, but the
exception is a checker crash rather than a classified unsupported-input result.
Observed with Apalache 0.62.2, build `f0dec98`. The later `corpus2` run found
five additional instances. The corpus evidence is the
[`8559bfa7...` input](../../corpus4/02apa-crash/8559bfa7129fccc8340501c41def25dbb2e15f0910a9e5d332479c785c0630a8.cbor)
and its [stacktrace](../../corpus4/02apa-crash/8559bfa7129fccc8340501c41def25dbb2e15f0910a9e5d332479c785c0630a8.stacktrace).

## Reproduction

```tla
---- MODULE NegatedQuantifierInt ----
EXTENDS Integers

VARIABLE
\* @type: Bool;
result

Init == result = ~(\E i \in Int : i = 0)
Next == UNCHANGED result
Inv == TRUE

====
```

Apalache emits a warning about expanding a complex set and then terminates:

```text
java.lang.UnsupportedOperationException:
Expansion of InfSet[CellTFrom(Int)] is not supported yet
    at at.forsyte.apalache.tla.bmcmt.rules.QuantRule.expandExistsOrForall(...)
```

## Expected behavior and impact

If infinite-domain quantification remains unsupported, `QuantRule` should
return Apalache's ordinary unsupported-input diagnostic. A deliberate
capability boundary must not escape as an implementation exception or ask the
user to report an unknown bug.

This is the crash-level form of the limitation recorded in
[`quantification-over-infinite-set.md`](../../conformance/quantification-over-infinite-set.md).
