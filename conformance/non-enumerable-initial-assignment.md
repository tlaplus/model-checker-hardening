# Non-enumerable initial assignment

Observed share: 0.31% of corpus1 aggregator deviations; TLC failed and Apalache
passed.

An initial predicate may constrain a variable with `x \in S` rather than
`x = e`. When `S` is infinite, TLC cannot enumerate it to produce initial
states and reports that the right side of `\in` is not enumerable, as error
2103 with the spec-evaluation status 75. Apalache represents the same
constraint symbolically and checks the module without enumerating `S`.

This is the same underlying TLC enumeration limit that
[quantification over an infinite set](quantification-over-infinite-set.md) and
[`CHOOSE` over `Int` or `Nat`](choose-over-infinite-set.md) record, reached
through initial-state computation instead. It is listed separately because it
carries its own diagnostic and is only reachable where a workflow generates
state variables and initializes them by membership.

## Representative MWE

```tla
---- MODULE NonEnumerableInitialAssignment ----
EXTENDS Integers
VARIABLE
\* @type: Int;
var0
VARIABLE
\* @type: Int;
step
Init == var0 \in Int /\ step = 0
Next == var0' = var0 /\ step' = step + 1
Inv == step >= 0
Bound == step <= 5
====
```

TLC reports:

```text
TLC error code 2103 mapped to exit status 75
Error: In computing initial states, the right side of \IN is not enumerable.
```

Apalache reports `The outcome is: NoError` and exits `OK`.

Unlike most rows in this catalogue, the MWE reproduces both sides directly: the
deviation does not depend on a surrounding expression's evaluation path.

## Reached through the next-state action

The same limit is reached when the membership constrains a primed variable. TLC
reports it as the next-state analogue of the diagnostic above, and the class is
otherwise identical.

```tla
---- MODULE NonEnumerableNextAssignment ----
EXTENDS Integers
VARIABLE
\* @type: Int;
var0
VARIABLE
\* @type: Int;
step
Init == var0 = 0 /\ step = 0
Next == var0' \in Nat /\ step' = step + 1
Inv == step >= 0
Bound == step <= 5
====
```

TLC computes the initial state and then fails:

```text
Error: In computing next states, the right side of \IN is not enumerable.
line 10, col 9 to line 10, col 21 of module NonEnumerableNextAssignment
```

Apalache reports `The outcome is: NoError` and exits `OK`. One `corpus10`
aggregator deviation has this shape.
