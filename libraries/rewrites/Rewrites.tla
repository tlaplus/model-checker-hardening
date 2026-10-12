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
(*                                                                         *)
(* A weight annotation in the comment before a rule sets its default       *)
(* weight, the slots it takes among the rules that apply at a node; an     *)
(* unannotated rule has weight 1, and [metamorphic] weights overrides it.  *)
(* The tiers favour rules that change how a checker evaluates a term:      *)
(*   1  padding, which wraps a value: x + 0, P /\ TRUE, S \union S         *)
(*   2  reordering: commutation, symmetry and association                  *)
(*   4  rebuilding, which reconstructs a value: FunEta, SubSeqWhole        *)
(*   8  strategy, which replaces an operator: filters, quantifier laws,    *)
(*      De Morgan, EXCEPT, Len, membership in a product or function set    *)
(***************************************************************************)
EXTENDS Integers, FiniteSets, Sequences, Variants

PlusZero(x) == x = x + 0

AddSub(x, y) == x = x + (y - y)

DoubleNeg(P) == P = ~~P

UnionSelf(S) == S = S \union S

\* @weight: 8;
UnchangedPrime(x) == (UNCHANGED x) = (x' = x)

\* @weight: 8;
ForallNotExists(S, P(_)) == (\A e \in S : P(e)) = ~(\E e \in S : ~P(e))

\* Integers
TimesOne(x) == x = x * 1

NegNeg(x) == x = -(-x)

\* @weight: 2;
PlusComm(x, y) == (x + y) = (y + x)

\* @weight: 2;
TimesComm(x, y) == (x * y) = (y * x)

\* @weight: 2;
PlusAssoc(x, y, z) == ((x + y) + z) = (x + (y + z))

\* @weight: 8;
MinusAsPlusNeg(x, y) == (x - y) = (x + (-y))

\* @weight: 8;
TimesDistrib(x, y, z) == (x * (y + z)) = ((x * y) + (x * z))

\* @weight: 8;
LtAsNotGe(x, y) == (x < y) = ~(x >= y)

\* @weight: 8;
LeAsLtOrEq(x, y) == (x <= y) = (x < y \/ x = y)

\* @weight: 2;
GtAsLt(x, y) == (x > y) = (y < x)

\* Booleans
AndTrue(P) == P = (P /\ TRUE)

OrFalse(P) == P = (P \/ FALSE)

\* @weight: 2;
AndComm(P, Q) == (P /\ Q) = (Q /\ P)

\* @weight: 2;
OrComm(P, Q) == (P \/ Q) = (Q \/ P)

\* @weight: 8;
DeMorganAnd(P, Q) == (~(P /\ Q)) = (~P \/ ~Q)

\* @weight: 8;
DeMorganOr(P, Q) == (~(P \/ Q)) = (~P /\ ~Q)

\* @weight: 8;
ImpliesAsOr(P, Q) == (P => Q) = (~P \/ Q)

\* @weight: 8;
EquivAsImplies(P, Q) == (P <=> Q) = ((P => Q) /\ (Q => P))

\* Every type
\* @weight: 8;
NeqAsNotEq(x, y) == (x # y) = ~(x = y)

\* @weight: 2;
EqSym(x, y) == (x = y) = (y = x)

\* @weight: 8;
IfNegate(P, x, y) == (IF P THEN x ELSE y) = (IF ~P THEN y ELSE x)

IfTrue(x, y) == x = (IF TRUE THEN x ELSE y)

\* Sets and quantifiers
UnionEmpty(S) == S = S \union {}

IntersectSelf(S) == S = S \intersect S

MinusEmpty(S) == S = S \ {}

\* @weight: 2;
UnionComm(S, T) == (S \union T) = (T \union S)

\* @weight: 2;
IntersectComm(S, T) == (S \intersect T) = (T \intersect S)

\* @weight: 8;
MinusAsFilter(S, T) == (S \ T) = {e \in S : e \notin T}

\* @weight: 8;
IntersectAsFilter(S, T) == (S \intersect T) = {e \in S : e \in T}

\* @weight: 8;
InUnion(x, S, T) == (x \in (S \union T)) = (x \in S \/ x \in T)

\* @weight: 8;
InIntersect(x, S, T) == (x \in (S \intersect T)) = (x \in S /\ x \in T)

\* @weight: 8;
NotInAsNotIn(x, S) == (x \notin S) = ~(x \in S)

\* @weight: 8;
SubsetAsForall(S, T) == (S \subseteq T) = (\A e \in S : e \in T)

MapIdentity(S) == S = {e : e \in S}

FilterTrue(S) == S = {e \in S : TRUE}

\* @weight: 8;
FilterFilter(S, P(_), Q(_)) ==
    {e \in {d \in S : P(d)} : Q(e)} = {e \in S : P(e) /\ Q(e)}

\* @weight: 8;
ExistsNotForall(S, P(_)) == (\E e \in S : P(e)) = ~(\A e \in S : ~P(e))

\* @weight: 8;
ExistsUnion(S, T, P(_)) ==
    (\E e \in (S \union T) : P(e)) = ((\E e \in S : P(e)) \/ (\E e \in T : P(e)))

\* @weight: 8;
ForallAnd(S, P(_), Q(_)) ==
    (\A e \in S : P(e) /\ Q(e)) = ((\A e \in S : P(e)) /\ (\A e \in S : Q(e)))

\* Functions
\* @weight: 4;
FunEta(f) == f = [e \in DOMAIN f |-> f[e]]

\* @weight: 8;
DomainOfCtor(S, F(_)) == (DOMAIN [e \in S |-> F(e)]) = S

\* @type: (a -> b, a, b, b) => Bool;
\* @weight: 8;
ExceptTwice(f, x, y, z) ==
    [[f EXCEPT ![x] = y] EXCEPT ![x] = z] = [f EXCEPT ![x] = z]

\* @weight: 8;
InFunSet(f, S, T) == (f \in [S -> T]) = (DOMAIN f = S /\ \A e \in S : f[e] \in T)

\* Sequences
ConcatEmpty(s) == s = s \o <<>>

EmptyConcat(s) == s = <<>> \o s

\* @weight: 2;
ConcatAssoc(s, t, u) == ((s \o t) \o u) = (s \o (t \o u))

\* @weight: 8;
AppendAsConcat(s, x) == Append(s, x) = s \o <<x>>

\* @weight: 8;
LenConcat(s, t) == Len(s \o t) = Len(s) + Len(t)

\* @weight: 8;
LenAppend(s, x) == Len(Append(s, x)) = Len(s) + 1

\* @weight: 4;
SubSeqWhole(s) == s = SubSeq(s, 1, Len(s))

\* Tuples
\* @type: (<<a, b>>) => Bool;
\* @weight: 4;
PairEta(t) == t = <<t[1], t[2]>>

\* @type: (<<a, b>>, <<a, b>>) => Bool;
\* @weight: 8;
PairEqAsAnd(s, t) == (s = t) = (s[1] = t[1] /\ s[2] = t[2])

\* @type: (<<a, b>>, Set(a), Set(b)) => Bool;
\* @weight: 8;
PairInProduct(t, S, T) == (t \in (S \X T)) = (t[1] \in S /\ t[2] \in T)

\* Records with the field field0
\* @type: ({ field0: a, z }) => Bool;
\* @weight: 4;
RecordExceptSame(r) == r = [r EXCEPT !.field0 = r.field0]

\* @type: ({ field0: a, z }, a, a) => Bool;
\* @weight: 8;
RecordExceptTwice(r, x, y) ==
    [[r EXCEPT !.field0 = x] EXCEPT !.field0 = y] = [r EXCEPT !.field0 = y]

\* @type: ({ field0: a, z }, a) => Bool;
\* @weight: 8;
RecordExceptGet(r, x) == ([r EXCEPT !.field0 = x].field0) = x

\* Variants with the tag Tag0
\* @weight: 8;
VariantTagOf(x) == VariantTag(Variant("Tag0", x)) = "Tag0"

\* @weight: 8;
VariantGetOf(x, d) == VariantGetOrElse("Tag0", Variant("Tag0", x), d) = x
=============================================================================
