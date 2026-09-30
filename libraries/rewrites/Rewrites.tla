------------------------------ MODULE Rewrites ------------------------------
(***************************************************************************)
(* The rewrite rules of metamorphic testing (ADR 0017). Every top-level   *)
(* definition is a rule F(p1, ..., pn) == A = B: the rewriter replaces an  *)
(* instance of A with the same instance of B. A parameter that occurs only *)
(* in B is fresh: the generator draws it at its type.                      *)
(*                                                                         *)
(* Declaration order is part of the byte encoding: a corpus pins these     *)
(* sources, so editing a rule requires a new corpus. Put helpers in a      *)
(* module this one EXTENDS. Prove every rule in RewritesProofs.tla.        *)
(*                                                                         *)
(* A pattern names a field or a tag literally, so the record and variant   *)
(* rules use the generator's first names, field0 and Tag0.                 *)
(***************************************************************************)
EXTENDS Integers, FiniteSets, Sequences, Variants

PlusZero(x) == x = x + 0

AddSub(x, y) == x = x + (y - y)

DoubleNeg(P) == P = ~~P

UnionSelf(S) == S = S \union S

UnchangedPrime(x) == (UNCHANGED x) = (x' = x)

ForallNotExists(S, P(_)) == (\A e \in S : P(e)) = ~(\E e \in S : ~P(e))

\* Integers
TimesOne(x) == x = x * 1

NegNeg(x) == x = -(-x)

PlusComm(x, y) == (x + y) = (y + x)

TimesComm(x, y) == (x * y) = (y * x)

PlusAssoc(x, y, z) == ((x + y) + z) = (x + (y + z))

MinusAsPlusNeg(x, y) == (x - y) = (x + (-y))

TimesDistrib(x, y, z) == (x * (y + z)) = ((x * y) + (x * z))

LtAsNotGe(x, y) == (x < y) = ~(x >= y)

LeAsLtOrEq(x, y) == (x <= y) = (x < y \/ x = y)

GtAsLt(x, y) == (x > y) = (y < x)

\* Booleans
AndTrue(P) == P = (P /\ TRUE)

OrFalse(P) == P = (P \/ FALSE)

AndComm(P, Q) == (P /\ Q) = (Q /\ P)

OrComm(P, Q) == (P \/ Q) = (Q \/ P)

DeMorganAnd(P, Q) == (~(P /\ Q)) = (~P \/ ~Q)

DeMorganOr(P, Q) == (~(P \/ Q)) = (~P /\ ~Q)

ImpliesAsOr(P, Q) == (P => Q) = (~P \/ Q)

EquivAsImplies(P, Q) == (P <=> Q) = ((P => Q) /\ (Q => P))

\* Every type
NeqAsNotEq(x, y) == (x # y) = ~(x = y)

EqSym(x, y) == (x = y) = (y = x)

IfNegate(P, x, y) == (IF P THEN x ELSE y) = (IF ~P THEN y ELSE x)

IfTrue(x, y) == x = (IF TRUE THEN x ELSE y)

\* Sets and quantifiers
UnionEmpty(S) == S = S \union {}

IntersectSelf(S) == S = S \intersect S

MinusEmpty(S) == S = S \ {}

UnionComm(S, T) == (S \union T) = (T \union S)

IntersectComm(S, T) == (S \intersect T) = (T \intersect S)

MinusAsFilter(S, T) == (S \ T) = {e \in S : e \notin T}

IntersectAsFilter(S, T) == (S \intersect T) = {e \in S : e \in T}

InUnion(x, S, T) == (x \in (S \union T)) = (x \in S \/ x \in T)

InIntersect(x, S, T) == (x \in (S \intersect T)) = (x \in S /\ x \in T)

NotInAsNotIn(x, S) == (x \notin S) = ~(x \in S)

SubsetAsForall(S, T) == (S \subseteq T) = (\A e \in S : e \in T)

MapIdentity(S) == S = {e : e \in S}

FilterTrue(S) == S = {e \in S : TRUE}

FilterFilter(S, P(_), Q(_)) ==
    {e \in {d \in S : P(d)} : Q(e)} = {e \in S : P(e) /\ Q(e)}

ExistsNotForall(S, P(_)) == (\E e \in S : P(e)) = ~(\A e \in S : ~P(e))

ExistsUnion(S, T, P(_)) ==
    (\E e \in (S \union T) : P(e)) = ((\E e \in S : P(e)) \/ (\E e \in T : P(e)))

ForallAnd(S, P(_), Q(_)) ==
    (\A e \in S : P(e) /\ Q(e)) = ((\A e \in S : P(e)) /\ (\A e \in S : Q(e)))

\* Functions
FunEta(f) == f = [e \in DOMAIN f |-> f[e]]

DomainOfCtor(S, F(_)) == (DOMAIN [e \in S |-> F(e)]) = S

\* @type: (a -> b, a, b, b) => Bool;
ExceptTwice(f, x, y, z) ==
    [[f EXCEPT ![x] = y] EXCEPT ![x] = z] = [f EXCEPT ![x] = z]

InFunSet(f, S, T) == (f \in [S -> T]) = (DOMAIN f = S /\ \A e \in S : f[e] \in T)

\* Sequences
ConcatEmpty(s) == s = s \o <<>>

EmptyConcat(s) == s = <<>> \o s

ConcatAssoc(s, t, u) == ((s \o t) \o u) = (s \o (t \o u))

AppendAsConcat(s, x) == Append(s, x) = s \o <<x>>

LenConcat(s, t) == Len(s \o t) = Len(s) + Len(t)

LenAppend(s, x) == Len(Append(s, x)) = Len(s) + 1

SubSeqWhole(s) == s = SubSeq(s, 1, Len(s))

\* Tuples
\* @type: (<<a, b>>) => Bool;
PairEta(t) == t = <<t[1], t[2]>>

\* @type: (<<a, b>>, <<a, b>>) => Bool;
PairEqAsAnd(s, t) == (s = t) = (s[1] = t[1] /\ s[2] = t[2])

\* @type: (<<a, b>>, Set(a), Set(b)) => Bool;
PairInProduct(t, S, T) == (t \in (S \X T)) = (t[1] \in S /\ t[2] \in T)

\* Records with the field field0
\* @type: ({ field0: a, z }) => Bool;
RecordExceptSame(r) == r = [r EXCEPT !.field0 = r.field0]

\* @type: ({ field0: a, z }, a, a) => Bool;
RecordExceptTwice(r, x, y) ==
    [[r EXCEPT !.field0 = x] EXCEPT !.field0 = y] = [r EXCEPT !.field0 = y]

\* @type: ({ field0: a, z }, a) => Bool;
RecordExceptGet(r, x) == ([r EXCEPT !.field0 = x].field0) = x

\* Variants with the tag Tag0
VariantTagOf(x) == VariantTag(Variant("Tag0", x)) = "Tag0"

VariantGetOf(x, d) == VariantGetOrElse("Tag0", Variant("Tag0", x), d) = x
=============================================================================
