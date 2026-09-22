---
state: open
labels: [apalache]
---

# Apalache's `IndexFirstSubSeq` returns 0 for the empty needle

## Summary

Apalache replaces `SequencesExt!IndexFirstSubSeq` with its own definition in
`__rewire_sequences_ext_in_apalache.tla`. For the empty needle it returns 0,
while the Community Modules definition returns 1:
`IndexFirstSubSeq(<<>>, <<1, 2>>)` is 0 in Apalache and 1 in TLC, which
evaluates the Community Modules definition (the operator has no Java
override). Non-empty needles agree, for example `IndexFirstSubSeq(<<2>>, <<1, 2>>) = 2`,
`IndexFirstSubSeq(<<1, 2>>, <<1, 2, 1, 2>>) = 1` and
`IndexFirstSubSeq(<<1>>, <<1, 1, 1>>) = 1` in both tools.

Observed with Apalache 0.62.2 (build `f0dec98`), CommunityModules release
`202609120237` (commit `9aae8ea`), tla2tools 1.8.0-SNAPSHOT (Maven snapshot
`1.8.0-20260917.033119-76`, tlaplus/tlaplus commit `142d0ba`), and FuzzTLA
`a13a4d1`.

## Reproduction

`Ifs.tla`:

```tla
---- MODULE Ifs ----
EXTENDS Integers, Sequences, SequencesExt
VARIABLE
  \* @type: Int;
  x
Init == x = 0
Next == UNCHANGED x
Inv == IndexFirstSubSeq(<<>>, <<1, 2>>) = 1
====
```

```sh
apalache-mc check --length=0 --inv=Inv Ifs.tla
```

Apalache reports that `Inv` is violated in the initial state and exits 12.
With `Inv == IndexFirstSubSeq(<<>>, <<1, 2>>) = 0` it exits 0. TLC, with
`CommunityModules.jar` on the class path and `INIT Init`, `NEXT Next`,
`INVARIANT Inv`, checks the first invariant and exits 0.

## Cause

The Community Modules definition (`SequencesExt.tla`, lines 484-488) finds the
shortest prefix of `t` that contains `s` and subtracts `Len(s) - 1` from its
length:

```tla
IndexFirstSubSeq(s, t) ==
  LET last == CHOOSE i \in 0..Len(t) :
                /\ s \in SubSeqs(SubSeq(t, 1, i))
                /\ \A j \in 0..i-1 : s \notin SubSeqs(SubSeq(t, 1, j))
  IN last - (Len(s) - 1)
```

For `s = <<>>`, `last` is 0 and the result is `0 - (0 - 1) = 1`.

Apalache's definition (lines 418-429 of the rewiring module) searches for the
start index directly and admits 0:

```tla
LET __dom0 == {0} \union DOMAIN __haystack IN
CHOOSE __i \in __dom0:
  /\ __needle = SubSeq(__haystack, __i, __i + __needle_len - 1)
  /\ \A __j \in __dom0: __j < __i => ~__is_subseq(__j)
```

For the empty needle, `SubSeq(t, 0, -1) = <<>>`, so `__i = 0` is the least
witness. For a non-empty needle, index 0 does not match and the two
definitions agree.

## Corpus evidence

The `module` corpus43 run has two aggregator deviations with this cause. Both
apply `IndexFirstSubSeq` to `<<>>`:

- `0081cf2a`: `Prop == IndexFirstSubSeq(<<>>, Last(...)) <= step`. TLC
  reports that the initial state (`step = 0`) violates `Prop`; Apalache passes.
- `e06e2dbe`: `Inv == Contains(<<1, 2, 3>>, IndexFirstSubSeq(<<>>, SetToSeq(...)))`.
  TLC passes; Apalache reports a counterexample.

Neither verdict changes when TLC's `SetToSeq` result or `ApaFoldSet` order is
reversed. `0081cf2a` is a PBT input of generation 17. `e06e2dbe` is a mutant of
generation 33, three `random_byte` edits away from `ebc0c3ca`, an agreeing entry
kept in `04quality-pass`.

## Expected behavior

Apalache's definition returns the Community Modules value, 1, for the empty
needle, for example by restricting `__dom0` to `DOMAIN __haystack` when the
needle is non-empty and returning 1 otherwise.

## Impact

A specification that locates an empty subsequence, directly or through a value
computed at run time, gets different results from TLC and Apalache. Neither
tool reports an error, so the difference shows up only as a different verdict.
