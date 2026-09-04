# Constant-level `FALSE` invariant

Observed share: 2.91% of corpus1 aggregator deviations; TLC failed and Apalache
passed.

TLC refuses an invariant that is a constant-level formula evaluating to `FALSE`.
It reports error 2230 and exits with status 151, the spec-parse status, and
directs the user to `ASSUME` for constant-level assertions. The check runs
before any state is computed. Apalache has no such restriction and checks the
formula against reachable states like any other invariant.

That restriction is the whole of TLC's side, and it is unconditional. What
Apalache does on the same module is a separate question with more than one
answer, so the 19 corpus1 instances do not share a single explanation:

| Apalache's side | Count |
| --- | ---: |
| No initial state exists, so nothing violates the invariant — see [vacuous initial predicate](vacuous-initial-predicate.md) | 10 |
| TLC's constant-level check preempts a second, independent failure | 8 |
| Apalache evaluates the formula incorrectly — see [`apalache-bmc-007`](../findings/apalache-bmc/apalache-bmc-007.md) | 1 |

The middle row is a reporting artifact rather than a conformance difference.
TLC evaluates the invariant before computing initial states, so where the module
*also* contains an undefined expression the recorded diagnostic names the
invariant and hides the other failure. Neutralizing the invariant makes those
eight report an unmatched `CASE`, `Head` of an empty sequence, or a `CHOOSE`
without a witness, which are rows of their own in this catalogue.

## Representative MWE

```tla
---- MODULE ConstantFalseInvariant ----
EXTENDS Integers
VARIABLE
\* @type: Int;
step
Init == step = 0 /\ step = 1
Next == step' = step + 1
Inv == FALSE
Bound == step <= 5
====
```

TLC reports:

```text
TLC error code 2230 mapped to exit status 151
Error: The invariant of Inv is equal to FALSE
```

Apalache reports `The outcome is: ExecutionsTooShort` and exits `OK`.

The MWE pairs the restriction with an unsatisfiable initial predicate, because
that is the only way to observe TLC failing while Apalache passes. Given a
reachable state, `Inv == FALSE` is violated and Apalache reports the
counterexample, so both checkers fail and no deviation is recorded.

This shape is a property of the generator rather than of TLA<sup>+</sup>: it
arises where a generated invariant degenerates to the closed Boolean terminal.
Reducing that degeneration removes the row rather than resolving it.
