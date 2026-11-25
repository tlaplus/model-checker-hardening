---
state: open
labels: [apalache]
---

# Integer division by a negative divisor follows SMT-LIB's Euclidean rule, not TLC's floor division

## Summary

Apalache encodes `a \div b` as SMT-LIB's integer `div`, which is Euclidean
division: the quotient is chosen so that the remainder satisfies
`0 <= r < |b|`. TLC evaluates `a \div b` by floor division, rounding the
quotient down. The two agree for `b > 0`. For a negative divisor they differ
whenever `b` does not divide `a`, so Apalache can verify an invariant that TLC
refutes, and the reverse.

| `a \div b` | TLC (floor) | Apalache (Euclidean) |
|---|---:|---:|
| `1 \div (-2)` | -1 | 0 |
| `7 \div (-2)` | -4 | -3 |
| `(-1) \div (-2)` | 0 | 1 |
| `(-7) \div (-2)` | 3 | 4 |

For a non-negative dividend the Euclidean quotient equals the quotient
truncated toward zero, which is how this finding first described it. For a
negative dividend it rounds away from zero instead.

The standard `Naturals` module defines `\div` and `%` only for `b > 0`, so TLA+
does not determine `a \div b` for a negative `b`. TLC picks floor division and
rejects `%` with a divisor that is not positive
([modulo-nonpositive-divisor](../../conformance/modulo-nonpositive-divisor.md)).
The difference is still a conformance failure between the tools.

The values above are those of a variable dividend and divisor. When both are
constants, Apalache's `ConstSimplifier` folds them by truncation instead
([apalache-optimizer-002](../apalache-optimizer/apalache-optimizer-002.md)),
which gives 3 for `(-7) \div (-2)`.

Observed with Apalache 0.62.2 (build `f0dec98`). The reproduction below and the
table were rechecked with Apalache 0.63.0 (`67b8ec69`), TLC `bc1b506` (tla2tools
`1.8.0-20261119.181232-127`) and FuzzTLA `fa5b6c03`.

## Reproduction

```tla
---- MODULE NegativeDivisor ----
EXTENDS Integers
VARIABLES
\* @type: Int;
x,
\* @type: Int;
step

Init == x = 0 /\ step = 0
Next == x' = x /\ step' = step + 1
Inv == step \div (-17201) = x
Bound == step <= 5
====
```

Run Apalache with:

```sh
java -Xmx1g -jar target/apalache.jar check \
  --init=Init --next=Next --inv=Inv --length=5 NegativeDivisor.tla
```

Apalache reports `NoError`: for `step` in `1..5`, the Euclidean quotient
`step \div (-17201)` is 0. TLC reaches `step = 1`, where `1 \div (-17201)` is
`-1`, and reports an invariant violation. The corpus reproducer is corpus6
entry `0a79bcf9`.

The table was measured with one Apalache run per pair. Each run's initial
state assigns `x = a`, `y = b` and `q = x \div y`, with `Inv == FALSE`, and the
counterexample trace gives `q`.

## Cause

`Z3SolverContext.scala:1037-1038` translates `TlaArithOper.div` with
`z3context.mkDiv`, and `Cvc5SolverContext.scala:544-545` with
`Kind.INTS_DIVISION` (Apalache `v0.63.0`). Both are SMT-LIB's integer `div`,
whose semantics is Euclidean.

## Expected behavior

Apalache should implement integer division by a negative divisor as TLC does,
with floor division, and report the invariant violation at the first successor
state. Alternatively, it should reject a negative divisor as outside the
definition in the standard module.

## Impact

Models that divide by a negative integer can receive a different verdict from
Apalache than from TLC, in either direction. FuzzTLA records this as a
TLC-counterexample/Apalache-pass conformance failure, or the reverse.
