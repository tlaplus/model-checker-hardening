---
state: closed
labels: [apalache]
---

# Empty-domain function sets violate reflexive equality

## Summary

Apalache reports an invariant violation when a state variable is initialized
to the function set `[{} -> {}]` and the invariant compares it with the same
expression. TLC finds no violation. Equality with an identical value must be
reflexive, so the counterexample is unsound.

Observed twice in the inspected corpus with Apalache 0.62.0 and reproduced with
Apalache 0.62.2, build `f0dec98`.

Fixed upstream in [Apalache
0.62.3](https://github.com/apalache-mc/apalache/releases/tag/v0.62.3) by [PR
#3478](https://github.com/apalache-mc/apalache/pull/3478). Verified with
Apalache 0.62.3 (commit `3eb15b2`), TLC `341472c` (tla2tools
`1.8.0-20260930.140537-84`) and FuzzTLA `b7caaf2`: Apalache proves `Inv` of the
reproduction below.

## Reproduction

```tla
---- MODULE EmptyFunctionSet ----

VARIABLE
\* @type: Set((Str -> Bool));
fs

Init == fs = [{} -> {}]
Next == UNCHANGED fs
Inv == fs = [{} -> {}]

====
```

Run both checkers at length zero. TLC completes without an invariant violation.
Apalache exits with status 12 and reports that `Inv` is violated in state 0.

## Expected behavior and impact

`[{} -> {}]` contains the single empty function. The initialized value and the
invariant expression denote the same singleton set, irrespective of the empty
element and result types supplied by the annotation. Apalache must prove `Inv`.

This is a bounded-checker soundness defect: a valid invariant is rejected by a
spurious counterexample. The computed-empty-domain variant in the same corpus
does not fail, which points to a representation-specific encoding error for a
literal empty domain.
