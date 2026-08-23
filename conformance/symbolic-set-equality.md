# Equality over symbolic-set representations

Observed share: 0.16% of Apalache crash outcomes. Within this group, 66.67%
involve `PowSet` and 33.33% involve `FinFunSet`.

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
[`apalache-bmc/issue-003`](../findings/apalache-bmc/issue-003.md).
