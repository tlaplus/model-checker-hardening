---- MODULE PolyOps ----
EXTENDS Integers, CustomOperators

Wrapped(value) == Identity(value)
Singleton(value) == {value}
Contains(values, value) == value \in values
Empty == {}

(* @type: { value: a, b } => a; *)
ReadValue(record) == record.value

WithValue(value) == [value |-> value]

(* Independent argument-only type variables. *)
First(left, right) == left

(* A local operator must not become a spurious module dependency. *)
Local(value) == LET Helper(parameter) == parameter IN Helper(value)

(* Fixed names deliberately collide with the generated skeleton and its usual bindings. *)
Init(Inv) == Inv
====
