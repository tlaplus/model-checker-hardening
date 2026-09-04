---
state: open
labels: [apalache]
---

# `Cardinality(Nat)` input error exits with generic status 255

## Summary

Apalache correctly diagnoses `Cardinality(Nat)` as applying `Cardinality` to a
non-finite set, but exits with generic error status 255. FuzzTLA therefore
stores the result as a crash instead of a specification-evaluation failure.

Observed with Apalache 0.62.2, build `f0dec98`.
See the [`2d51b926...` input](../../corpus4/02apa-crash/2d51b9265c46d4427ec4a3900abcd820cea1df91d284d032c3b9d2577362906a.cbor)
and its [diagnostic](../../corpus4/02apa-crash/2d51b9265c46d4427ec4a3900abcd820cea1df91d284d032c3b9d2577362906a.stacktrace).

## Reproduction

```tla
---- MODULE CardinalityNat ----
EXTENDS Naturals, FiniteSets

VARIABLE
\* @type: Int;
result

Init == result = Cardinality(Nat)
Next == UNCHANGED result
Inv == TRUE

====
```

Apalache reaches `BoundedChecker` and reports:

```text
Input error (see the manual): Cardinality expected a finite set, found:
InfSet[CellTFrom(Int)]
EXITCODE: ERROR (255)
```

The result contains no unhandled exception or resource failure.

## Expected behavior and impact

`Cardinality` is defined only for finite sets. Applying it to `Nat` is an input
evaluation error and should use Apalache's specification-evaluation exit status
75, consistently with other classified input errors.

The generic status conflates an expected negative specification with a checker
crash. Consumers must otherwise grow an operation-specific status-255
allowlist, which is incomplete by construction.
