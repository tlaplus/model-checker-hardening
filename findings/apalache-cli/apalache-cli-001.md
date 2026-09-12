---
state: open
labels: [apalache]
---

# Diagnosed errors exit with generic status 255

## Summary

Apalache reaches a definite diagnosis, prints it, and then exits with the
generic error status 255. FuzzTLA therefore stores the result as a crash
instead of a classified failure, and cannot tell these runs apart from an
unhandled exception or a resource failure.

Observed with Apalache 0.62.2, build `f0dec98`. The following diagnoses are
known to reach this status:

| Diagnosis | Kind | Earlier instances | corpus14 |
| --- | --- | --- | ---: |
| `Cardinality expected a finite set` | input error | `corpus3`: 7; `corpus4`: 1 | 4 |
| `Expected a constant integer range in [ .. ]` | input error, upstream-known | `corpus1`: 2 | 97 |
| `Found a set map over an infinite set` | input error | `corpus3`: 142 | 12 |
| `FoldSet is not supported over an infinite set` | known limitation | `corpus3`: 27 | 11 |
| Negative or implementation-sized exponentiation | input error | `corpus3`: 29 | 23 |
| `Accessing a non-existing variant option` | rewriter diagnosis | `corpus3`: 10 | 0 |
| `Range bounds are too large to fit in scala.Int` | rewriter diagnosis | `corpus3`: 2 | 0 |
| `error when rewriting to SMT: z3 reports UNKNOWN` | solver capability | `corpus1`: 3 | 50 |
| `rewriter error: Trying to expand a set of functions` | rewriter refusal | `corpus1`: 1 | 7 |

The first is reproduced below. See also the
[`2d51b926...` input](../../corpus4/02apa-crash/2d51b9265c46d4427ec4a3900abcd820cea1df91d284d032c3b9d2577362906a.cbor)
and its [diagnostic](../../corpus4/02apa-crash/2d51b9265c46d4427ec4a3900abcd820cea1df91d284d032c3b9d2577362906a.stacktrace),
and for three `corpus1` diagnoses
[`714f9c96...`](../../corpus1/02apa-crash/714f9c966cd2e4802ecb29b0792e1e96ece5e975706f93e6b87d32fab0de0a37.stacktrace),
[`6439457b...`](../../corpus1/02apa-crash/6439457b665ead77b6a3406369c7174f094f6bc4dc53a4ea69f41f3167ed9561.stacktrace) and
[`8aed066e...`](../../corpus1/02apa-crash/8aed066e4d4833e9fdabbac946e898e2abcdf7a10a76a12cfcb3d5fbb19be6d8.stacktrace).

The `corpus14` run adds 204 instances, and its distribution differs sharply
from the earlier sessions: the constant-integer-range and `z3 reports UNKNOWN`
rows dominate it, while the two rewriter-diagnosis rows produced nothing. The
grouping is by exit status, so the mix is a property of the generator settings
rather than of this defect.

The rewriter and solver rows are grouped here because they share this exit
status, not because they share a cause. `Trying to expand a set of functions`
prints *"Please report an issue"*, so it may warrant its own finding once the
underlying rewriter behavior is understood.

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
75, consistently with other classified input errors. Every listed diagnosis is
conclusive enough to use an input, unsupported-feature, or solver status rather
than the generic status.

Status 255 conflates expected negative specifications and capability limits
with checker crashes. Consumers must otherwise grow an operation-specific
allowlist, which is incomplete by construction.
