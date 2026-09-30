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
(*   a primed parameter    NEW VARIABLE x                                  *)
(*                                                                         *)
(* Checked with TLAPM b064bce: all 6 obligations proved.                   *)
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
=============================================================================
