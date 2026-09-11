# `CASE` with more than one true guard

Observed once among corpus12's 50,545 aggregator deviations; TLC returned a
counterexample and Apalache passed.

TLA+ defines `CASE p1 -> e1 [] ... [] pn -> en` as
`CHOOSE v : (p1 /\ v = e1) \/ ... \/ (pn /\ v = en)`, so when several guards
hold the value is unspecified but fixed. TLC resolves it by taking the first
true guard in source order. Apalache encodes the choice symbolically and may
take any true arm, so the two tools can disagree about a `CASE` whose guards
overlap.

This is the `CASE` analogue of
[`CHOOSE` with more than one witness](choose-multiple-witnesses.md), observed in
the opposite verdict direction: there Apalache's freedom adds a counterexample
TLC does not find, here it removes one TLC does find.

The corpus input is
[`f252909a...fbed.cbor`](../corpus12/03aggregator-fail/f252909a663e658f74f7cd8da7d40b9325bd42917ccc0fcb18987819f8d6fbed.cbor),
whose invariant begins
`CASE TRUE -> var1 >= step [] [field1 |-> "default_OF_MODEL"] = [field1 |-> "default_OF_MODEL"] -> ...`.
Both guards are true: the first is the literal `TRUE`, and the second compares a
record literal with itself. TLC takes the first arm, which fails once `step`
passes `var1`; Apalache takes an arm that holds.

## Representative MWE

```tla
---- MODULE CaseArms ----
EXTENDS Integers

VARIABLE
\* @type: Int;
step

Init == step = 0
Next == step' = step + 1
Inv ==
  CASE TRUE -> step <= 0
    [] TRUE -> TRUE
Bound == step <= 4
====
```

TLC, with `INIT Init`, `NEXT Next`, `INVARIANT Inv`, `CONSTRAINT Bound`, takes
the first arm and reports a violation in the second state:

```text
Error: Invariant Inv is violated.
2 states generated, 2 distinct states found, 0 states left on queue.
```

Apalache, with `--init=Init --next=Next --inv=Inv --length=5 --no-deadlock`,
takes the second arm and completes:

```text
The outcome is: NoError
EXITCODE: OK
```

Both answers are admitted by the `CASE` expression alone, and TLA+ admits only
one of them for any given specification. Neither tool reports that the guards
overlap.

Specifications that must be checked by both tools should keep `CASE` guards
disjoint, or make the intended precedence explicit with nested `IF`-`THEN`-`ELSE`.
