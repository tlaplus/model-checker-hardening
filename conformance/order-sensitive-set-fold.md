# An order-sensitive `ApaFoldSet` over a set

Observed in corpus29: 22 aggregator deviations in which the two checkers
compute different values for the same `ApaFoldSet`. In 21 of them TLC passed
and Apalache reported a counterexample; in one, `e2dbe490`, TLC reported the
counterexample and Apalache passed.

TLC evaluates `ApaFoldSet(Op, v, S)` with the compatibility definition in
`Apalache.tla`: it recursively combines `v` with a `CHOOSE`-picked element of
`S`. Apalache folds the arena cells of `S` in their arena order. When the value
of `Op` depends on the combination order — the extreme case is
`Op(a, b) == b`, which yields the last-visited element — the two tools compute
different values. Each tool is deterministic, but TLA+ fixes no iteration order
for a set, so neither value is the single admitted one. This is the same kind
of intentional deviation as
[`CHOOSE` with more than one witness](choose-multiple-witnesses.md), reached
through a fold rather than an explicit `CHOOSE`: on Apalache's side no
`CHOOSE` is evaluated at all.

## Representative MWE

```tla
---- MODULE FoldOrder ----
EXTENDS Integers, Apalache
VARIABLE
\* @type: Bool;
flag
\* @type: (Bool, Bool) => Bool;
L(a, b) == b
Init == flag = ApaFoldSet(L, FALSE, {TRUE, FALSE})
Next == UNCHANGED flag
Inv == flag
====
```

TLC completes with no error: its fold picks `FALSE` first and returns `TRUE`,
so `Inv` holds. Apalache 0.62.2 reports `State 0: state invariant 0 violated`
and exits 12: its fold returns `FALSE`. Negating the invariant flips the
verdicts.

## Corpus evidence

corpus29 is a `module` corpus with generational mutation
([ADR 0010](../docs/decisions/0010-mutation.md)) and 41,688 aggregator
deviations. In the TLC-pass/Apalache-counterexample direction, 63 deviations
remain; 37 of them are
[an eventuality failing in the initial state](../findings/TLC/tlc-008.md). Of
the other 26, 22 carry an `ApaFoldSet` over a set with at least two elements
whose combinator makes the result order-dependent:

- `b69d0db3` reduces to the MWE verbatim: its invariant is
  `ApaFoldSet(Lambda5, FALSE, {TRUE, FALSE})` with `Lambda5(a, b) == b`.
- `1096a692` and `16da33a4` carry the same fold in the property, with
  `Lambda12(a, b) == b` over `{TRUE, FALSE}`.
- `3c195332`, `dfae0a5c` and `e9821e2a` use combinators that rebuild the result
  from the element argument, such as `[p1 EXCEPT ![var0] = p2]`.
- The remaining 16 match the same shape: an `ApaFoldSet` combinator whose body
  is the element parameter. They were rerun but not individually reduced.

Of the other four, `47a267be` is
[`CHOOSE` with more than one witness](choose-multiple-witnesses.md), and
`23d225ff`, `34ca0062` and `64f6a67d` were not reduced. In the opposite
direction, `e2dbe490` initializes `var0` from a set containing such a fold:
TLC's fold returns `TRUE` and violates `Inv == ~var0`, while Apalache's
returns `FALSE` and passes.

All 27 deviations were rerun with the workflow's arguments: TLC commit
`142d0ba` (tla2tools `1.8.0-20260917.033119-76`) exits 0 for the 26 and 12 for
`e2dbe490`; Apalache 0.62.2 (build `f0dec98`) reports a counterexample for the
26 and `NoError` for `e2dbe490`. FuzzTLA is `bfc3a25`.

Specifications that must be checked by both tools should only fold over a set
with an order-insensitive combinator, or fold over a sequence with
`ApaFoldSeqLeft`, where the order is fixed.
