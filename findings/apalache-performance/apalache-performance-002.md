---
state: open
labels: [apalache]
---

# Insertion sort by nested `ApaFoldSeqLeft` of nine integers exhausts a 1 GB heap

## Summary

Apalache runs out of heap memory in its bounded checker on a constant invariant
that sorts nine integers by insertion sort. The sort nests one `ApaFoldSeqLeft`
in another:

```tla
Inv == SeqInsertionSort(<<9, 8, 7, 6, 5, 4, 3, 2, 1>>) = <<1, 2, 3, 4, 5, 6, 7, 8, 9>>
```

With `-Xmx1g`, the check at `--length=0` fails with
`java.lang.OutOfMemoryError: Java heap space` after 67 seconds. Six elements
check in 2 seconds. Each further element roughly triples the time and the
memory. Sorting a state variable compounds the cost: `x' = SeqInsertionSort(x)`
from `x = <<1, 2, 3>>` exhausts the heap in the third step.

Observed with Apalache 0.62.2 (build `f0dec98`) and FuzzTLA `1fd3fe9`.

## Reproduction

`ISort9.tla`:

```tla
---- MODULE ISort9 ----
EXTENDS Integers, Sequences, Apalache
\* @type: (Int, Seq(Int)) => Seq(Int);
InsertSorted(x, s) ==
    LET \* @type: (<<Seq(Int), Bool>>, Int) => <<Seq(Int), Bool>>;
        Step(st, y) ==
            IF ~st[2] /\ x <= y THEN <<Append(Append(st[1], x), y), TRUE>> ELSE <<Append(st[1], y), st[2]>>
        st == ApaFoldSeqLeft(Step, <<<<>>, FALSE>>, s)
    IN IF st[2] THEN st[1] ELSE Append(st[1], x)
\* @type: Seq(Int) => Seq(Int);
SeqInsertionSort(s) == LET Insert(r, x) == InsertSorted(x, r) IN ApaFoldSeqLeft(Insert, <<>>, s)
VARIABLE
  \* @type: Seq(Int);
  x
Init == x = <<1, 2, 3>>
Next == UNCHANGED x
Inv == SeqInsertionSort(<<9, 8, 7, 6, 5, 4, 3, 2, 1>>) = <<1, 2, 3, 4, 5, 6, 7, 8, 9>>
====
```

```sh
java -Xmx1g -jar apalache.jar check --length=0 --inv=Inv ISort9.tla
```

```text
Ran out of heap memory (max JVM memory: 1073741824)
Ran out of heap memory: Java heap space
EXITCODE: ERROR (255)
```

All rows used a 1 GB heap. Peak resident set includes Z3.

| Input | Check | Result |
| --- | --- | --- |
| `Inv` sorts `<<5, ..., 1>>` | `--length=0` | passes in 1 s |
| `Inv` sorts `<<6, ..., 1>>` | `--length=0` | passes in 2 s |
| `Inv` sorts `<<7, ..., 1>>` | `--length=0` | passes in 7 s, 619 MB resident |
| `Inv` sorts `<<8, ..., 1>>` | `--length=0` | passes in 20 s, 1,567 MB resident |
| `Inv` sorts `<<9, ..., 1>>` | `--length=0` | out of memory after 67 s |
| `Next == x' = SeqInsertionSort(x)`, `x = <<1, 2, 3>>` | `--length=2` | passes in 5 s |
| `Next == x' = SeqInsertionSort(x)`, `x = <<1, 2, 3>>` | `--length=3` | out of memory after 59 s |

## Cause

Not diagnosed. The double `Append` in `Step` is not the cause. The following
formulation counts the smaller elements with one fold and splices `x` in with
`SubSeq` and `\o`:

```tla
InsertSorted(x, s) ==
    LET Count(c, y) == IF y < x THEN c + 1 ELSE c
        k == ApaFoldSeqLeft(Count, 0, s)
    IN SubSeq(s, 1, k) \o <<x>> \o SubSeq(s, k + 1, Len(s))
```

It also exhausts the heap in the third step of `x' = SeqInsertionSort(x)`, after
86 seconds, and it does not finish one sort of nine elements within 300 seconds.
Both formulations fold over a sequence that an enclosing fold computes.

## Corpus evidence

The recursion library's `RecursionApalache` module defines `SeqInsertionSort`
and `SetToSortedSeq` as above; `SetToSortedSeq` folds `InsertSorted` over a set
with `ApaFoldSet`. Of the 73 new Apalache crashes of corpus49, 69 are
`Terminating due to java.lang.OutOfMemoryError: Java heap space` in a worker with
a 1 GB heap and apply one of the two operators. The 4 other crashes apply
`SeqReverse` to a sequence that doubles in every step. The smallest input
is typical:

```tla
Init == var0 \in {<<1, 2, 3>>} /\ step = 0
Next == (step < 5 /\ var0' = Append(SeqInsertionSort(var0), 1) /\ step' = step + 1)
     \/ UNCHANGED <<var0, step>>
```

Rerun with `Inv == TRUE` and `Next == x' = Append(SeqInsertionSort(x), 1)`,
this runs out of heap memory at `--length=3` after 85 seconds. `--length=2` passes in
25 seconds. TLC completes all 73 inputs with the recursive definitions of the
paired `RecursionTLC` module: 50 pass, 7 report a counterexample, 15 fail with an
evaluation error, and 1 overflows the Java stack.

## Expected behavior

Sorting a sequence of nine integers checks within seconds and a few hundred
megabytes.

## Impact

Folds that build a sequence and pass it to another fold are the standard
replacement for recursive sequence operators in Apalache. Sorting is out of
reach beyond about eight elements, even when the input is a constant. The
failure is an out-of-memory exit, which a user may attribute to the state space
rather than to one operator.
