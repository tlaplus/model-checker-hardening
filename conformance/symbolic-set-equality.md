# Equality over symbolic-set representations

The original session's share was 0.16% of Apalache crash outcomes. The later
`corpus2` run contains 44 instances among 341 crashes (12.90%): 25 compare the
same symbolic representation and 19 compare symbolic and materialized sets.

Apalache aborts with `Unexpected equality test over types` while comparing
symbolic sets. TLC evaluates the corresponding equality normally.

## Representative MWE

```tla
---- MODULE SymbolicSetEquality ----
VARIABLE
\* @type: Seq(Set(Set(Bool)));
sets

\* @type: (() => Seq(Set(Set(Bool))));
Rhs == <<(SUBSET (DOMAIN [b \in {} |-> FALSE])) \union {}>>

Init == sets = Rhs
Next == UNCHANGED sets
Inv == sets = Rhs
====
```

The corpus signature reports `PowSet[Set(Bool)]` on both sides. This defect is
filed as
[`apalache-bmc/apalache-bmc-003`](../findings/apalache-bmc/apalache-bmc-003.md).
