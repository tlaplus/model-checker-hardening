--------------------------- MODULE RecursionPairs ---------------------------
(***************************************************************************)
(* The pairwise self-test of libraries/recursion (ADR 0015): TLC evaluates *)
(* both modules of every diff-linked operator and asserts that they agree  *)
(* on small domains, including empty collections and negative integers.  *)
(* A false assumption names the line of the operator whose pair disagrees. *)
(* Spec and Inv only let the fuzztla TLC worker check this module.         *)
(***************************************************************************)
EXTENDS Integers, Sequences, FiniteSets

T == INSTANCE RecursionTLC
A == INSTANCE RecursionApalache

Ints == -2..3
Sets == SUBSET Ints
SeqsOver(D, n) == UNION {[1..k -> D] : k \in 0..n}
Seqs == SeqsOver(Ints, 3)
SetsOfSets == SUBSET SUBSET {1, 2, 3}
Relations == SUBSET ({1, 2, 3} \X {1, 2, 3})
Functions == UNION {[D -> -1..2] : D \in SUBSET {1, 2, 3}}
\* Past every clamp, and beyond TLC's recursion-friendly range where no clamp applies.
Clamped == -3..35
Large == {2147483647, -2147483647, 1000000000, 1836311903, 1134903170}

ASSUME \A S \in Sets : T!SetSum(S) = A!SetSum(S)
ASSUME \A S \in Sets : T!SetSumLetRec(S) = A!SetSumLetRec(S)
ASSUME \A S \in Sets : T!SetSumFun(S) = A!SetSumFun(S)
ASSUME \A S \in Sets : T!SetSumHO(S) = A!SetSumHO(S)
ASSUME \A S \in Sets : T!SetCount(S) = A!SetCount(S)
ASSUME \A SS \in SetsOfSets : T!SetUnionAll(SS) = A!SetUnionAll(SS)
ASSUME \A S \in Sets : T!SetPowerset(S) = A!SetPowerset(S)
ASSUME \A S \in Sets, d \in Ints : T!SetMaxWith(S, d) = A!SetMaxWith(S, d)
ASSUME \A S \in Sets : T!SetAllPositive(S) = A!SetAllPositive(S)
ASSUME \A S \in Sets : T!SetMapDouble(S) = A!SetMapDouble(S)
ASSUME \A S \in Sets : T!SetFilterPositive(S) = A!SetFilterPositive(S)
ASSUME \A S \in Sets : T!SetToSortedSeq(S) = A!SetToSortedSeq(S)
ASSUME \A S \in SUBSET {-1, 0, 29, 30, 31} :T!SetTriangles(S) = A!SetTriangles(S)

ASSUME \A s \in Seqs : T!SeqSum(s) = A!SeqSum(s)
ASSUME \A s \in Seqs : T!SeqSumLetRec(s) = A!SeqSumLetRec(s)
ASSUME \A s \in Seqs : T!SeqSumFun(s) = A!SeqSumFun(s)
ASSUME \A s \in Seqs : T!SeqReverse(s) = A!SeqReverse(s)
ASSUME \A s \in Seqs : T!SeqToSet(s) = A!SeqToSet(s)
ASSUME \A ss \in SeqsOver(SeqsOver(-1..1, 2), 2) : T!SeqFlatten(ss) = A!SeqFlatten(ss)
ASSUME \A s \in Seqs : T!SeqMapDouble(s) = A!SeqMapDouble(s)
ASSUME \A s \in Seqs : T!SeqMapDoubleHO(s) = A!SeqMapDoubleHO(s)
ASSUME \A s \in Seqs : T!SeqFilterPositive(s) = A!SeqFilterPositive(s)
ASSUME \A s \in Seqs, x \in Ints : T!SeqCountOf(s, x) = A!SeqCountOf(s, x)
ASSUME \A s \in Seqs : T!SeqIsSorted(s) = A!SeqIsSorted(s)
ASSUME \A s \in SeqsOver(Ints, 4) : T!SeqInsertionSort(s) = A!SeqInsertionSort(s)
ASSUME \A s \in Seqs : T!SeqEvenLength(s) = A!SeqEvenLength(s)

ASSUME \A f \in Functions : T!FunSumValues(f) = A!FunSumValues(f)
ASSUME \A f \in Functions : T!FunIncrementAll(f) = A!FunIncrementAll(f)
ASSUME \A S \in SUBSET {-1, 0, 7, 31} : T!FunTriangles(S) = A!FunTriangles(S)

ASSUME \A R \in Relations : T!TransitiveClosure(R) = A!TransitiveClosure(R)
ASSUME \A R \in Relations, x \in {1, 2, 3, 4} : T!Reachable(R, x) = A!Reachable(R, x)

ASSUME \A n \in Clamped : T!IntTriangle(n) = A!IntTriangle(n)
ASSUME \A n \in Clamped : T!IntPow2(n) = A!IntPow2(n)
ASSUME \A n \in Clamped : T!IntFactorial(n) = A!IntFactorial(n)
ASSUME \A n \in Clamped : T!IntFib(n) = A!IntFib(n)
ASSUME \A n \in Clamped : T!IntFibFun(n) = A!IntFibFun(n)
ASSUME \A n \in -1000..1000 \cup Large : T!IntDigitSum(n) = A!IntDigitSum(n)
ASSUME \A a, b \in -12..12 \cup Large : T!IntGcd(a, b) = A!IntGcd(a, b)
ASSUME \A n \in -100..100 \cup Large : T!IntIsEven(n) = A!IntIsEven(n)

VARIABLE checked
Spec == checked = TRUE /\ [][UNCHANGED checked]_checked
Inv == checked
=============================================================================
