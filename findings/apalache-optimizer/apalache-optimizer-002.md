---
state: open
labels: [apalache]
---

# `ConstSimplifier` folds `\div` and `%` of a negative constant by truncation

## Summary

Apalache's constant simplifier evaluates `a \div b` and `a % b` for integer
literals `a` and `b` with Scala's `BigInt` operators `/` and `%`. They truncate
the quotient toward zero, and the remainder takes the sign of the dividend.
TLA+ defines both operators for `b > 0` so that `0 <= a % b < b`, which rounds
the quotient down. TLC computes the same values, and so does Apalache's SMT
encoding, which uses SMT-LIB's `div` and `mod`. The values differ whenever `a`
is negative and `b` does not divide it:

| Expression | TLA+, TLC, Apalache's SMT encoding | Apalache's constant simplifier |
|---|---:|---:|
| `(-1) \div 2` | -1 | 0 |
| `(-7) \div 2` | -4 | -3 |
| `(-1) % 2` | 1 | -1 |
| `(-7) % 3` | 2 | -1 |

Apalache therefore gives an expression two values, depending on whether its
operands are constant when the simplifier runs. With `x = -7`, the formula
`x \div 2 = (-7) \div 2` is false in Apalache. An invariant that holds can be
reported as violated, and one that is violated can pass.

Observed with Apalache 0.63.0 (`67b8ec69`), FuzzTLA `fa5b6c03` and TLC
`bc1b506` (tla2tools `1.8.0-20261119.181232-127`), on OpenJDK 25.0.5.1 for Linux
aarch64. The folding code is unchanged on Apalache `main` at `a2910d423`.

## Reproduction

`NegativeDividend.tla`:

```tla
---- MODULE NegativeDividend ----
EXTENDS Integers
VARIABLE
  \* @type: Int;
  x
Init == x = -7
Next == UNCHANGED x
Inv == x \div 2 = (-7) \div 2
====
```

```sh
java -jar apalache.jar check --length=0 --inv=Inv NegativeDividend.tla
```

```text
State 0: state invariant 0 violated.
The outcome is: Error
EXITCODE: ERROR (12)
```

TLC, with `INIT Init`, `NEXT Next` and `INVARIANT Inv`, reports no error. Each
row below is a separate run of Apalache and TLC with the given `Init` and
`Inv`, and `--length=0` for Apalache:

| `Init` | `Inv` | Apalache 0.63.0 | TLC `bc1b506` |
|---|---|---|---|
| `x = 0` | `(-1) \div 2 = -1` | violated | holds |
| `x = 0` | `(-3) \div 2 = -2` | violated | holds |
| `x = 0` | `(-1) % 2 = 1` | violated | holds |
| `x = -1` | `x \div 2 = -1` | holds | holds |
| `x = -1` | `x % 2 = 1` | holds | holds |
| `x = 0` | `1 \div 2 = 0`, `7 % 3 = 1` | holds | holds |

A grid over `a` in `{-7, -3, -1, 0, 1, 3, 7}` and `b` in `{-2, -1, 1, 2, 3}`
computes each of `a \div b` and `a % b` twice in one initial state: from the
literals, and from variables `x = a` and `y = b`. Apalache's counterexample
trace shows the two results differ in exactly the 8 pairs where `a` is negative
and `b` does not divide it, for divisors of either sign.

## Cause

`tla-pp/src/main/scala/at/forsyte/apalache/tla/pp/ConstSimplifierBase.scala` at
`v0.63.0`:

```scala
// line 112
case OperEx(TlaArithOper.div, ValEx(TlaInt(left)), ValEx(TlaInt(right))) => ValEx(TlaInt(left / right))(intTag)
// line 124
case OperEx(TlaArithOper.mod, ValEx(TlaInt(left)), ValEx(TlaInt(right))) => ValEx(TlaInt(left % right))(intTag)
```

`left` and `right` are `BigInt`s, whose `/` and `%` follow
`java.math.BigInteger.divide` and `remainder`, which truncate. Both lines date
from Apalache `909db06a3` (2021-12-23). The SMT encoding
(`Z3SolverContext.scala:1037-1043`, `Cvc5SolverContext.scala:544-548`) maps
`\div` and `%` to SMT-LIB's `div` and `mod`. Those use Euclidean division:
`0 <= a mod b < |b|`. For `b > 0` this coincides with TLA+.

For a negative divisor, the standard module leaves both operators unspecified.
The simplifier and the SMT encoding still disagree there when `a` is negative,
for example `(-7) \div (-2)`, which the simplifier folds to 3 and the SMT
encoding evaluates to 4.
[`apalache-bmc-008`](../apalache-bmc/apalache-bmc-008.md) records how the SMT
encoding differs from TLC for negative divisors.

The simplifier also folds operands that become literals only through earlier
simplification, such as a record field or a negated constant.

## Corpus evidence

corpus88 is an `mt` corpus with the checkers TLC `7e64fd0` and Apalache 0.63.0.
Its aggregator has four entries in which Apalache reports a counterexample of
the relation invariant and TLC passes: `84f5c1ea`, `abac4606`, `bd7f672e` and
`45821555`. Each divides a negated record field by 2. In `84f5c1ea`, the rewrite
of the initial predicate checks the identity
`n = 2 * (n \div 2) + (IF n % 2 = 0 THEN 0 ELSE 1)` for the constant
`n == -([field2 |-> 1]["field2"])`, which is -1. Folded by truncation, the
right side is 1, so the rewritten predicate is false in the initial state,
and `Inv` is violated there.

With the rewrite of `84f5c1ea` changed only so that `n` is the variable `var0`,
whose initial value is the same -1, Apalache reports `NoError` up to length 5.
The run uses `--init=Init --next=Next --inv=Inv --length=5`. In the variant
types of `InvE` and `InvC`, `<<var0>>` and `<<step>>` were replaced with `var0`
and `step`, so that the rendered module type-checks without FuzzTLA's typed
IR. The unchanged entry is violated in state 0.

## Expected behavior

The simplifier folds `\div` and `%` as TLA+ defines them, rounding the quotient
down, for example with `BigInt`'s `/%` corrected toward negative infinity. For a
divisor that is not positive, it either matches the SMT encoding or leaves the
expression to the solver, so that an expression has one value whatever the
form of its operands.

## Impact

Any specification that divides a negative constant gets a wrong value in
Apalache. Examples are a constant offset, a parameter instantiated with a
negative number, or a bound such as `(-n) \div 2`. Invariants over such values
can be reported violated although they hold, or pass although they are
violated. Rewriting an expression into an equivalent form can change
Apalache's verdict, which makes the defect hard to diagnose.
