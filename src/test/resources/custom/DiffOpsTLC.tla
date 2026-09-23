---- MODULE DiffOpsTLC ----
\* The recursive side of the diff-linked fixture, which only SANY and TLC read.
EXTENDS Integers, Sequences

RECURSIVE Sum(_)
Sum(S) == IF S = {} THEN 0 ELSE LET x == CHOOSE y \in S : TRUE IN x + Sum(S \ {x})

Length(s) == LET f[i \in 0..Len(s)] == IF i = 0 THEN 0 ELSE f[i - 1] + 1 IN f[Len(s)]
====
