-------------------------- MODULE TemporalRewrites --------------------------
(***************************************************************************)
(* The temporal rewrite rules of metamorphic testing (ADR 0027). Every    *)
(* top-level definition is a rule F(p1, ..., pn) == A <=> B: the rewriter  *)
(* replaces an instance of A with the same instance of B, in a property    *)
(* or a fairness condition only. A corpus loads this module through        *)
(* [metamorphic] temporal_rules, after the rules of Rewrites.tla.          *)
(*                                                                         *)
(* Parameters, by their position in A (ADR 0027 section 1):                *)
(*   F, G  formula parameters: they match a state predicate or a temporal  *)
(*         formula, never an action                                        *)
(*   A     an action parameter: the action of [A]_v, <<A>>_v, WF_v(A),    *)
(*         SF_v(A) or ENABLED A                                            *)
(*   v     a subscript                                                     *)
(*                                                                         *)
(* A rule whose B applies a temporal operator that A does not apply, such  *)
(* as EventuallyWeaken, applies only where the property admits a temporal  *)
(* formula.                                                                *)
(*                                                                         *)
(* Declaration order is part of the byte encoding, as in Rewrites.tla.     *)
(* Prove every rule in TemporalRewritesProofs.tla. The weight tiers are    *)
(* those of Rewrites.tla: 8 for a rule that changes how TLC checks a       *)
(* formula, 2 for a split, 1 for the rest.                                 *)
(***************************************************************************)

\* Always and eventually
AlwaysTwice(F) == []F <=> [][]F

EventuallyTwice(F) == <>F <=> <><>F

\* @weight: 2;
AlwaysAndSplit(F, G) == [](F /\ G) <=> ([]F /\ []G)

\* @weight: 2;
EventuallyOrSplit(F, G) == <>(F \/ G) <=> (<>F \/ <>G)

\* @weight: 8;
AlwaysAsNotEventually(F) == []F <=> ~<>~F

\* @weight: 8;
EventuallyAsNotAlways(F) == <>F <=> ~[]~F

AlwaysUnfold(F) == []F <=> (F /\ []F)

EventuallyUnfold(F) == <>F <=> (F \/ <>F)

InfOftenIdem(F) == []<>F <=> []<>[]<>F

EventuallyAlwaysIdem(F) == <>[]F <=> <>[]<>[]F

\* Leads-to
\* @weight: 8;
LeadsToDef(F, G) == (F ~> G) <=> [](F => <>G)

\* Actions
BoxActionUnchanged(A, v) == [][A]_v <=> [][A \/ UNCHANGED v]_v

DiamondActionChanged(A, v) == <><<A>>_v <=> <><<A /\ ~UNCHANGED v>>_v

\* Fairness, by its definition in terms of ENABLED
\* @weight: 8;
WFDef(A, v) == WF_v(A) <=> ([]<>~ENABLED <<A>>_v \/ []<><<A>>_v)

\* @weight: 8;
SFDef(A, v) == SF_v(A) <=> (<>[]~ENABLED <<A>>_v \/ []<><<A>>_v)

\* Idempotence: TLC checks []F alone as an invariant, the disjunction and the
\* conjunction with <>F by its liveness checker
\* @weight: 8;
AlwaysWithEventually(F) == []F <=> ([]F /\ <>F)

AlwaysAndIdem(F) == []F <=> ([]F /\ []F)

\* @weight: 8;
AlwaysOrIdem(F) == []F <=> ([]F \/ []F)

EventuallyAndIdem(F) == <>F <=> (<>F /\ <>F)

EventuallyOrIdem(F) == <>F <=> (<>F \/ <>F)

\* Raising the level of a formula, at temporal positions only
AlwaysOrBreak(F) == F <=> ([]F \/ (F /\ <>~F))

EventuallyWeaken(F) == F <=> (F /\ <>F)
=============================================================================
