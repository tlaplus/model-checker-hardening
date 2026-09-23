-------------------------- MODULE RecursionApalache --------------------------
(***************************************************************************)
(* Non-recursive counterparts of RecursionTLC (ADR 0015), in folds and     *)
(* closed forms that Apalache evaluates. Fuzztla typechecks this module,   *)
(* so its signatures and definitions are the reference: generated calls    *)
(* are typed by it, and Apalache, richness scoring and known-defect        *)
(* matching read it. Variants of one RecursionTLC operator are aliases of  *)
(* one definition here.                                                    *)
(*                                                                         *)
(* Folds over sets combine elements commutatively and associatively, or    *)
(* apply one step a fixed number of times, so their value does not depend *)
(* on the order Apalache visits the elements in. Integer ranges have       *)
(* constant bounds.                                                        *)
(*                                                                         *)
(* Snowcat generalizes the type of a parameter that only LET definitions   *)
(* constrain, e.g. LET k == n + 1 IN k gets (a) => Int                     *)
(* (apalache-typechecker-001). Operators of that shape carry a @type.      *)
(***************************************************************************)
EXTENDS Integers, Sequences, FiniteSets, Apalache

\* The argument of an integer-driven operator, clamped to 0..hi.
Clamp(n, hi) == IF n < 0 THEN 0 ELSE IF n > hi THEN hi ELSE n

\* The set 1..k for 0 <= k <= hi, as a filter of a range with constant bounds: folding a
\* step over it applies the step k times.
Iterations(k, hi) == {i \in 1..hi : i <= k}

---------------------------------------------------------------------------
(* Sets *)

SetSum(S) == LET Plus(a, x) == a + x IN ApaFoldSet(Plus, 0, S)
SetSumLetRec(S) == SetSum(S)
SetSumFun(S) == SetSum(S)
SetSumHO(S) == SetSum(S)

SetCount(S) == Cardinality(S)

SetUnionAll(SS) == UNION SS

SetPowerset(S) == SUBSET S

SetMaxWith(S, d) == LET Max(m, x) == IF x > m THEN x ELSE m IN ApaFoldSet(Max, d, S)

SetAllPositive(S) == \A x \in S : x > 0

SetMapDouble(S) == {2 * x : x \in S}

SetFilterPositive(S) == {x \in S : x > 0}

\* Inserts x into the sorted sequence s.
\* @type: (Int, Seq(Int)) => Seq(Int);
InsertSorted(x, s) ==
    LET \* <<prefix, inserted>>: copies s, placing x before its first element >= x.
        \* @type: (<<Seq(Int), Bool>>, Int) => <<Seq(Int), Bool>>;
        Step(st, y) ==
            IF ~st[2] /\ x <= y THEN <<Append(Append(st[1], x), y), TRUE>> ELSE <<Append(st[1], y), st[2]>>
        st == ApaFoldSeqLeft(Step, <<<<>>, FALSE>>, s)
    IN IF st[2] THEN st[1] ELSE Append(st[1], x)

SetToSortedSeq(S) == LET Insert(s, x) == InsertSorted(x, s) IN ApaFoldSet(Insert, <<>>, S)

---------------------------------------------------------------------------
(* Sequences *)

SeqSum(s) == LET Plus(a, x) == a + x IN ApaFoldSeqLeft(Plus, 0, s)
SeqSumLetRec(s) == SeqSum(s)
SeqSumFun(s) == SeqSum(s)

SeqReverse(s) == LET Prepend(r, x) == <<x>> \o r IN ApaFoldSeqLeft(Prepend, <<>>, s)

SeqToSet(s) == LET Add(S, x) == S \cup {x} IN ApaFoldSeqLeft(Add, {}, s)

SeqFlatten(ss) == LET Concat(r, t) == r \o t IN ApaFoldSeqLeft(Concat, <<>>, ss)

SeqMapDouble(s) == LET Double(r, x) == Append(r, 2 * x) IN ApaFoldSeqLeft(Double, <<>>, s)
SeqMapDoubleHO(s) == SeqMapDouble(s)

SeqFilterPositive(s) ==
    LET Keep(r, x) == IF x > 0 THEN Append(r, x) ELSE r IN ApaFoldSeqLeft(Keep, <<>>, s)

SeqCountOf(s, x) == LET Count(n, y) == IF y = x THEN n + 1 ELSE n IN ApaFoldSeqLeft(Count, 0, s)

SeqIsSorted(s) ==
    LET \* <<sorted so far, seen an element, last element>>
        \* @type: (<<Bool, Bool, Int>>, Int) => <<Bool, Bool, Int>>;
        Step(st, x) == <<st[1] /\ (~st[2] \/ st[3] <= x), TRUE, x>>
    IN ApaFoldSeqLeft(Step, <<TRUE, FALSE, 0>>, s)[1]

SeqInsertionSort(s) == LET Insert(r, x) == InsertSorted(x, r) IN ApaFoldSeqLeft(Insert, <<>>, s)

SeqEvenLength(s) == Len(s) % 2 = 0

---------------------------------------------------------------------------
(* Functions *)

\* @type: (a -> Int) => Int;
FunSumValues(f) == LET Plus(a, x) == a + f[x] IN ApaFoldSet(Plus, 0, DOMAIN f)

\* @type: (a -> Int) => (a -> Int);
FunIncrementAll(f) == [x \in DOMAIN f |-> f[x] + 1]

---------------------------------------------------------------------------
(* Relations *)

\* Warshall's algorithm: the closure does not depend on the order of the pivots.
\* @type: (Set(<<a, a>>)) => Set(<<a, a>>);
TransitiveClosure(R) ==
    LET nodes == {e[1] : e \in R} \cup {e[2] : e \in R}
        Pivot(T, k) == T \cup {e \in nodes \X nodes : <<e[1], k>> \in T /\ <<k, e[2]>> \in T}
    IN ApaFoldSet(Pivot, R, nodes)

\* @type: (Set(<<a, a>>), a) => Set(a);
Reachable(R, x) == {e[2] : e \in {e \in TransitiveClosure(R) : e[1] = x}}

---------------------------------------------------------------------------
(* Integers *)

\* @type: (Int) => Int;
IntTriangle(n) == LET k == Clamp(n, 30) IN (k * (k + 1)) \div 2

IntPow2(n) == 2 ^ Clamp(n, 20)

IntFactorial(n) ==
    LET Mult(a, i) == a * i IN ApaFoldSet(Mult, 1, Iterations(Clamp(n, 12), 12))

IntFib(n) ==
    LET \* @type: (<<Int, Int>>, Int) => <<Int, Int>>;
        Step(p, i) == <<p[2], p[1] + p[2]>>
    IN ApaFoldSet(Step, <<0, 1>>, Iterations(Clamp(n, 20), 20))[1]
IntFibFun(n) == IntFib(n)

\* The decimal digits of |n|, which has at most 10 in TLC's integer range.
\* @type: (Int) => Int;
IntDigitSum(n) ==
    LET a == IF n < 0 THEN -n ELSE n
        Digit(sum, i) == sum + ((a \div 10 ^ i) % 10)
    IN ApaFoldSet(Digit, 0, 0..9)

\* Euclid's step, applied 48 times, more often than any pair in TLC's integer range needs.
\* TLC passes a fold's accumulator unevaluated, so evaluating one fold over 1..48 in the
\* self-test nests 48 steps on TLC's stack. Six folds of 8 steps, each forced by testing
\* its remainder before the next fold starts, keep that depth at 8.
\* @type: (Int, Int) => Int;
IntGcd(a, b) ==
    LET \* @type: (<<Int, Int>>, Int) => <<Int, Int>>;
        Step(p, i) == IF p[2] = 0 THEN p ELSE <<p[2], p[1] % p[2]>>
        \* @type: (<<Int, Int>>) => <<Int, Int>>;
        Steps(p) == ApaFoldSet(Step, p, 1..8)
        p1 == Steps(<<IF a < 0 THEN -a ELSE a, IF b < 0 THEN -b ELSE b>>)
        p2 == Steps(p1)
        p3 == Steps(p2)
        p4 == Steps(p3)
        p5 == Steps(p4)
        p6 == Steps(p5)
    IN IF p1[2] = 0 THEN p1[1]
       ELSE IF p2[2] = 0 THEN p2[1]
       ELSE IF p3[2] = 0 THEN p3[1]
       ELSE IF p4[2] = 0 THEN p4[1]
       ELSE IF p5[2] = 0 THEN p5[1]
       ELSE p6[1]

IntIsEven(n) == n % 2 = 0

SetTriangles(S) == {IntTriangle(x) : x \in S}
FunTriangles(S) == [x \in S |-> IntTriangle(x)]
=============================================================================
