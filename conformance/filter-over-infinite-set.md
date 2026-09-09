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

## Membership against the filter

A membership test reaches the same limit under a different diagnostic. When the
filter's predicate raises an evaluation error, TLC over a finite bound reports
that error, but over `Nat` it reports only that it cannot decide the membership:

```tla
Inv == x \notin {n \in Nat : Head(<<>>) = n}
```

TLC exits with status 75 and reports:

```text
Error: Cannot decide if element:
0
 is element of:
Nat
and satisfies the predicate ...
```

Replacing `Nat` with `{0, 1}` reports `Attempted to apply Head to the empty
sequence.` instead, so the opaque message is the infinite bound hiding an
otherwise documented undefined expression. One corpus9 deviation
([`491d0018...`](../corpus9/03aggregator-fail/491d0018c44befaa3df242b8b301c55c24255e725ba0b85dc4f983af2505a550.cbor))
has this shape.
