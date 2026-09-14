# Apalache does not support fairness

Observed share: not yet measured. Generated modules contain `WF` and `SF` once
the `temporal` category is enabled ([ADR 0007](../docs/decisions/0007-levels-and-temporal-properties.md));
TLC passes or reports a counterexample and Apalache fails.

TLC checks a property under the fairness of its specification. Apalache supports
neither weak nor strong fairness. The workflow asks it to check
`Liveness == Fairness => Prop`, the implication TLC answers under `Spec`, so that
Apalache reports the limitation instead of a counterexample that fairness excludes.
Apalache throws `scala.NotImplementedError` and exits with status 255, which the
workflow classifies as a `spec_eval` failure rather than a crash.

## Representative MWE

```tla
---- MODULE FairnessApalacheUnsupported ----
EXTENDS Integers
VARIABLES
  \* @type: Int;
  x,
  \* @type: Int;
  step
Init == x = 0 /\ step = 0
Inc == step < 3 /\ x' = x + 1 /\ step' = step + 1
Next == Inc \/ UNCHANGED <<x, step>>
Inv == TRUE
\* @type: <<Int, Int>>;
vars == <<x, step>>
Fairness == WF_vars(Inc)
Spec == Init /\ [][Next]_vars /\ Fairness
Prop == <>(x = 3)
Liveness == Fairness => Prop
====
```

TLC passes `PROPERTY Prop` under `SPECIFICATION Spec`. Apalache, run with
`--temporal=Liveness --length=4`, reports `Handling fairness is not supported
yet!`. This is a documented Apalache limitation, not a defect.
