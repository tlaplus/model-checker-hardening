---- MODULE CustomOperators ----
EXTENDS Integers

Identity(value) == value

(* Clamp an integer to the inclusive interval; requires lower <= upper. *)
Clamp(value, lower, upper) ==
    IF value < lower THEN lower ELSE IF value > upper THEN upper ELSE value

NonEmpty(values) == values # {}
Disjoint(left, right) == left \cap right = {}

(* Ignore requested keys that are outside the function's domain. *)
(* @type: ((a -> b), Set(a)) => (a -> b); *)
Restrict(function, keys) ==
    [key \in DOMAIN function \cap keys |-> function[key]]

(* @type: ((a -> b), a, b) => b; *)
GetOrElse(function, key, fallback) ==
    IF key \in DOMAIN function THEN function[key] ELSE fallback
====
