---
state: open
labels: [apalache]
---

# Reversing 240 integers by `\o` in `ApaFoldSeqLeft` exhausts a 1 GB heap

## Summary

Apalache runs out of heap memory in its bounded checker on a constant invariant
that reverses a sequence of 240 integers with one `ApaFoldSeqLeft`. The
combinator prepends each element with `\o`:

```tla
SeqReverse(s) == LET Prepend(r, x) == <<x>> \o r IN ApaFoldSeqLeft(Prepend, <<>>, s)
Inv == Len(SeqReverse(MkSeq(240, LAMBDA i: i + 0))) = 240
```

With `-Xmx1g`, the check at `--length=0` fails with
`java.lang.OutOfMemoryError: Java heap space` after 75 seconds. The same fold
with `Append(r, x)` checks in 9 seconds. Unlike
[apalache-performance-002](apalache-performance-002.md), no fold is nested in
another; `\o` in the combinator is enough.

Observed with Apalache 0.62.2 (build `f0dec98`), FuzzTLA `703c9fa` and TLC commit
`8f4bc8b` (tla2tools `1.8.0-20260925.164314-82`).

## Reproduction

`Reverse.tla`:

```tla
---- MODULE Reverse ----
EXTENDS Integers, Sequences, Apalache
\* @type: Seq(Int);
Empty == <<>>
\* @type: (Seq(Int)) => Seq(Int);
SeqReverse(s) == LET Prepend(r, x) == <<x>> \o r IN ApaFoldSeqLeft(Prepend, Empty, s)
VARIABLE
  \* @type: Int;
  x
Init == x = 0
Next == UNCHANGED x
Inv == Len(SeqReverse(MkSeq(240, LAMBDA i: i + 0))) = 240
====
```

```sh
java -Xmx1g -jar apalache.jar check --init=Init --next=Next --inv=Inv \
  --length=0 --no-deadlock Reverse.tla
```

```text
Ran out of heap memory (max JVM memory: 1073741824)
Ran out of heap memory: Java heap space
EXITCODE: ERROR (255)
```

All rows used a 1 GB heap and `--length=0`, and vary the combinator and the
sequence length `n`. Resident set sizes include Z3.

| Combinator | n = 60 | n = 120 | n = 240 |
| --- | --- | --- | --- |
| `Append(r, x)` | not measured | 3 s, 441 MB | 9 s, 896 MB |
| `r \o <<x>>` | 2 s, 381 MB | 6 s, 896 MB | 43 s, 3,254 MB |
| `<<x>> \o r` | 4 s, 489 MB | 24 s, 1,939 MB | out of memory after 75 s |

The three combinators compute sequences of the same length. `\o` costs more than
`Append` in either operand order, and far more when the accumulator is its right
operand.

## Cause

Not diagnosed.

## Corpus evidence

The recursion library's `RecursionApalache` module defines `SeqReverse` as above
and `SeqFlatten` with the combinator `r \o t`. corpus53 has 58 Apalache crashes
that the triager leaves new, all `Terminating due to java.lang.OutOfMemoryError:
Java heap space` in a worker with a 1 GB heap. 57 apply `SeqReverse` to a
sequence that doubles in every step. The smallest, `ee6bc33a`, is typical:

```tla
Init == var0 = Append(<<{1}, {2}, {3}>>, {1, 2, 3}) /\ step = 0
Next == (step < 5 /\ var0' = SeqReverse(var0 \o var0) /\ step' = step + 1)
     \/ UNCHANGED <<var0, step>>
```

The fold reaches 128 elements in the fifth step. Rerun on the entry's typed IR,
Apalache passes at `--length=4` in 107 seconds and runs out of heap memory at
`--length=5` after 189 seconds. The remaining crash, `4a84fcd9`, has a generated
`ApaFoldSet` combinator that concatenates its accumulator with a sequence
variable. With the recursive definitions of the paired `RecursionTLC` module, TLC passes
49 of the 58 and reports an evaluation error on 7, one of them with the exit
status of [tlc-002](../TLC/tlc-002.md). The other 2 overflow the Java stack
([tlc-performance-001](../tlc-performance/tlc-performance-001.md)).

## Expected behavior

Reversing a sequence of a few hundred integers checks within seconds and a few
hundred megabytes, as the same fold with `Append` does.

## Impact

A fold whose combinator uses `\o` is the natural way to reverse, flatten, or
splice sequences in Apalache. Its cost depends on the operand order, which a user
has no reason to expect. The failure is an out-of-memory exit, which a user may
attribute to the state space rather than to one operator.
