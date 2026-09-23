---- MODULE DiffOpsApalache ----
\* The fold-based side of the diff-linked fixture; DiffOpsTLC defines the same operators recursively.
EXTENDS Integers, Sequences, Apalache

Sum(S) == LET Plus(a, b) == a + b IN ApaFoldSet(Plus, 0, S)

Length(s) == LET Count(n, x) == n + 1 IN ApaFoldSeqLeft(Count, 0, s)
====
