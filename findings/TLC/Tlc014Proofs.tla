---- MODULE Tlc014Proofs ----
(*****************************************************************************)
(* Proofs for tlc-014.md: every property shape that TLC reports as violated  *)
(* there is valid. Corpus and Row6 need no specification -- they are         *)
(* propositional-temporal tautologies. Inert states the rewriting TLC gets   *)
(* wrong: a double negation around a temporal subformula is inert.           *)
(*                                                                           *)
(* Checked with TLAPM b064bce: all 29 obligations proved.                    *)
(*                                                                           *)
(*   tlapm --toolbox 0 0 findings/TLC/Tlc014Proofs.tla                       *)
(*****************************************************************************)
EXTENDS Integers, TLAPS
VARIABLE x
Init == x = 0
Next == (x < 3 /\ x' = x + 1) \/ UNCHANGED x
Spec == Init /\ [][Next]_x
TypeOK == x \in Nat
P == x >= 0

THEOREM Invariance == Spec => []TypeOK
  <1>1. Init => TypeOK BY DEF Init, TypeOK
  <1>2. TypeOK /\ [Next]_x => TypeOK' BY DEF TypeOK, Next
  <1>3. QED BY <1>1, <1>2, PTL DEF Spec

THEOREM AlwaysP == Spec => []P
  <1>1. TypeOK => P BY DEF TypeOK, P
  <1>2. QED BY Invariance, <1>1, PTL

THEOREM Corpus == (<>(~(FALSE ~> FALSE)) => FALSE)  BY PTL
THEOREM Inert  == ([]P) <=> [](~(~([]P)))           BY PTL
THEOREM Mwe    == Spec => [](~(~([]P)))             BY AlwaysP, PTL
THEOREM Row2   == Spec => ~(<>(~([]P)))             BY AlwaysP, PTL
THEOREM Row3   == Spec => <>(~(~([]P)))             BY AlwaysP, PTL
THEOREM Row4   == Spec => [](P => ~(~([]P)))        BY AlwaysP, PTL
THEOREM Row6   == ~(<>(~(P ~> P)))                  BY PTL
====
