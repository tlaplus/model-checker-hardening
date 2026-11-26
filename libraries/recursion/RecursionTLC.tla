---------------------------- MODULE RecursionTLC ----------------------------
(***************************************************************************)
(* Recursive definitions that TLC evaluates and Apalache rejects. Every    *)
(* operator has a non-recursive counterpart of the same name and arity in  *)
(* RecursionApalache (ADR 0015). Operators that share a counterpart differ *)
(* only in the recursion mechanism, named by their suffix:                 *)
(*                                                                         *)
(*   (none)   top-level RECURSIVE operator                                 *)
(*   LetRec   LET RECURSIVE operator                                       *)
(*   Fun      recursive function definition, in a LET or at the top level  *)
(*   HO       higher-order RECURSIVE helper taking an operator argument    *)
(*                                                                         *)
(* A recursion that picks set elements with CHOOSE combines them           *)
(* commutatively and associatively, so its value does not depend on the   *)
(* order TLC picks them in. Integer-driven recursion clamps its argument   *)
(* exactly as the counterpart does.                                        *)
(***************************************************************************)
EXTENDS Integers, Sequences, FiniteSets

\* The argument of an integer-driven recursion, clamped to 0..hi.
Clamp(n, hi) == IF n < 0 THEN 0 ELSE IF n > hi THEN hi ELSE n

\* An arbitrary element of a nonempty set.
Any(S) == CHOOSE x \in S : TRUE

---------------------------------------------------------------------------
(* Generic recursive helpers, instantiated with operator arguments. *)

RECURSIVE FoldSet(_, _, _)
FoldSet(Op(_, _), v, S) ==
    IF S = {} THEN v ELSE LET x == Any(S) IN FoldSet(Op, Op(v, x), S \ {x})

RECURSIVE MapSeq(_, _)
MapSeq(F(_), s) == IF s = <<>> THEN <<>> ELSE <<F(Head(s))>> \o MapSeq(F, Tail(s))

---------------------------------------------------------------------------
(* Sets *)

RECURSIVE SetSum(_)
SetSum(S) == IF S = {} THEN 0 ELSE LET x == Any(S) IN x + SetSum(S \ {x})

SetSumLetRec(S) ==
    LET RECURSIVE Sum(_)
        Sum(T) == IF T = {} THEN 0 ELSE LET x == Any(T) IN x + Sum(T \ {x})
    IN Sum(S)

SetSumFun(S) ==
    LET sum[T \in SUBSET S] == IF T = {} THEN 0 ELSE LET x == Any(T) IN x + sum[T \ {x}]
    IN sum[S]

SetSumHO(S) == LET Plus(a, b) == a + b IN FoldSet(Plus, 0, S)

RECURSIVE SetCount(_)
SetCount(S) == IF S = {} THEN 0 ELSE 1 + SetCount(S \ {Any(S)})

RECURSIVE SetUnionAll(_)
SetUnionAll(SS) == IF SS = {} THEN {} ELSE LET T == Any(SS) IN T \cup SetUnionAll(SS \ {T})

RECURSIVE SetPowerset(_)
SetPowerset(S) ==
    IF S = {} THEN {{}}
    ELSE LET x == Any(S)
             P == SetPowerset(S \ {x})
         IN P \cup {T \cup {x} : T \in P}

\* A recursive function keyed by subsets, whose values are sets of sets.
SetPowersetFun(S) ==
    LET ps[T \in SUBSET S] ==
            IF T = {} THEN {{}}
            ELSE LET x == Any(T)
                     P == ps[T \ {x}]
                 IN P \cup {U \cup {x} : U \in P}
    IN ps[S]

RECURSIVE SetMaxWith(_, _)
SetMaxWith(S, d) ==
    IF S = {} THEN d
    ELSE LET x == Any(S)
             m == SetMaxWith(S \ {x}, d)
         IN IF x > m THEN x ELSE m

RECURSIVE SetAllPositive(_)
SetAllPositive(S) == S = {} \/ (LET x == Any(S) IN x > 0 /\ SetAllPositive(S \ {x}))

RECURSIVE SetMapDouble(_)
SetMapDouble(S) == IF S = {} THEN {} ELSE LET x == Any(S) IN {2 * x} \cup SetMapDouble(S \ {x})

RECURSIVE SetFilterPositive(_)
SetFilterPositive(S) ==
    IF S = {} THEN {}
    ELSE LET x == Any(S)
             rest == SetFilterPositive(S \ {x})
         IN IF x > 0 THEN rest \cup {x} ELSE rest

\* Recursion on the unique minimum: CHOOSE has exactly one witness.
RECURSIVE SetToSortedSeq(_)
SetToSortedSeq(S) ==
    IF S = {} THEN <<>>
    ELSE LET m == CHOOSE x \in S : \A y \in S : x <= y
         IN <<m>> \o SetToSortedSeq(S \ {m})

---------------------------------------------------------------------------
(* Sequences *)

RECURSIVE SeqSum(_)
SeqSum(s) == IF s = <<>> THEN 0 ELSE Head(s) + SeqSum(Tail(s))

SeqSumLetRec(s) ==
    LET RECURSIVE Sum(_, _)
        \* Accumulator-passing, so the recursive call is in tail position.
        Sum(t, acc) == IF t = <<>> THEN acc ELSE Sum(Tail(t), acc + Head(t))
    IN Sum(s, 0)

SeqSumFun(s) ==
    LET sum[i \in 0..Len(s)] == IF i = 0 THEN 0 ELSE sum[i - 1] + s[i]
    IN sum[Len(s)]

RECURSIVE SeqReverse(_)
SeqReverse(s) == IF s = <<>> THEN <<>> ELSE SeqReverse(Tail(s)) \o <<Head(s)>>

\* A recursive function keyed by sequences: the suffixes of s, including <<>>.
SeqReverseFun(s) ==
    LET rev[t \in {SubSeq(s, i, Len(s)) : i \in 1..Len(s) + 1}] ==
            IF t = <<>> THEN <<>> ELSE rev[Tail(t)] \o <<Head(t)>>
    IN rev[s]

RECURSIVE SeqToSet(_)
SeqToSet(s) == IF s = <<>> THEN {} ELSE {Head(s)} \cup SeqToSet(Tail(s))

RECURSIVE SeqFlatten(_)
SeqFlatten(ss) == IF ss = <<>> THEN <<>> ELSE Head(ss) \o SeqFlatten(Tail(ss))

RECURSIVE SeqMapDouble(_)
SeqMapDouble(s) == IF s = <<>> THEN <<>> ELSE <<2 * Head(s)>> \o SeqMapDouble(Tail(s))

SeqMapDoubleHO(s) == LET Double(x) == 2 * x IN MapSeq(Double, s)

RECURSIVE SeqFilterPositive(_)
SeqFilterPositive(s) ==
    IF s = <<>> THEN <<>>
    ELSE LET rest == SeqFilterPositive(Tail(s))
         IN IF Head(s) > 0 THEN <<Head(s)>> \o rest ELSE rest

RECURSIVE SeqCountOf(_, _)
SeqCountOf(s, x) ==
    IF s = <<>> THEN 0 ELSE (IF Head(s) = x THEN 1 ELSE 0) + SeqCountOf(Tail(s), x)

RECURSIVE SeqIsSorted(_)
SeqIsSorted(s) == Len(s) <= 1 \/ (s[1] <= s[2] /\ SeqIsSorted(Tail(s)))

RECURSIVE SeqInsertSorted(_, _)
SeqInsertSorted(x, s) ==
    IF s = <<>> THEN <<x>>
    ELSE IF x <= Head(s) THEN <<x>> \o s
    ELSE <<Head(s)>> \o SeqInsertSorted(x, Tail(s))

RECURSIVE SeqInsertionSort(_)
SeqInsertionSort(s) == IF s = <<>> THEN <<>> ELSE SeqInsertSorted(Head(s), SeqInsertionSort(Tail(s)))

\* Mutual recursion.
RECURSIVE SeqEven(_), SeqOdd(_)
SeqEven(s) == IF s = <<>> THEN TRUE ELSE SeqOdd(Tail(s))
SeqOdd(s) == IF s = <<>> THEN FALSE ELSE SeqEven(Tail(s))
SeqEvenLength(s) == SeqEven(s)

---------------------------------------------------------------------------
(* Functions *)

FunSumValues(f) ==
    LET RECURSIVE Sum(_)
        Sum(D) == IF D = {} THEN 0 ELSE LET x == Any(D) IN f[x] + Sum(D \ {x})
    IN Sum(DOMAIN f)

\* Recursion through EXCEPT, one domain element at a time.
FunIncrementAll(f) ==
    LET RECURSIVE Inc(_, _)
        Inc(g, D) == IF D = {} THEN g ELSE LET x == Any(D) IN Inc([g EXCEPT ![x] = @ + 1], D \ {x})
    IN Inc(f, DOMAIN f)

\* A recursive function whose values are functions, updated through EXCEPT.
FunIncrementAllFun(f) ==
    LET inc[D \in SUBSET DOMAIN f] ==
            IF D = {} THEN f ELSE LET x == Any(D) IN [inc[D \ {x}] EXCEPT ![x] = @ + 1]
    IN inc[DOMAIN f]

---------------------------------------------------------------------------
(* Relations *)

\* The least fixed point of composing the relation with itself.
RECURSIVE TransitiveClosure(_)
TransitiveClosure(R) ==
    LET step == R \cup {<<pq[1][1], pq[2][2]>> : pq \in {pq \in R \X R : pq[1][2] = pq[2][1]}}
    IN IF step = R THEN R ELSE TransitiveClosure(step)

\* A recursive function whose values are relations: step k adds the paths of k + 1 edges.
\* A shortest path has at most Cardinality(R) edges.
TransitiveClosureFun(R) ==
    LET tc[k \in 0..Cardinality(R)] ==
            IF k = 0 THEN R
            ELSE LET prev == tc[k - 1]
                 IN prev \cup {<<pq[1][1], pq[2][2]>> : pq \in {pq \in prev \X R : pq[1][2] = pq[2][1]}}
    IN tc[Cardinality(R)]

\* The elements reachable from x in one or more steps, by recursion over a frontier.
Reachable(R, x) ==
    LET RECURSIVE Visit(_, _)
        Visit(seen, frontier) ==
            IF frontier = {} THEN seen
            ELSE LET next == {e[2] : e \in {e \in R : e[1] \in frontier}}
                 IN Visit(seen \cup next, next \ seen)
    IN Visit({}, {x})

---------------------------------------------------------------------------
(* Integers *)

IntTriangle(n) ==
    LET RECURSIVE Tri(_)
        Tri(k) == IF k = 0 THEN 0 ELSE k + Tri(k - 1)
    IN Tri(Clamp(n, 30))

\* A LET recursive function over an infinite domain.
IntTriangleFun(n) ==
    LET tri[k \in Nat] == IF k = 0 THEN 0 ELSE k + tri[k - 1]
    IN tri[Clamp(n, 30)]

RECURSIVE Pow2(_)
Pow2(k) == IF k = 0 THEN 1 ELSE 2 * Pow2(k - 1)
IntPow2(n) == Pow2(Clamp(n, 20))

\* A top-level recursive function over an infinite domain.
pow2[k \in Nat] == IF k = 0 THEN 1 ELSE 2 * pow2[k - 1]
IntPow2Fun(n) == pow2[Clamp(n, 20)]

IntFactorial(n) ==
    LET fact[k \in 0..Clamp(n, 12)] == IF k = 0 THEN 1 ELSE k * fact[k - 1]
    IN fact[Clamp(n, 12)]

\* The recursive function itself, compared as a whole value.
IntFactorialTableFun(n) ==
    LET fact[k \in 0..Clamp(n, 12)] == IF k = 0 THEN 1 ELSE k * fact[k - 1]
    IN fact

\* A recursive function of two arguments: Pascal's triangle; 0 unless 0 <= k <= m.
IntBinomialFun(n, k) ==
    LET m == Clamp(n, 12)
        c[i \in 0..m, j \in 0..m] ==
            IF j = 0 \/ j = i THEN 1 ELSE IF j > i THEN 0 ELSE c[i - 1, j - 1] + c[i - 1, j]
    IN IF k < 0 \/ k > m THEN 0 ELSE c[m, k]

\* Doubly recursive, so the clamp keeps the number of calls near 20 000.
RECURSIVE Fib(_)
Fib(k) == IF k < 2 THEN k ELSE Fib(k - 1) + Fib(k - 2)
IntFib(n) == Fib(Clamp(n, 20))

IntFibFun(n) ==
    LET fib[k \in 0..Clamp(n, 20)] == IF k < 2 THEN k ELSE fib[k - 1] + fib[k - 2]
    IN fib[Clamp(n, 20)]

IntDigitSum(n) ==
    LET RECURSIVE Digits(_)
        Digits(k) == IF k = 0 THEN 0 ELSE (k % 10) + Digits(k \div 10)
    IN Digits(IF n < 0 THEN -n ELSE n)

RECURSIVE Euclid(_, _)
Euclid(a, b) == IF b = 0 THEN a ELSE Euclid(b, a % b)
IntGcd(a, b) == Euclid(IF a < 0 THEN -a ELSE a, IF b < 0 THEN -b ELSE b)

\* Mutual recursion on n % 64, which has the parity of n.
RECURSIVE IsEven(_), IsOdd(_)
IsEven(k) == IF k = 0 THEN TRUE ELSE IsOdd(k - 1)
IsOdd(k) == IF k = 0 THEN FALSE ELSE IsEven(k - 1)
IntIsEven(n) == IsEven(n % 64)

\* Mutual recursion as one recursive function with record values.
IntIsEvenFun(n) ==
    LET parity[k \in 0..63] ==
            IF k = 0 THEN [even |-> TRUE, odd |-> FALSE]
            ELSE LET p == parity[k - 1] IN [even |-> p.odd, odd |-> p.even]
    IN parity[n % 64].even

\* Recursion inside set and function constructors.
SetTriangles(S) == {IntTriangle(x) : x \in S}
FunTriangles(S) == [x \in S |-> IntTriangle(x)]

\* A recursive function whose domain depends on the constructor's bound variable.
FunTrianglesFun(S) ==
    [x \in S |->
        LET m == Clamp(x, 30)
            tri[k \in 0..m] == IF k = 0 THEN 0 ELSE k + tri[k - 1]
        IN tri[m]]
=============================================================================
