--------------------------- MODULE RewritesProofs ---------------------------
(***************************************************************************)
(* The proofs of libraries/rewrites (ADR 0017 §7): every rule R of         *)
(* Rewrites.tla is valid, stated as THEOREM RValid. The hypotheses follow  *)
(* from the rule's Snowcat signature:                                      *)
(*                                                                         *)
(*   Int                   NEW x \in Int                                   *)
(*   Bool                  NEW P \in BOOLEAN                               *)
(*   a type variable       NEW x                                           *)
(*   Set(a)                NEW S                                           *)
(*   a -> b                NEW D, NEW R, NEW f \in [D -> R]                *)
(*   Seq(a)                NEW A, NEW s \in Seq(A)                         *)
(*   <<a, b>>              NEW A, NEW B, NEW t \in A \X B                  *)
(*   { field0: a, z }      NEW D, NEW V, NEW r \in [D -> V],             *)
(*                         "field0" \in D                                 *)
(*   (a) => Bool           NEW P(_), \A e \in S : P(e) \in BOOLEAN         *)
(*                         for the set S that P ranges over                *)
(*   (a, b) => Bool        NEW P(_, _),                                    *)
(*                         \A e \in S, d \in T : P(e, d) \in BOOLEAN       *)
(*   a primed parameter    NEW VARIABLE x                                  *)
(*                                                                         *)
(* Checked with TLAPM b064bce: all 232 obligations proved.                 *)
(*                                                                         *)
(*   make proofs                                                           *)
(***************************************************************************)
EXTENDS Rewrites, SequenceTheorems, TLAPS

THEOREM PlusZeroValid == ASSUME NEW x \in Int PROVE PlusZero(x)
  BY DEF PlusZero

THEOREM AddSubValid == ASSUME NEW x \in Int, NEW y \in Int PROVE AddSub(x, y)
  BY DEF AddSub

THEOREM DoubleNegValid == ASSUME NEW P \in BOOLEAN PROVE DoubleNeg(P)
  BY DEF DoubleNeg

THEOREM UnionSelfValid == ASSUME NEW S PROVE UnionSelf(S)
  BY DEF UnionSelf

THEOREM UnchangedPrimeValid == ASSUME NEW VARIABLE x PROVE UnchangedPrime(x)
  BY DEF UnchangedPrime

THEOREM ForallNotExistsValid ==
  ASSUME NEW S, NEW P(_), \A e \in S : P(e) \in BOOLEAN PROVE ForallNotExists(S, P)
  BY DEF ForallNotExists

\* Integers
THEOREM TimesOneValid == ASSUME NEW x \in Int PROVE TimesOne(x)
  BY DEF TimesOne

THEOREM NegNegValid == ASSUME NEW x \in Int PROVE NegNeg(x)
  BY DEF NegNeg

THEOREM PlusCommValid == ASSUME NEW x \in Int, NEW y \in Int PROVE PlusComm(x, y)
  BY DEF PlusComm

THEOREM TimesCommValid == ASSUME NEW x \in Int, NEW y \in Int PROVE TimesComm(x, y)
  BY DEF TimesComm

THEOREM PlusAssocValid ==
  ASSUME NEW x \in Int, NEW y \in Int, NEW z \in Int PROVE PlusAssoc(x, y, z)
  BY DEF PlusAssoc

THEOREM MinusAsPlusNegValid ==
  ASSUME NEW x \in Int, NEW y \in Int PROVE MinusAsPlusNeg(x, y)
  BY DEF MinusAsPlusNeg

THEOREM TimesDistribValid ==
  ASSUME NEW x \in Int, NEW y \in Int, NEW z \in Int PROVE TimesDistrib(x, y, z)
  BY DEF TimesDistrib

THEOREM LtAsNotGeValid == ASSUME NEW x \in Int, NEW y \in Int PROVE LtAsNotGe(x, y)
  BY DEF LtAsNotGe

THEOREM LeAsLtOrEqValid == ASSUME NEW x \in Int, NEW y \in Int PROVE LeAsLtOrEq(x, y)
  BY DEF LeAsLtOrEq

THEOREM GtAsLtValid == ASSUME NEW x \in Int, NEW y \in Int PROVE GtAsLt(x, y)
  BY DEF GtAsLt

THEOREM GeAsLeValid == ASSUME NEW x \in Int, NEW y \in Int PROVE GeAsLe(x, y)
  BY DEF GeAsLe

THEOREM NegAsMinusValid == ASSUME NEW x \in Int PROVE NegAsMinus(x)
  BY DEF NegAsMinus

THEOREM DoubleAsTimesValid == ASSUME NEW x \in Int PROVE DoubleAsTimes(x)
  BY DEF DoubleAsTimes

THEOREM MinusMinusValid ==
  ASSUME NEW x \in Int, NEW y \in Int, NEW z \in Int PROVE MinusMinus(x, y, z)
  BY DEF MinusMinus

\* Z3 proves the remainder's range, and x % n = x - n * (x \div n), but not the
\* decomposition itself; the arithmetic decompositions below go through this lemma.
LEMMA DivMod ==
  ASSUME NEW x \in Int, NEW n \in Int, n > 0
  PROVE  /\ x \div n \in Int
         /\ x % n \in 0 .. n - 1
         /\ x = (n * (x \div n)) + (x % n)
  <1>1. x \div n \in Int /\ x % n \in 0 .. n - 1
    BY Z3
  <1>2. x % n = x - n * (x \div n)
    BY Z3
  <1>3. QED BY <1>1, <1>2, Z3

LEMMA SquarePlusOne == ASSUME NEW y \in Int PROVE (y * y) + 1 \in Int /\ (y * y) + 1 > 0
  BY SMT

THEOREM DivModPosValid == ASSUME NEW x \in Int, NEW y \in Int PROVE DivModPos(x, y)
  <1>1. x = ((y * y) + 1) * (x \div ((y * y) + 1)) + (x % ((y * y) + 1))
    BY SquarePlusOne, DivMod
  <1>2. QED BY <1>1, Z3 DEF DivModPos

THEOREM DivSplitValid == ASSUME NEW x \in Int, NEW y \in Int PROVE DivSplit(x, y)
  <1>1. x \div ((y * y) + 1) \in Int
    BY SquarePlusOne, DivMod
  <1>2. QED BY <1>1, Z3 DEF DivSplit

THEOREM DivSplitTwoValid == ASSUME NEW x \in Int PROVE DivSplitTwo(x)
  <1>1. x \div 2 \in Int
    BY DivMod
  <1>2. QED BY <1>1, Z3 DEF DivSplitTwo

THEOREM DivModTwoValid == ASSUME NEW x \in Int PROVE DivModTwo(x)
  BY DivMod DEF DivModTwo

THEOREM DivModThreeValid == ASSUME NEW x \in Int PROVE DivModThree(x)
  BY DivMod DEF DivModThree

THEOREM ModTwoFloorValid == ASSUME NEW x \in Int PROVE ModTwoFloor(x)
  <1>1. x % 2 \in 0 .. 1 /\ x = (2 * (x \div 2)) + (x % 2)
    BY DivMod
  <1>2. (IF (x % 2) = 0 THEN 0 ELSE 1) = x % 2
    BY <1>1
  <1>3. QED BY <1>1, <1>2 DEF ModTwoFloor

THEOREM ModInRangeValid == ASSUME NEW x \in Int, NEW y \in Int PROVE ModInRange(x, y)
  <1>1. x % ((y * y) + 1) \in 0 .. ((y * y) + 1) - 1
    BY SquarePlusOne, DivMod
  <1>2. ((y * y) + 1) - 1 = y * y
    BY SMT
  <1>3. QED BY <1>1, <1>2 DEF ModInRange

\* Booleans
THEOREM AndTrueValid == ASSUME NEW P \in BOOLEAN PROVE AndTrue(P)
  BY DEF AndTrue

THEOREM OrFalseValid == ASSUME NEW P \in BOOLEAN PROVE OrFalse(P)
  BY DEF OrFalse

THEOREM AndCommValid == ASSUME NEW P \in BOOLEAN, NEW Q \in BOOLEAN PROVE AndComm(P, Q)
  BY DEF AndComm

THEOREM OrCommValid == ASSUME NEW P \in BOOLEAN, NEW Q \in BOOLEAN PROVE OrComm(P, Q)
  BY DEF OrComm

THEOREM DeMorganAndValid ==
  ASSUME NEW P \in BOOLEAN, NEW Q \in BOOLEAN PROVE DeMorganAnd(P, Q)
  BY DEF DeMorganAnd

THEOREM DeMorganOrValid ==
  ASSUME NEW P \in BOOLEAN, NEW Q \in BOOLEAN PROVE DeMorganOr(P, Q)
  BY DEF DeMorganOr

THEOREM ImpliesAsOrValid ==
  ASSUME NEW P \in BOOLEAN, NEW Q \in BOOLEAN PROVE ImpliesAsOr(P, Q)
  BY DEF ImpliesAsOr

THEOREM EquivAsImpliesValid ==
  ASSUME NEW P \in BOOLEAN, NEW Q \in BOOLEAN PROVE EquivAsImplies(P, Q)
  BY DEF EquivAsImplies

THEOREM AndAssocValid ==
  ASSUME NEW P \in BOOLEAN, NEW Q \in BOOLEAN, NEW R \in BOOLEAN PROVE AndAssoc(P, Q, R)
  BY DEF AndAssoc

THEOREM OrAssocValid ==
  ASSUME NEW P \in BOOLEAN, NEW Q \in BOOLEAN, NEW R \in BOOLEAN PROVE OrAssoc(P, Q, R)
  BY DEF OrAssoc

THEOREM IfAsAndOrValid ==
  ASSUME NEW P \in BOOLEAN, NEW Q \in BOOLEAN, NEW R \in BOOLEAN PROVE IfAsAndOr(P, Q, R)
  BY DEF IfAsAndOr

THEOREM EquivAsEqValid == ASSUME NEW P \in BOOLEAN, NEW Q \in BOOLEAN PROVE EquivAsEq(P, Q)
  BY DEF EquivAsEq

THEOREM BoolAsIfValid == ASSUME NEW P \in BOOLEAN PROVE BoolAsIf(P)
  BY DEF BoolAsIf

THEOREM ImpliesCurryValid ==
  ASSUME NEW P \in BOOLEAN, NEW Q \in BOOLEAN, NEW R \in BOOLEAN PROVE ImpliesCurry(P, Q, R)
  BY DEF ImpliesCurry

THEOREM ExistsBoolValid ==
  ASSUME NEW P(_), \A e \in BOOLEAN : P(e) \in BOOLEAN PROVE ExistsBool(P)
  BY DEF ExistsBool

\* Every type
THEOREM NeqAsNotEqValid == ASSUME NEW x, NEW y PROVE NeqAsNotEq(x, y)
  BY DEF NeqAsNotEq

THEOREM EqSymValid == ASSUME NEW x, NEW y PROVE EqSym(x, y)
  BY DEF EqSym

THEOREM IfNegateValid == ASSUME NEW P \in BOOLEAN, NEW x, NEW y PROVE IfNegate(P, x, y)
  BY DEF IfNegate

THEOREM IfTrueValid == ASSUME NEW x, NEW y PROVE IfTrue(x, y)
  BY DEF IfTrue

THEOREM IfAsCaseValid == ASSUME NEW P \in BOOLEAN, NEW x, NEW y PROVE IfAsCase(P, x, y)
  BY DEF IfAsCase

THEOREM IfAsCaseNegValid == ASSUME NEW P \in BOOLEAN, NEW x, NEW y PROVE IfAsCaseNeg(P, x, y)
  BY DEF IfAsCaseNeg

THEOREM IfLiftEqValid ==
  ASSUME NEW P \in BOOLEAN, NEW x, NEW y, NEW z PROVE IfLiftEq(P, x, y, z)
  BY DEF IfLiftEq

THEOREM EqAsInValid == ASSUME NEW x, NEW y PROVE EqAsIn(x, y)
  BY DEF EqAsIn

\* Sets and quantifiers
THEOREM UnionEmptyValid == ASSUME NEW S PROVE UnionEmpty(S)
  BY DEF UnionEmpty

THEOREM IntersectSelfValid == ASSUME NEW S PROVE IntersectSelf(S)
  BY DEF IntersectSelf

THEOREM MinusEmptyValid == ASSUME NEW S PROVE MinusEmpty(S)
  BY DEF MinusEmpty

THEOREM UnionCommValid == ASSUME NEW S, NEW T PROVE UnionComm(S, T)
  BY DEF UnionComm

THEOREM IntersectCommValid == ASSUME NEW S, NEW T PROVE IntersectComm(S, T)
  BY DEF IntersectComm

THEOREM MinusAsFilterValid == ASSUME NEW S, NEW T PROVE MinusAsFilter(S, T)
  BY DEF MinusAsFilter

THEOREM IntersectAsFilterValid == ASSUME NEW S, NEW T PROVE IntersectAsFilter(S, T)
  BY DEF IntersectAsFilter

THEOREM InUnionValid == ASSUME NEW x, NEW S, NEW T PROVE InUnion(x, S, T)
  BY DEF InUnion

THEOREM InIntersectValid == ASSUME NEW x, NEW S, NEW T PROVE InIntersect(x, S, T)
  BY DEF InIntersect

THEOREM NotInAsNotInValid == ASSUME NEW x, NEW S PROVE NotInAsNotIn(x, S)
  BY DEF NotInAsNotIn

THEOREM SubsetAsForallValid == ASSUME NEW S, NEW T PROVE SubsetAsForall(S, T)
  BY DEF SubsetAsForall

THEOREM MapIdentityValid == ASSUME NEW S PROVE MapIdentity(S)
  BY DEF MapIdentity

THEOREM FilterTrueValid == ASSUME NEW S PROVE FilterTrue(S)
  BY DEF FilterTrue

THEOREM FilterFilterValid ==
  ASSUME NEW S, NEW P(_), NEW Q(_),
         \A e \in S : P(e) \in BOOLEAN, \A e \in S : Q(e) \in BOOLEAN
  PROVE  FilterFilter(S, P, Q)
  BY DEF FilterFilter

THEOREM ExistsNotForallValid ==
  ASSUME NEW S, NEW P(_), \A e \in S : P(e) \in BOOLEAN PROVE ExistsNotForall(S, P)
  BY DEF ExistsNotForall

THEOREM ExistsUnionValid ==
  ASSUME NEW S, NEW T, NEW P(_), \A e \in S \union T : P(e) \in BOOLEAN
  PROVE  ExistsUnion(S, T, P)
  BY DEF ExistsUnion

THEOREM ForallAndValid ==
  ASSUME NEW S, NEW P(_), NEW Q(_),
         \A e \in S : P(e) \in BOOLEAN, \A e \in S : Q(e) \in BOOLEAN
  PROVE  ForallAnd(S, P, Q)
  BY DEF ForallAnd

THEOREM ExistsAsFilterValid ==
  ASSUME NEW S, NEW P(_), \A e \in S : P(e) \in BOOLEAN PROVE ExistsAsFilter(S, P)
  BY DEF ExistsAsFilter

THEOREM ForallAsFilterValid ==
  ASSUME NEW S, NEW P(_), \A e \in S : P(e) \in BOOLEAN PROVE ForallAsFilter(S, P)
  BY DEF ForallAsFilter

THEOREM ExistsOrValid ==
  ASSUME NEW S, NEW P(_), NEW Q(_),
         \A e \in S : P(e) \in BOOLEAN, \A e \in S : Q(e) \in BOOLEAN
  PROVE  ExistsOr(S, P, Q)
  BY DEF ExistsOr

THEOREM ForallSwapValid ==
  ASSUME NEW S, NEW T, NEW P(_, _), \A e \in S, d \in T : P(e, d) \in BOOLEAN
  PROVE  ForallSwap(S, T, P)
  BY DEF ForallSwap

THEOREM ExistsSwapValid ==
  ASSUME NEW S, NEW T, NEW P(_, _), \A e \in S, d \in T : P(e, d) \in BOOLEAN
  PROVE  ExistsSwap(S, T, P)
  BY DEF ExistsSwap

THEOREM ForallPairValid ==
  ASSUME NEW S, NEW T, NEW P(_, _), \A e \in S, d \in T : P(e, d) \in BOOLEAN
  PROVE  ForallPair(S, T, P)
  <1>1. ASSUME \A e \in S : \A d \in T : P(e, d) PROVE \A t \in S \X T : P(t[1], t[2])
    BY <1>1
  <1>2. ASSUME \A t \in S \X T : P(t[1], t[2]) PROVE \A e \in S : \A d \in T : P(e, d)
    <2> SUFFICES ASSUME NEW e \in S, NEW d \in T PROVE P(e, d)
      OBVIOUS
    <2>1. <<e, d>> \in S \X T /\ <<e, d>>[1] = e /\ <<e, d>>[2] = d
      OBVIOUS
    <2>2. QED BY <1>2, <2>1
  <1>3. QED BY <1>1, <1>2 DEF ForallPair

THEOREM ExistsPairValid ==
  ASSUME NEW S, NEW T, NEW P(_, _), \A e \in S, d \in T : P(e, d) \in BOOLEAN
  PROVE  ExistsPair(S, T, P)
  <1>1. ASSUME NEW e \in S, NEW d \in T, P(e, d) PROVE \E t \in S \X T : P(t[1], t[2])
    <2>1. <<e, d>> \in S \X T /\ <<e, d>>[1] = e /\ <<e, d>>[2] = d
      OBVIOUS
    <2>2. QED BY <1>1, <2>1
  <1>2. ASSUME NEW t \in S \X T, P(t[1], t[2]) PROVE \E e \in S : \E d \in T : P(e, d)
    BY <1>2
  <1>3. QED BY <1>1, <1>2 DEF ExistsPair

THEOREM ExistsFilterValid ==
  ASSUME NEW S, NEW P(_), NEW Q(_),
         \A e \in S : P(e) \in BOOLEAN, \A e \in S : Q(e) \in BOOLEAN
  PROVE  ExistsFilter(S, P, Q)
  BY DEF ExistsFilter

THEOREM ForallFilterValid ==
  ASSUME NEW S, NEW P(_), NEW Q(_),
         \A e \in S : P(e) \in BOOLEAN, \A e \in S : Q(e) \in BOOLEAN
  PROVE  ForallFilter(S, P, Q)
  BY DEF ForallFilter

THEOREM ExistsMapValid ==
  ASSUME NEW S, NEW F(_), NEW P(_), \A d \in S : P(F(d)) \in BOOLEAN PROVE ExistsMap(S, F, P)
  BY DEF ExistsMap

THEOREM MapMapValid == ASSUME NEW S, NEW F(_), NEW G(_) PROVE MapMap(S, F, G)
  BY DEF MapMap

THEOREM MapAsUnionValid == ASSUME NEW S, NEW F(_) PROVE MapAsUnion(S, F)
  BY DEF MapAsUnion

THEOREM FilterAsUnionValid ==
  ASSUME NEW S, NEW P(_), \A e \in S : P(e) \in BOOLEAN PROVE FilterAsUnion(S, P)
  BY DEF FilterAsUnion

THEOREM InFilterValid ==
  ASSUME NEW x, NEW S, NEW P(_), \A e \in S : P(e) \in BOOLEAN PROVE InFilter(x, S, P)
  BY DEF InFilter

THEOREM InMapValid == ASSUME NEW x, NEW S, NEW F(_) PROVE InMap(x, S, F)
  BY DEF InMap

THEOREM InAsExistsValid == ASSUME NEW x, NEW S PROVE InAsExists(x, S)
  BY DEF InAsExists

THEOREM InRangeValid == ASSUME NEW x \in Int, NEW a \in Int, NEW b \in Int PROVE InRange(x, a, b)
  BY DEF InRange

THEOREM UnionAsBigUnionValid == ASSUME NEW S, NEW T PROVE UnionAsBigUnion(S, T)
  BY DEF UnionAsBigUnion

THEOREM SubsetAsPowersetValid == ASSUME NEW S, NEW T PROVE SubsetAsPowerset(S, T)
  BY DEF SubsetAsPowerset

THEOREM InPowersetAsSubsetValid == ASSUME NEW S, NEW T PROVE InPowersetAsSubset(S, T)
  BY DEF InPowersetAsSubset

THEOREM SetEqAsSubsetValid == ASSUME NEW S, NEW T PROVE SetEqAsSubset(S, T)
  BY DEF SetEqAsSubset

\* Functions
THEOREM FunEtaValid == ASSUME NEW D, NEW R, NEW f \in [D -> R] PROVE FunEta(f)
  BY DEF FunEta

THEOREM DomainOfCtorValid == ASSUME NEW S, NEW F(_) PROVE DomainOfCtor(S, F)
  BY DEF DomainOfCtor

THEOREM ExceptTwiceValid ==
  ASSUME NEW D, NEW R, NEW f \in [D -> R], NEW x, NEW y, NEW z PROVE ExceptTwice(f, x, y, z)
  BY DEF ExceptTwice

THEOREM InFunSetValid ==
  ASSUME NEW D, NEW R, NEW f \in [D -> R], NEW S, NEW T PROVE InFunSet(f, S, T)
  BY DEF InFunSet

THEOREM ExceptAsCtorValid ==
  ASSUME NEW D, NEW R, NEW f \in [D -> R], NEW x, NEW y PROVE ExceptAsCtor(f, x, y)
  BY DEF ExceptAsCtor

THEOREM DomainExceptValid ==
  ASSUME NEW D, NEW R, NEW f \in [D -> R], NEW x, NEW y PROVE DomainExcept(f, x, y)
  BY DEF DomainExcept

\* Sequences
THEOREM ConcatEmptyValid == ASSUME NEW A, NEW s \in Seq(A) PROVE ConcatEmpty(s)
  BY ConcatEmptySeq DEF ConcatEmpty

THEOREM EmptyConcatValid == ASSUME NEW A, NEW s \in Seq(A) PROVE EmptyConcat(s)
  BY ConcatEmptySeq DEF EmptyConcat

THEOREM ConcatAssocValid ==
  ASSUME NEW A, NEW s \in Seq(A), NEW t \in Seq(A), NEW u \in Seq(A) PROVE ConcatAssoc(s, t, u)
  BY ConcatAssociative DEF ConcatAssoc

THEOREM AppendAsConcatValid ==
  ASSUME NEW A, NEW s \in Seq(A), NEW x \in A PROVE AppendAsConcat(s, x)
  BY AppendIsConcat DEF AppendAsConcat

THEOREM LenConcatValid ==
  ASSUME NEW A, NEW s \in Seq(A), NEW t \in Seq(A) PROVE LenConcat(s, t)
  BY ConcatProperties DEF LenConcat

THEOREM LenAppendValid == ASSUME NEW A, NEW s \in Seq(A), NEW x \in A PROVE LenAppend(s, x)
  BY AppendProperties DEF LenAppend

THEOREM SubSeqWholeValid == ASSUME NEW A, NEW s \in Seq(A) PROVE SubSeqWhole(s)
  <1>1. /\ SubSeq(s, 1, Len(s)) \in Seq(A)
        /\ Len(SubSeq(s, 1, Len(s))) = Len(s)
        /\ \A i \in 1 .. Len(s) : SubSeq(s, 1, Len(s))[i] = s[i]
    BY SubSeqProperties, LenProperties
  <1>2. QED BY <1>1, SeqEqual DEF SubSeqWhole

THEOREM TailConsValid == ASSUME NEW A, NEW s \in Seq(A), NEW x \in A PROVE TailCons(s, x)
  <1> DEFINE c == <<x>> \o s
  <1>1. <<x>> \in Seq(A) /\ Len(<<x>>) = 1
    BY AppendIsConcat, AppendProperties, EmptySeq, ConcatEmptySeq
  <1>2. /\ c \in Seq(A)
        /\ Len(c) = 1 + Len(s)
        /\ \A i \in 1 .. 1 + Len(s) : c[i] = IF i <= 1 THEN x ELSE s[i - 1]
    BY <1>1, ConcatProperties
  <1>3. c # << >>
    BY <1>2, LenProperties, EmptySeq
  <1>4. /\ Tail(c) \in Seq(A)
        /\ Len(Tail(c)) = Len(s)
        /\ \A i \in 1 .. Len(s) : Tail(c)[i] = s[i]
    BY <1>2, <1>3, HeadTailProperties, LenProperties
  <1>5. QED BY <1>4, SeqEqual DEF TailCons

THEOREM InSeqSetValid == ASSUME NEW A, NEW s \in Seq(A), NEW S PROVE InSeqSet(s, S)
  <1>1. /\ Len(s) \in Nat
        /\ s \in [1 .. Len(s) -> A]
    BY LenProperties
  <1>2. ASSUME s \in Seq(S) PROVE \A i \in 1 .. Len(s) : s[i] \in S
    BY <1>2, ElementOfSeq
  <1>3. ASSUME \A i \in 1 .. Len(s) : s[i] \in S PROVE s \in Seq(S)
    <2>1. s \in [1 .. Len(s) -> S]
      BY <1>1, <1>3
    <2>2. QED BY <1>1, <2>1, SeqDef
  <1>4. QED BY <1>2, <1>3 DEF InSeqSet

\* Tuples
THEOREM PairEtaValid == ASSUME NEW A, NEW B, NEW t \in A \X B PROVE PairEta(t)
  BY DEF PairEta

THEOREM PairEqAsAndValid ==
  ASSUME NEW A, NEW B, NEW s \in A \X B, NEW t \in A \X B PROVE PairEqAsAnd(s, t)
  BY DEF PairEqAsAnd

THEOREM PairInProductValid ==
  ASSUME NEW A, NEW B, NEW t \in A \X B, NEW S, NEW T PROVE PairInProduct(t, S, T)
  BY DEF PairInProduct

\* Records with the field field0
THEOREM RecordExceptSameValid ==
  ASSUME NEW D, NEW V, NEW r \in [D -> V], "field0" \in D PROVE RecordExceptSame(r)
  BY DEF RecordExceptSame

THEOREM RecordExceptTwiceValid ==
  ASSUME NEW D, NEW V, NEW r \in [D -> V], "field0" \in D, NEW x, NEW y
  PROVE  RecordExceptTwice(r, x, y)
  BY DEF RecordExceptTwice

THEOREM RecordExceptGetValid ==
  ASSUME NEW D, NEW V, NEW r \in [D -> V], "field0" \in D, NEW x PROVE RecordExceptGet(r, x)
  BY DEF RecordExceptGet

\* Variants with the tag Tag0
THEOREM VariantTagOfValid == ASSUME NEW x PROVE VariantTagOf(x)
  BY DEF VariantTagOf, VariantTag, Variant

THEOREM VariantGetOfValid == ASSUME NEW x, NEW d PROVE VariantGetOf(x, d)
  BY DEF VariantGetOf, VariantGetOrElse, Variant

\* Definitions and application
THEOREM LetNameValid == ASSUME NEW x PROVE LetName(x)
  BY DEF LetName

THEOREM LetApplyValid == ASSUME NEW x PROVE LetApply(x)
  BY DEF LetApply

THEOREM LetConstValid == ASSUME NEW x, NEW y PROVE LetConst(x, y)
  BY DEF LetConst

THEOREM LetHigherValid == ASSUME NEW x PROVE LetHigher(x)
  BY DEF LetHigher

THEOREM ExistsViaLetValid == ASSUME NEW S, NEW P(_) PROVE ExistsViaLet(S, P)
  BY DEF ExistsViaLet

THEOREM ForallViaLetValid == ASSUME NEW S, NEW P(_) PROVE ForallViaLet(S, P)
  BY DEF ForallViaLet

THEOREM FilterViaLetValid == ASSUME NEW S, NEW P(_) PROVE FilterViaLet(S, P)
  BY DEF FilterViaLet

THEOREM MapViaLetValid == ASSUME NEW S, NEW F(_) PROVE MapViaLet(S, F)
  BY DEF MapViaLet

THEOREM FunViaLetValid == ASSUME NEW S, NEW F(_) PROVE FunViaLet(S, F)
  BY DEF FunViaLet

THEOREM FilterViaLambdaValid == ASSUME NEW S, NEW P(_) PROVE FilterViaLambda(S, P)
  BY DEF FilterViaLambda

THEOREM ExistsViaLambdaValid == ASSUME NEW S, NEW P(_) PROVE ExistsViaLambda(S, P)
  BY DEF ExistsViaLambda

THEOREM FunBetaValid == ASSUME NEW S, NEW F(_), NEW x PROVE FunBeta(S, F, x)
  BY DEF FunBeta

THEOREM RecordProjValid == ASSUME NEW x PROVE RecordProj(x)
  BY DEF RecordProj

THEOREM TupleProjValid == ASSUME NEW x, NEW y PROVE TupleProj(x, y)
  BY DEF TupleProj

THEOREM SeqHeadValid == ASSUME NEW x PROVE SeqHead(x)
  BY DEF SeqHead

THEOREM FunApplyCtorValid == ASSUME NEW x PROVE FunApplyCtor(x)
  BY DEF FunApplyCtor

THEOREM FunApplyConstValid == ASSUME NEW x, NEW y PROVE FunApplyConst(x, y)
  BY DEF FunApplyConst

THEOREM ChooseSingletonValid == ASSUME NEW x PROVE ChooseSingleton(x)
  <1>1. \E e \in {x} : TRUE
    OBVIOUS
  <1>2. QED BY <1>1 DEF ChooseSingleton

THEOREM CaseTrueValid == ASSUME NEW x, NEW y PROVE CaseTrue(x, y)
  BY DEF CaseTrue
=============================================================================
