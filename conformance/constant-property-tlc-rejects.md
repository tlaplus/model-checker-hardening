# TLC rejects a constant `FALSE` or tautological property

Observed share: 4.17% (10 of 240) of the aggregator deviations in a 400-module
smoke corpus with the `action` and `temporal` categories enabled
([ADR 0007](../docs/decisions/0007-levels-and-temporal-properties.md)); TLC
failed and Apalache passed or reported a counterexample.

This is the property counterpart of the
[constant-level `FALSE` invariant](constant-false-invariant.md). TLC inspects
a temporal property before computing any state and refuses one that it can
decide without the behaviors:

| TLC detail | Code | Smoke count |
| --- | ---: | ---: |
| `The property of Prop is equal to FALSE` | 150 | 8 |
| `The spec is trivially false because FALSE is false.` | 150 | 1 |
| `Temporal formula is a tautology (its negation is unsatisfiable).` | 75 | 1 |

Corpus22 adds a variant of the second detail that names a bound variable, as in
`The spec is trivially false because q36 is false.` (code 150, 3 deviations, all
with an Apalache counterexample). Each property is a bounded `\A` over a set
enumeration, `\A q36 \in {e, ...} : ...`, whose elements are large fold, `CASE`
and `CHOOSE` expressions. The shape was not reduced: the
simpler properties `\A q \in {FALSE} : q` and `\A q \in {1} : FALSE` produce
`The property of Prop is equal to FALSE` instead.

Apalache has no such restriction. It checks `Liveness == Fairness => Prop` like
any other property: it reports a counterexample when an initial state exists, or
a violation of the invariant, and passes when the initial predicate is
unsatisfiable.

## Representative MWE

```tla
---- MODULE ConstantPropertyTlcRejects ----
EXTENDS Integers
VARIABLES
  \* @type: Int;
  x,
  \* @type: Int;
  step
Init == x = 0 /\ step = 0
Next == (step < 3 /\ x' = x + 1 /\ step' = step + 1) \/ UNCHANGED <<x, step>>
Inv == TRUE
\* @type: <<Int, Int>>;
vars == <<x, step>>
Fairness == TRUE
Spec == Init /\ [][Next]_vars /\ Fairness
Prop == FALSE
Liveness == Fairness => Prop
====
```

TLC, configured with `SPECIFICATION Spec` and `PROPERTY Prop`, reports `The
property of Prop is equal to FALSE`. Apalache, run with `--temporal=Liveness`,
reports a counterexample in the initial state.

This shape is a property of the generator rather than of TLA<sup>+</sup>: it
arises where a generated property degenerates to a closed Boolean terminal or to
a vacuous quantifier.
