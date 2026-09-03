---
state: open
---

# Empty-domain function sets violate reflexive equality

## Summary

Apalache reports an invariant violation when a state variable is initialized
to the function set `[{} -> {}]` and the invariant compares it with the same
expression. TLC finds no violation. Equality with an identical value must be
reflexive, so the counterexample is unsound.

Observed twice in the inspected corpus with Apalache 0.62.0.

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
