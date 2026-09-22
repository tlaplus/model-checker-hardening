---
state: open
labels: [apalache]
---

# `LongestCommonPrefix(SubSeqs(s))` of a three-element sequence exhausts a 1 GB heap

## Summary

Apalache runs out of heap memory in its bounded checker on a constant
invariant over a sequence of three integers:

```tla
Inv == Len(LongestCommonPrefix(SubSeqs(<<1, 2, 3>>))) >= 0
```

With `-Xmx1g`, the check at `--length=0` fails with
`java.lang.OutOfMemoryError: Java heap space` after 116 seconds. With `-Xmx4g`
it passes after 202 seconds, 193 of them spent on the one state invariant, at a
peak resident set of 12 GB (JVM and Z3). With `<<1, 2>>` it passes in 23
seconds. The parts are cheap on their own: `SubSeqs(<<1, 2, 3>>)`,
`CommonPrefixes(SubSeqs(<<1, 2, 3>>))` and `LongestCommonPrefix` of a literal
set of four sequences each check in 5-8 seconds. The expected value is `<<>>`,
because `SubSeqs` contains `<<>>`.

Observed with Apalache 0.62.2 (build `f0dec98`), FuzzTLA `a13a4d1`,
CommunityModules release `202609120237` (commit `9aae8ea`), and tla2tools
1.8.0-SNAPSHOT (tlaplus/tlaplus commit `142d0ba`), which evaluates the
Community Modules definitions and is not involved.

## Reproduction

`Lcp3.tla`:

```tla
---- MODULE Lcp3 ----
EXTENDS Integers, Sequences, SequencesExt
VARIABLE
  \* @type: Int;
  x
Init == x = 0
Next == UNCHANGED x
Inv == Len(LongestCommonPrefix(SubSeqs(<<1, 2, 3>>))) >= 0
====
```

```sh
java -Xmx1g -jar apalache.jar check --length=0 --inv=Inv Lcp3.tla
```

```text
PASS #13: BoundedChecker
Ran out of heap memory (max JVM memory: 1073741824)
Ran out of heap memory: Java heap space
Total time: 116.380 sec
EXITCODE: ERROR (255)
```

| `Inv` | Heap | Result |
| --- | --- | --- |
| `Len(LongestCommonPrefix(SubSeqs(<<1, 2, 3>>))) >= 0` | 1 GB | out of memory after 116 s |
| `Len(LongestCommonPrefix(SubSeqs(<<1, 2, 3>>))) >= 0` | 4 GB | passes in 202 s |
| `Len(LongestCommonPrefix(SubSeqs(<<1, 2>>))) >= 0` | 1 GB | passes in 23 s |
| `Cardinality(SubSeqs(<<1, 2, 3>>)) >= 0` | 1 GB | passes in 5 s |
| `CommonPrefixes(SubSeqs(<<1, 2, 3>>)) /= {}` | 1 GB | passes in 8 s |
| `Len(LongestCommonPrefix({<<1, 2, 3>>, <<1, 2>>, <<1>>, <<2, 3>>})) >= 0` | 1 GB | passes in 6 s |

## Cause

Not diagnosed. Apalache's rewired definitions are, with the `__` prefix of their
names dropped,

```tla
SubSeqs(s) == { SubSeq(s, i + 1, j): i, j \in {0} \union (DOMAIN s) }
Prefixes(s) == { SubSeq(s, 1, l): l \in { 0 } \union DOMAIN s }
CommonPrefixes(S) ==
  LET P == UNION { Prefixes(seq) : seq \in S }
  IN { prefix \in P: \A t \in S: IsPrefix(prefix, t) }
LongestCommonPrefix(S) ==
  CHOOSE longest \in CommonPrefixes(S):
    \A other \in CommonPrefixes(S): Len(other) <= Len(longest)
```

`SubSeqs(<<1, 2, 3>>)` maps 16 index pairs to slices, `P` collects the
prefixes of each slice, and `LongestCommonPrefix` quantifies over `CommonPrefixes(S)`
twice, once inside the `CHOOSE`. The table shows that the cost appears only when
`LongestCommonPrefix` is applied to the result of `SubSeqs`, not to a literal
set; how the encoding grows was not measured.

## Corpus evidence

All 54 new Apalache crashes of the `module` corpus43 run are
`Terminating due to java.lang.OutOfMemoryError: Java heap space` in a worker
with a 1 GB heap, with no stack trace. `075a7ff4` also runs out of heap memory
in a fresh JVM at `-Xmx1g`, in `BoundedChecker` after 150 seconds, so at least this crash is not caused
by state accumulated in the long-lived worker; the others were not rerun.

`377f54ed` is the smallest of them. Its property is
`IsStrictPrefix(LongestCommonPrefix(SubSeqs(<<a, b>>)), <<a, b>>)` with two
singleton string sets as elements. The reduction above was derived from it;
with these elements the entry runs out of memory at `-Xmx1g` as well. It is a
mutant (`copy`, `parity_flip`) of `7af53875`, a generation-0 entry kept in
`04quality-pass`.

Only 20 of the 54 entries apply `LongestCommonPrefix`. All 54 are dominated by sequence slicing: `SubSeq` occurs in 94% of them and `\o` in 93%,
against 31% and 22% of the other corpus entries past the parser stage. The other 34
were not reduced and may have further causes, for example in
`ReplaceFirstSubSeq`, `ReplaceSubSeqAt` or `RemoveAt`, which also build
sequences from `SubSeq` with symbolic bounds.

## Expected behavior

A constant expression over a three-element sequence checks within seconds and
a few hundred megabytes, as its parts do.

## Impact

`LongestCommonPrefix` over a computed set of sequences is out of reach for
Apalache at default heap sizes, even when every sequence is a small constant.
The failure is an out-of-memory exit, which a user may attribute to the state
space rather than to one operator.
