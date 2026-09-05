# Union containing an infinite set

Observed share: 0.03% of the original session's aggregator deviations; TLC
failed and Apalache passed. Corpus3 adds 15 instances (<0.01% of its 435,265
deviations), including both `UNION S` and binary `S \cup T`.

TLC attempts to enumerate the operands while computing a union. Apalache can
retain the integer component symbolically.

## Representative MWE

```tla
---- MODULE UnionContainingInfiniteSet ----
EXTENDS Integers
VARIABLE
\* @type: Set(Int);
values
Init == values = UNION {{0}, Int}
Next == UNCHANGED values
Inv == TRUE
====
```

TLC cannot enumerate `Int`; Apalache simplifies the union to a symbolic integer
set.
