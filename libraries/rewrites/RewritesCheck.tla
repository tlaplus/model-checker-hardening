--------------------------- MODULE RewritesCheck ---------------------------
(***************************************************************************)
(* The self-test of libraries/rewrites (ADR 0017 §7): TLC asserts every    *)
(* constant-level rule on small domains, each polymorphic rule at two      *)
(* element types, and every action-level rule as the action property       *)
(* StepProperty of a small specification. A false assumption names the    *)
(* line of its rule. Spec, Inv and StepProperty let the fuzztla TLC worker *)
(* check this module.                                                      *)
(***************************************************************************)
EXTENDS Rewrites

Ints == -3..3

ASSUME \A x \in Ints : PlusZero(x)
ASSUME \A x, y \in Ints : AddSub(x, y)
ASSUME \A P \in BOOLEAN : DoubleNeg(P)
ASSUME \A S \in SUBSET Ints : UnionSelf(S)
ASSUME \A S \in SUBSET SUBSET {1, 2} : UnionSelf(S)
ASSUME \A S \in SUBSET Ints : ForallNotExists(S, LAMBDA e : e > 0)
ASSUME \A S \in SUBSET Ints, k \in Ints : ForallNotExists(S, LAMBDA e : e # k)
ASSUME \A S \in SUBSET SUBSET {1, 2} : ForallNotExists(S, LAMBDA e : 1 \in e)

VARIABLE v
Spec == v \in Ints /\ [][v' \in Ints]_v
Inv == TRUE
StepProperty == [][UnchangedPrime(v)]_v
=============================================================================
