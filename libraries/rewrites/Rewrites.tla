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
(*   1  padding, which wraps a value: x + 0, P /\ TRUE, S \union S, and    *)
(*      the beta and iota redexes that match every value of a type:        *)
(*      LET y == x IN y, [field0 |-> x].field0                             *)
(*   2  reordering: commutation, symmetry and association                  *)
(*   4  rebuilding, which reconstructs a value: FunEta, SubSeqWhole        *)
(*   8  strategy, which replaces an operator: filters, quantifier laws,    *)
(*      De Morgan, EXCEPT, Len, membership in a product or function set,   *)
(*      CASE, and a binder body moved into a LET definition or a LAMBDA    *)
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

\* @weight: 2;
GeAsLe(x, y) == (x >= y) = (y <= x)

\* @weight: 8;
NegAsMinus(x) == (-x) = (0 - x)

\* @weight: 8;
DoubleAsTimes(x) == (x + x) = (2 * x)

\* @weight: 8;
MinusMinus(x, y, z) == ((x - y) - z) = (x - (y + z))

DivModPos(x, y) ==
    x = (((x \div ((y * y) + 1)) * ((y * y) + 1)) + (x % ((y * y) + 1)))

\* Arithmetic decompositions by a positive divisor: (y * y) + 1, or a literal.
\* A checker that truncates instead of flooring keeps DivSplit and DivMod*
\* consistent; ModTwoFloor and ModInRange pin the remainder to 0 .. K - 1.
DivSplit(x, y) == x = ((x \div ((y * y) + 1)) + (x - (x \div ((y * y) + 1))))

DivSplitTwo(x) == x = ((x \div 2) + (x - (x \div 2)))

DivModTwo(x) == x = ((2 * (x \div 2)) + (x % 2))

DivModThree(x) == x = ((3 * (x \div 3)) + (x % 3))

ModTwoFloor(x) == x = ((2 * (x \div 2)) + (IF (x % 2) = 0 THEN 0 ELSE 1))

ModInRange(x, y) == x = (IF (x % ((y * y) + 1)) \in 0 .. (y * y) THEN x ELSE x + 1)

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

\* @weight: 2;
AndAssoc(P, Q, R) == ((P /\ Q) /\ R) = (P /\ (Q /\ R))

\* @weight: 2;
OrAssoc(P, Q, R) == ((P \/ Q) \/ R) = (P \/ (Q \/ R))

\* Only a Boolean IF: in an action, Q and R keep their assignments.
\* @weight: 8;
IfAsAndOr(P, Q, R) == (IF P THEN Q ELSE R) = ((P /\ Q) \/ (~P /\ R))

\* @weight: 8;
EquivAsEq(P, Q) == (P <=> Q) = (P = Q)

BoolAsIf(P) == P = (IF P THEN TRUE ELSE FALSE)

\* @weight: 8;
ImpliesCurry(P, Q, R) == ((P /\ Q) => R) = (P => (Q => R))

\* @weight: 8;
ExistsBool(P(_)) == (\E e \in BOOLEAN : P(e)) = (P(TRUE) \/ P(FALSE))

\* Every type
\* @weight: 8;
NeqAsNotEq(x, y) == (x # y) = ~(x = y)

\* @weight: 2;
EqSym(x, y) == (x = y) = (y = x)

\* @weight: 8;
IfNegate(P, x, y) == (IF P THEN x ELSE y) = (IF ~P THEN y ELSE x)

IfTrue(x, y) == x = (IF TRUE THEN x ELSE y)

\* @weight: 8;
IfAsCase(P, x, y) == (IF P THEN x ELSE y) = (CASE P -> x [] OTHER -> y)

\* @weight: 8;
IfAsCaseNeg(P, x, y) == (IF P THEN x ELSE y) = (CASE P -> x [] ~P -> y)

\* @weight: 8;
IfLiftEq(P, x, y, z) == ((IF P THEN x ELSE y) = z) = (IF P THEN x = z ELSE y = z)

\* @weight: 8;
EqAsIn(x, y) == (x = y) = (x \in {y})

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

\* @weight: 8;
ExistsAsFilter(S, P(_)) == (\E e \in S : P(e)) = ({e \in S : P(e)} # {})

\* @weight: 8;
ForallAsFilter(S, P(_)) == (\A e \in S : P(e)) = ({e \in S : P(e)} = S)

\* @weight: 8;
ExistsOr(S, P(_), Q(_)) ==
    (\E e \in S : P(e) \/ Q(e)) = ((\E e \in S : P(e)) \/ (\E e \in S : Q(e)))

\* @weight: 2;
ForallSwap(S, T, P(_, _)) ==
    (\A e \in S : \A d \in T : P(e, d)) = (\A d \in T : \A e \in S : P(e, d))

\* @weight: 2;
ExistsSwap(S, T, P(_, _)) ==
    (\E e \in S : \E d \in T : P(e, d)) = (\E d \in T : \E e \in S : P(e, d))

\* Not \A <<e, d>> \in S \X T: Apalache's PrettyWriter prints a tuple binder as a name.
\* @weight: 8;
ForallPair(S, T, P(_, _)) ==
    (\A e \in S : \A d \in T : P(e, d)) = (\A t \in S \X T : P(t[1], t[2]))

\* @weight: 8;
ExistsPair(S, T, P(_, _)) ==
    (\E e \in S : \E d \in T : P(e, d)) = (\E t \in S \X T : P(t[1], t[2]))

\* @weight: 8;
ExistsFilter(S, P(_), Q(_)) ==
    (\E e \in {d \in S : P(d)} : Q(e)) = (\E e \in S : P(e) /\ Q(e))

\* @weight: 8;
ForallFilter(S, P(_), Q(_)) ==
    (\A e \in {d \in S : P(d)} : Q(e)) = (\A e \in S : P(e) => Q(e))

\* @weight: 8;
ExistsMap(S, F(_), P(_)) == (\E e \in {F(d) : d \in S} : P(e)) = (\E d \in S : P(F(d)))

\* @weight: 8;
MapMap(S, F(_), G(_)) == {G(e) : e \in {F(d) : d \in S}} = {G(F(d)) : d \in S}

\* @weight: 8;
MapAsUnion(S, F(_)) == {F(e) : e \in S} = UNION {{F(e)} : e \in S}

\* @weight: 8;
FilterAsUnion(S, P(_)) == {e \in S : P(e)} = UNION {IF P(e) THEN {e} ELSE {} : e \in S}

\* @weight: 8;
InFilter(x, S, P(_)) == (x \in {e \in S : P(e)}) = (x \in S /\ P(x))

\* @weight: 8;
InMap(x, S, F(_)) == (x \in {F(e) : e \in S}) = (\E e \in S : F(e) = x)

\* @weight: 8;
InAsExists(x, S) == (x \in S) = (\E e \in S : x = e)

\* @weight: 8;
InRange(x, a, b) == (x \in a .. b) = (a <= x /\ x <= b)

\* @weight: 8;
UnionAsBigUnion(S, T) == (S \union T) = UNION {S, T}

\* @weight: 8;
SubsetAsPowerset(S, T) == (S \subseteq T) = (S \in SUBSET T)

\* @weight: 8;
InPowersetAsSubset(S, T) == (S \in SUBSET T) = (S \subseteq T)

\* @weight: 8;
SetEqAsSubset(S, T) == (S = T) = (S \subseteq T /\ T \subseteq S)

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

\* @type: (a -> b, a, b) => Bool;
\* @weight: 8;
ExceptAsCtor(f, x, y) ==
    [f EXCEPT ![x] = y] = [e \in DOMAIN f |-> IF e = x THEN y ELSE f[e]]

\* @type: (a -> b, a, b) => Bool;
\* @weight: 8;
DomainExcept(f, x, y) == (DOMAIN [f EXCEPT ![x] = y]) = DOMAIN f

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

TailCons(s, x) == s = Tail(<<x>> \o s)

\* @weight: 8;
InSeqSet(s, S) == (s \in Seq(S)) = (\A i \in 1 .. Len(s) : s[i] \in S)

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

\* Definitions and application: beta and iota redexes
LetName(x) == x = (LET y == x IN y)

LetApply(x) == x = (LET F(v) == v IN F(x))

LetConst(x, y) == x = (LET F(v) == x IN F(y))

LetHigher(x) == x = (LET Ap(G(_), v) == G(v) IN Ap(LAMBDA d : d, x))

\* @weight: 8;
ExistsViaLet(S, P(_)) == (\E e \in S : P(e)) = (LET Q(d) == P(d) IN \E e \in S : Q(e))

\* @weight: 8;
ForallViaLet(S, P(_)) == (\A e \in S : P(e)) = (LET Q(d) == P(d) IN \A e \in S : Q(e))

\* @weight: 8;
FilterViaLet(S, P(_)) == {e \in S : P(e)} = (LET Q(d) == P(d) IN {e \in S : Q(e)})

\* @weight: 8;
MapViaLet(S, F(_)) == {F(e) : e \in S} = (LET G(d) == F(d) IN {G(e) : e \in S})

\* @weight: 8;
FunViaLet(S, F(_)) == [e \in S |-> F(e)] = (LET G(d) == F(d) IN [e \in S |-> G(e)])

\* @weight: 8;
FilterViaLambda(S, P(_)) ==
    {e \in S : P(e)} = (LET Keep(T, G(_)) == {d \in T : G(d)} IN Keep(S, LAMBDA c : P(c)))

\* @weight: 8;
ExistsViaLambda(S, P(_)) ==
    (\E e \in S : P(e)) = (LET Some(T, G(_)) == \E d \in T : G(d) IN Some(S, LAMBDA c : P(c)))

\* @weight: 8;
FunBeta(S, F(_), x) ==
    ([e \in S |-> F(e)][x]) = (IF x \in S THEN F(x) ELSE [e \in S |-> F(e)][x])

\* @type: (a) => Bool;
RecordProj(x) == x = [field0 |-> x].field0

\* Snowcat cannot tell a tuple literal from a sequence: Pair annotates it.
TupleProj(x, y) ==
    x = (LET \* @type: (a, b) => <<a, b>>;
             Pair(u, w) == <<u, w>>
         IN Pair(x, y)[1])

SeqHead(x) == x = Head(<<x>>)

FunApplyCtor(x) == x = [e \in {x} |-> e][x]

FunApplyConst(x, y) == x = [e \in {y} |-> x][y]

\* Apalache picks a CHOOSE witness non-deterministically: metamorphic-defects.toml
\* quarantines every CHOOSE.
ChooseSingleton(x) == x = (CHOOSE e \in {x} : TRUE)

CaseTrue(x, y) == x = (CASE TRUE -> x [] OTHER -> y)
=============================================================================
