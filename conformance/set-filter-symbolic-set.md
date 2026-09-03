# Set filtering over symbolic-set representations

Observed share: 1.05% of Apalache crash outcomes. Within this group, 25% use an
`InfSet` domain, 60% use `PowSet`, and 15% use `FinFunSet`.

Apalache reaches `SetFilterRule` and throws an unhandled
`NotImplementedError` for these arena shapes. TLC either evaluates a finite
domain or reports its ordinary non-enumerable-set error.

## Representative MWE

```tla
---- MODULE SetFilterSymbolicSet ----
EXTENDS Integers
VARIABLE
\* @type: Set(Int);
filtered
Init == filtered = {n \in Int : n = 0}
Next == UNCHANGED filtered
Inv == TRUE
====
```

TLC reports that `Int` is not enumerable. Apalache instead aborts on the
`InfSet` filter. The shared defect and its other arena variants are filed as
[`apalache-bmc/apalache-bmc-001`](../findings/apalache-bmc/apalache-bmc-001.md).
