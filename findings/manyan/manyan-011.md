---
state: closed
labels: [manyan]
---

# Manyan loses the next state when an operator argument is applied to a primed expression

## Summary

When a definition applies an operator argument to an expression that contains a primed
variable, Manyan evaluates that expression without the next state. In the next-state
relation and under `ENABLED`, it then fails:

```text
Error: In evaluation, the identifier x is either undefined or not an operator.
```

TLC checks these specifications. For `Ap(Op(_), v) == Op(v)` and `Id(a) == a`, Manyan
fails on `Next == Ap(Id, x' = 1 - x)` but checks `Next == Id(x' = 1 - x)`. Apalache's
`ApaFoldSet` and `ApaFoldSeqLeft` apply their operator argument to the accumulator, so
`ENABLED ApaFoldSet(F, x' = x, {1})` fails as well, while the same fold over `{}` does
not. An action property `[][Ap(Id, x' >= x)]_x` is evaluated correctly.

Observed with Manyan `v0.17.0` (soasme/manyan
`a9ca4f38028f4e8b4cc3f016167d071b55e3abe0`, 2026-11-24), built with GCC 13.3.0 by
`make manyan-build` on aarch64 Linux; corpus89-manyan ran an x86-64 build of the same
revision. The reference is TLC `bc1b506` (tlaplus/tlaplus
`bc1b506bdcad80bb518bf3776a9161c34ff0fc4c`) on OpenJDK 25.0.5.1. Apalache is not
involved. FuzzTLA `fa5b6c03`.

Fixed in [Manyan v0.18.0](https://github.com/soasme/manyan/releases/tag/v0.18.0), by
`188c9ad`.

## Reproduction

`OpArgPrime.tla`:

```tla
---- MODULE OpArgPrime ----
EXTENDS Naturals
VARIABLE x
Id(a) == a
Ap(Op(_), v) == Op(v)
Init == x = 0
Next == Ap(Id, x' = 1 - x)
Spec == Init /\ [][Next]_x
P == x \in {0, 1}
====
```

`OpArgPrime.cfg`:

```text
SPECIFICATION Spec
INVARIANT P
```

```sh
manyan -workers 1 -deadlock -config OpArgPrime.cfg OpArgPrime.tla
```

Manyan exits 75:

```text
Error: TLC threw an unexpected exception.
This was probably caused by an error in the spec or model.
See the User Output or TLC Console for clues to what happened.
The exception was a java.lang.RuntimeException
: In evaluation, the identifier x is either undefined or not an operator.
line 7, col 16 to line 7, col 16 of module OpArgPrime
Error: The behavior up to this point is:
State 1: <Initial predicate>
x = 0
```

Column 16 is the `x` of `x'`.
`java -cp tla2tools.jar tlc2.TLC -workers 1 -deadlock -config OpArgPrime.cfg OpArgPrime`
exits 0, with `Model checking completed. No error has been found.` With `P == x \in {0}`,
TLC reports the violation in the second state and Manyan still fails as above.

### Variants

The rows change `Next` or the invariant `P` of the module above. The `ENABLED` rows use
`Next == UNCHANGED x`, `F(acc, e) == acc`, and `EXTENDS Naturals, Apalache`.

| Definition | TLC | Manyan |
| --- | --- | --- |
| `Next == Ap(Id, x' = 1 - x)` | 0 | 75, identifier undefined |
| `Next == Id(x' = 1 - x)` | 0 | 0 |
| `P == ENABLED Ap(Id, x' = x)` | 0 | 75, identifier undefined |
| `P == ENABLED Id(x' = x)` | 0 | 0 |
| `P == ENABLED ApaFoldSet(F, x' = x, {1})` | 0 | 75, identifier undefined |
| `P == ENABLED ApaFoldSeqLeft(F, x' = x, <<1>>)` | 0 | 75, identifier undefined |
| `P == ENABLED ApaFoldSet(F, x' = x, {})` | 0 | 0 |
| `PROPERTY P` with `P == [][Ap(Id, x' >= x)]_x` and `Next == x' = 1 - x` | 13 | 13 |

A user-defined recursion behaves like the fold when it passes its operator argument on:
`K(Op(_,_), v, n) == IF n = 0 THEN v ELSE K(Op, Op(v, n), n - 1)` fails for
`ENABLED K(F, x' = x, 1)`, and checks `ENABLED K(F, x' = x, 0)`.

## Expected behavior

An expression passed through an operator argument should be evaluated in the context of
the call, with the next state that the next-state relation or `ENABLED` provides.

## Impact

Higher-order operators that take actions, such as a fold over a set of processes that
conjoins their steps, are common in specifications, so the defect is not specific to
generated inputs. In corpus89-manyan, a 250,000-entry pbt campaign, one entry shows it:
in `b7a06b95`, the invariant is
`ENABLED ApaFoldSet(Lambda6, (var0' = var0), {"default_OF_MODEL"})` with
`Lambda6(p4, p5) == p4`. Manyan exits 75 on the initial state, and TLC reports a
violation of the property. The generator rarely passes an action to an operator argument,
so the campaign does not measure how often the defect occurs in practice.

## Resolution

Manyan fixed this in
[`188c9ad`](https://github.com/soasme/manyan/commit/188c9adfa47f47fdba72880cc1629baf1dff8fe3),
"Fix EXCEPT @ scoping, lazy empty sets/V_LAMBDA, veq/filter/arg_pure, and generator
HO/LET/fairness", released in
[v0.18.0](https://github.com/soasme/manyan/releases/tag/v0.18.0) (soasme/manyan
`5522fa6b11b9e35ae60d46a31d0d33ce77df8ff9`, 2026-11-26). It is the first commit between
`v0.17.0` and `v0.18.0` that passes the reproduction; each commit was built with `make
manyan PGO_TOOLS=`.

Rechecked on 2026-11-26 with `v0.18.0`, built by `make manyan-build`, against the exit
statuses that TLC gave above: Manyan exits 0 on the reproduction and on the four
`ENABLED` rows that failed, including `ENABLED K(F, x' = x, 1)`, and with `P == x \in
{0}` it reports the violation (exit 12), as TLC does. FuzzTLA `dc773e71`.
