----------------------- MODULE TemporalRewritesProofs -----------------------
(***************************************************************************)
(* The proofs of TemporalRewrites.tla (ADR 0027 section 1): every rule R   *)
(* is valid, stated as THEOREM RValid. The hypotheses follow from where a  *)
(* parameter occurs in the rule's pattern:                                 *)
(*                                                                         *)
(*   a formula parameter    TEMPORAL F                                     *)
(*   an action parameter    ACTION A                                       *)
(*   a subscript            STATE v                                        *)
(*                                                                         *)
(* PTL proves every rule. Checked with TLAPM b064bce: all 44 obligations   *)
(* proved.                                                                 *)
(*                                                                         *)
(*   make proofs                                                           *)
(***************************************************************************)
EXTENDS TemporalRewrites, TLAPS

THEOREM AlwaysTwiceValid == ASSUME TEMPORAL F PROVE AlwaysTwice(F)
  BY PTL DEF AlwaysTwice

THEOREM EventuallyTwiceValid == ASSUME TEMPORAL F PROVE EventuallyTwice(F)
  BY PTL DEF EventuallyTwice

THEOREM AlwaysAndSplitValid == ASSUME TEMPORAL F, TEMPORAL G PROVE AlwaysAndSplit(F, G)
  BY PTL DEF AlwaysAndSplit

THEOREM EventuallyOrSplitValid == ASSUME TEMPORAL F, TEMPORAL G PROVE EventuallyOrSplit(F, G)
  BY PTL DEF EventuallyOrSplit

THEOREM AlwaysAsNotEventuallyValid == ASSUME TEMPORAL F PROVE AlwaysAsNotEventually(F)
  BY PTL DEF AlwaysAsNotEventually

THEOREM EventuallyAsNotAlwaysValid == ASSUME TEMPORAL F PROVE EventuallyAsNotAlways(F)
  BY PTL DEF EventuallyAsNotAlways

THEOREM AlwaysUnfoldValid == ASSUME TEMPORAL F PROVE AlwaysUnfold(F)
  BY PTL DEF AlwaysUnfold

THEOREM EventuallyUnfoldValid == ASSUME TEMPORAL F PROVE EventuallyUnfold(F)
  BY PTL DEF EventuallyUnfold

THEOREM InfOftenIdemValid == ASSUME TEMPORAL F PROVE InfOftenIdem(F)
  BY PTL DEF InfOftenIdem

THEOREM EventuallyAlwaysIdemValid == ASSUME TEMPORAL F PROVE EventuallyAlwaysIdem(F)
  BY PTL DEF EventuallyAlwaysIdem

THEOREM LeadsToDefValid == ASSUME TEMPORAL F, TEMPORAL G PROVE LeadsToDef(F, G)
  BY PTL DEF LeadsToDef

THEOREM BoxActionUnchangedValid == ASSUME ACTION A, STATE v PROVE BoxActionUnchanged(A, v)
  BY PTL DEF BoxActionUnchanged

THEOREM DiamondActionChangedValid == ASSUME ACTION A, STATE v PROVE DiamondActionChanged(A, v)
  BY PTL DEF DiamondActionChanged

THEOREM WFDefValid == ASSUME ACTION A, STATE v PROVE WFDef(A, v)
  BY PTL DEF WFDef

THEOREM SFDefValid == ASSUME ACTION A, STATE v PROVE SFDef(A, v)
  BY PTL DEF SFDef

THEOREM AlwaysWithEventuallyValid == ASSUME TEMPORAL F PROVE AlwaysWithEventually(F)
  BY PTL DEF AlwaysWithEventually

THEOREM AlwaysAndIdemValid == ASSUME TEMPORAL F PROVE AlwaysAndIdem(F)
  BY PTL DEF AlwaysAndIdem

THEOREM AlwaysOrIdemValid == ASSUME TEMPORAL F PROVE AlwaysOrIdem(F)
  BY PTL DEF AlwaysOrIdem

THEOREM EventuallyAndIdemValid == ASSUME TEMPORAL F PROVE EventuallyAndIdem(F)
  BY PTL DEF EventuallyAndIdem

THEOREM EventuallyOrIdemValid == ASSUME TEMPORAL F PROVE EventuallyOrIdem(F)
  BY PTL DEF EventuallyOrIdem

THEOREM AlwaysOrBreakValid == ASSUME TEMPORAL F PROVE AlwaysOrBreak(F)
  BY PTL DEF AlwaysOrBreak

THEOREM EventuallyWeakenValid == ASSUME TEMPORAL F PROVE EventuallyWeaken(F)
  BY PTL DEF EventuallyWeaken

=============================================================================
