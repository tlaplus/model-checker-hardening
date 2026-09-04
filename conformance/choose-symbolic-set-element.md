# Choosing a symbolic-set value

Observed five times among corpus2's 341 Apalache crash outcomes (1.47%). TLC
produced an ordinary verdict, while Apalache aborted in its bounded checker.

A finite set may contain sets as elements. Apalache can represent those elements
as `PowSet` or `InfSet` arena values, but `CherryPick.pickByOracle` cannot select
those representations when implementing bounded `CHOOSE`.

## Representative MWE

```tla
---- MODULE ChooseSymbolicSet ----

VARIABLE
\* @type: Set(Set(Bool));
x

\* @type: (() => Set(Set(Bool)));
Rhs == CHOOSE s \in {
  SUBSET (DOMAIN [b \in {TRUE} |-> FALSE]),
  SUBSET {TRUE}
} : TRUE

Init == x = Rhs
Next == UNCHANGED x
Inv == TRUE

====
```

Apalache 0.62.2 reports:

```text
rewriter error: Do not know how pick an element from a set of type:
PowSet[Set(Bool)]
EXITCODE: ERROR (255)
```

This is the defect recorded in
[`apalache-bmc-006`](../findings/apalache-bmc/apalache-bmc-006.md), not a
semantic difference in bounded `CHOOSE`.
