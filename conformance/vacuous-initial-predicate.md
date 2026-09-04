# Vacuous initial predicate

Not a deviation: both checkers pass. Recorded because it is what makes some
deviations observable, and because it makes an input verify nothing.

A generated module can have an unsatisfiable initial predicate. TLC computes
`0 distinct states` and reports success; Apalache finds no execution, reports
`ExecutionsTooShort`, and exits `OK`. The two agree, so no deviation is
recorded, and nothing in either verdict distinguishes such a run from one that
checked a reachable state space.

It matters here for two reasons.

It is the second ingredient of the
[constant-level `FALSE` invariant](constant-false-invariant.md) row. TLC's
refusal of that invariant is unconditional, but Apalache only *passes* where no
initial state exists; given a reachable state it reports the counterexample that
`FALSE` guarantees, and both checkers fail. Ten of that row's nineteen
instances are vacuous in this way.

It is also a corpus-quality signal independent of any checker difference. A
vacuous entry occupies a corpus slot, consumes both checkers, and establishes
nothing about either. Neither tool warns, so the condition is invisible unless
the initial-state count is inspected.

## Representative MWE

```tla
---- MODULE VacuousInitialPredicate ----
EXTENDS Integers
VARIABLE
\* @type: Int;
step
Init == step = 0 /\ step = 1
Next == step' = step + 1
Inv == TRUE
Bound == step <= 5
====
```

TLC reports:

```text
Finished computing initial states: 0 distinct states generated
0 states generated, 0 distinct states found, 0 states left on queue.
```

and exits successfully. Apalache reports `The outcome is: ExecutionsTooShort`
and exits `OK`.

Detecting the condition is cheap on the TLC side, which prints the count, and on
the Apalache side, which distinguishes `ExecutionsTooShort` from `NoError` in
its own output but reports both as success.
