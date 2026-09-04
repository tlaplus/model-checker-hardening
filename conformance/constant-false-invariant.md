# Constant-level `FALSE` invariant

Observed share: 2.91% of corpus1 aggregator deviations; TLC failed and Apalache
passed.

TLC rejects an invariant that is a constant-level formula evaluating to `FALSE`
as a specification error, before computing any state. It reports error 2230 and
exits with status 151, the spec-parse status, and directs the user to `ASSUME`
for constant-level assertions. Apalache has no such restriction and checks the
invariant against reachable states like any other.

The deviation needs a second ingredient to appear: the module must also have no
initial state. Where initial states exist, Apalache reports the counterexample
that `FALSE` guarantees and both checkers fail. Where the initial predicate is
unsatisfiable, Apalache finds nothing to violate the invariant, reports
`ExecutionsTooShort`, and exits `OK`, while TLC still rejects the invariant
without reaching that conclusion.

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

Both checkers agree about the module itself: replacing `Inv` with `TRUE` makes
TLC compute `0 distinct states` and pass, matching Apalache. The disagreement is
entirely TLC's restriction on constant-level invariants.

This shape is a property of the generator rather than of TLA<sup>+</sup>: it
arises where a generated invariant degenerates to the closed Boolean terminal.
Reducing that degeneration removes the row rather than resolving it.
