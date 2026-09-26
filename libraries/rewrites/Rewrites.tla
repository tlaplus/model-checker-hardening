------------------------------ MODULE Rewrites ------------------------------
(***************************************************************************)
(* The seed rewrite rules of metamorphic testing (ADR 0017). Every        *)
(* top-level definition is a rule F(p1, ..., pn) == A = B: the rewriter    *)
(* replaces an instance of A with the same instance of B. A parameter that *)
(* occurs only in B is fresh: the generator draws it at its type.          *)
(*                                                                         *)
(* Declaration order is part of the byte encoding: a corpus pins these     *)
(* sources, so editing a rule requires a new corpus. Put helpers in a      *)
(* module this one EXTENDS. Check every rule in RewritesCheck.tla.         *)
(***************************************************************************)
EXTENDS Integers, FiniteSets

PlusZero(x) == x = x + 0

AddSub(x, y) == x = x + (y - y)

DoubleNeg(P) == P = ~~P

UnionSelf(S) == S = S \union S

UnchangedPrime(x) == (UNCHANGED x) = (x' = x)

ForallNotExists(S, P(_)) == (\A e \in S : P(e)) = ~(\E e \in S : ~P(e))
=============================================================================
