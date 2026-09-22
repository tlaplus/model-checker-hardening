---
state: open
labels: [apalache]
---

# Apalache's `ExistsSurjection(S, {})` is `TRUE` for a non-empty `S`

## Summary

Apalache replaces `Functions!ExistsSurjection` with
`Cardinality(S) >= Cardinality(T)` in `__rewire_functions_in_apalache.tla`. For a
non-empty `S` and an empty `T`, this is `TRUE`, but no function from `S` to
`{}` exists, so the Community Modules definition, `Surjection(S, T) # {}`, is
`FALSE`. For example, `ExistsSurjection({1, 2, 3}, {})` is `TRUE` in Apalache
and `FALSE` in TLC, which evaluates the Community Modules definition. The
rewired `ExistsInjection` (`<=`) and `ExistsBijection` (`=`) agree with their
definitions on finite sets.

Observed with Apalache 0.62.2 (build `f0dec98`), CommunityModules release
`202609120237` (commit `9aae8ea`), tla2tools 1.8.0-SNAPSHOT (Maven snapshot
`1.8.0-20260917.033119-76`, tlaplus/tlaplus commit `142d0ba`), and FuzzTLA
`91871fd`.

## Reproduction

`ExistsSurj.tla`:

```tla
---- MODULE ExistsSurj ----
EXTENDS Integers, Functions
VARIABLE
  \* @type: Int;
  x
Init == x = 0
Next == UNCHANGED x
Inv == ~ExistsSurjection({1, 2, 3}, {})
====
```

```sh
apalache-mc check --length=0 --inv=Inv ExistsSurj.tla
```

```text
State 0: state invariant 0 violated.
EXITCODE: ERROR (12)
```

TLC, with `CommunityModules.jar` on the class path and `INIT Init`,
`NEXT Next`, `INVARIANT Inv`, reports `No error has been found`.

## Cause

The Community Modules definition (`Functions.tla`, lines 120 and 134):

```tla
Surjection(S,T) == { M \in [S -> T] : \A t \in T : \E s \in S : M[s] = t }
ExistsSurjection(S,T) == Surjection(S,T) # {}
```

For `T = {}` and `S # {}`, `[S -> T]` is empty, so `ExistsSurjection` is
`FALSE`. For `S = T = {}`, the empty function is a surjection.

Apalache's definition (`src/tla/__rewire_functions_in_apalache.tla`, lines
124-125):

```tla
ExistsSurjection(__S, __T) ==
    Cardinality(__S) >= Cardinality(__T)
```

The cardinality test is correct only when `T` is non-empty or `S` is empty.
The fix is `Cardinality(__S) >= Cardinality(__T) /\ (__T = {} => __S = {})`.

## Corpus evidence

The `module` corpus44 run has three aggregator deviations with this cause.
In each, TLC reports a counterexample and Apalache passes:

- `5ae1c837` (generation 36): `Init == var0 = {}` and
  `Inv == ExistsSurjection({1, 2, 3}, var0)`. The initial state violates `Inv`.
- `531d2ddf` (generation 30): `Next` sets `var0' = {}`, and
  `Inv == ExistsSurjection({ {{"1"}}, {{"2"}}, {{"3"}} }, var0)`.
- `d6adcdd4` (generation 23): `Next` sets
  `var0' = SymDiff({TRUE, FALSE}, var0)`, which is `{}` from the initial
  `var0 = {TRUE, FALSE}`, and `Inv == ExistsSurjection({1, 2, 3}, var0)`.

TLC's verdict is correct in all three. None of them apply `ApaFoldSet` or
`SetToSeq`.

## Expected behavior

`ExistsSurjection(S, {})` is `FALSE` for every non-empty `S`, as the Community
Modules definition gives.

## Impact

A specification that asks whether a surjection onto an empty set exists gets
`TRUE` from Apalache. The empty set is a common value of a state variable at
initialization or after removals. Apalache can then miss an invariant
violation or enable an action that TLC disables, and neither tool reports an
error.
