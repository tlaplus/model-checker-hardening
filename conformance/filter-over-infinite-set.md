# Filtering `Nat`

Observed share: 0.03% of aggregator deviations; TLC failed and Apalache passed.

TLC reached a filter over `Nat` and could not enumerate its domain. In the one
observed instance, Apalache eliminated the surrounding typed-IR computation and
passed.

## Representative MWE

```tla
---- MODULE FilterOverInfiniteSet ----
EXTENDS Naturals
VARIABLE
\* @type: Set(Int);
values
Init == values = {n \in Nat : n = 0}
Next == UNCHANGED values
Inv == TRUE
====
```

The module isolates TLC's diagnostic. Checked directly as Apalache source, it
hits the [`SetFilterRule` crash](set-filter-symbolic-set.md); the corpus pass is
specific to evaluation of the larger generated expression.
