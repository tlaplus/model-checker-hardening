# Apalache does not support `ENABLED`

Observed share: not yet measured. Generated modules contain `ENABLED` once the
`temporal` category is enabled ([ADR 0007](../docs/decisions/0007-levels-and-temporal-properties.md));
TLC passes or reports a counterexample and Apalache fails.

TLC evaluates `ENABLED A` by searching for a successor state that satisfies `A`.
Apalache lists `ENABLED` as unsupported and rejects it wherever it occurs: in an
invariant, a guard or a temporal property.

## Representative MWE

```tla
---- MODULE EnabledApalacheUnsupported ----
EXTENDS Integers
VARIABLES
  \* @type: Int;
  x,
  \* @type: Int;
  step
Init == x = 0 /\ step = 0
Inc == step < 3 /\ x' = x + 1 /\ step' = step + 1
Next == Inc \/ UNCHANGED <<x, step>>
Inv == (ENABLED Inc) \/ step = 3
====
```

TLC passes. Apalache exits with status 75 and reports `unsupported expression:
ENABLED`. This is a documented Apalache limitation, not a defect.
