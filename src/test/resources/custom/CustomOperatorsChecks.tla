---- MODULE CustomOperatorsChecks ----
EXTENDS CustomOperators

Check ==
    LET values == [key \in {"present"} |-> 7]
        flags == [key \in {1} |-> TRUE]
    IN /\ Clamp(-1, 0, 2) = 0
       /\ Clamp(1, 0, 2) = 1
       /\ Clamp(3, 0, 2) = 2
       /\ Clamp(7, 2, 2) = 2
       /\ NonEmpty({1})
       /\ ~NonEmpty({})
       /\ NonEmpty({FALSE})
       /\ Disjoint({1}, {2})
       /\ Disjoint({}, {"key"})
       /\ ~Disjoint({FALSE}, {FALSE})
       /\ Restrict(values, {"present", "absent"}) = values
       /\ DOMAIN Restrict(values, {}) = {}
       /\ DOMAIN Restrict(values, {"absent"}) = {}
       /\ GetOrElse(values, "present", 9) = 7
       /\ GetOrElse(values, "absent", 9) = 9
       /\ GetOrElse(flags, 1, FALSE)
       /\ ~GetOrElse(flags, 2, FALSE)
====
