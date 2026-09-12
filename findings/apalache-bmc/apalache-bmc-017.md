---
state: open
labels: [apalache]
---

# A computed empty function-set domain loses every initial state

## Summary

`[S -> R]` with `S = {}` is the singleton `{<<>>}`, the empty function, for any
`R`. Apalache 0.62.2 encodes `fn \in [S -> {}]` as unsatisfiable whenever `S` is
an empty set produced by an expression rather than written as the literal `{}`.
The initial predicate then has no model, the bounded checker explores nothing,
and the run ends with exit status `OK`.

The same misencoding is observable from an invariant, where the checker does
explore a state: `[S -> {}] = {}` and `[S -> {}] \subseteq {}` are answered
`TRUE`.

In the initial-predicate direction the invariant is never evaluated. The only
signal is the warning
`All executions are shorter than the provided bound.` and the outcome
`ExecutionsTooShort`, which a specification can also reach legitimately, so
nothing in the exit status distinguishes this from a checked property.

Both conditions are required. A literal `{}` domain is encoded correctly, and a
nonempty range is encoded correctly with either domain:

| Function set | Apalache 0.62.2 | TLC |
| --- | --- | --- |
| `[{} -> {}]` | initial state found | initial state found |
| `[{x \in {1} : FALSE} -> {}]` | no initial state | initial state found |
| `[ApaFoldSet(Lambda, {}, {}) -> {}]` | no initial state | initial state found |
| `[{} -> {1}]` | initial state found | initial state found |
| `[ApaFoldSet(Lambda, {}, {}) -> {1}]` | initial state found | initial state found |

This is the complement of [`apalache-bmc-002`](apalache-bmc-002.md), which
records a spurious counterexample for the *literal* empty domain and notes that
the computed-empty-domain variant did not fail. It fails, in the opposite
direction and without any counterexample to inspect.

The `corpus12` run contains one aggregator deviation with this cause, where TLC
reported a violation and Apalache passed. Its initial predicate is
`var0 \in [ApaFoldSet(Lambda77, {}, {}) -> {}] /\ ...`. See the
[`b0f60628...` input](../../corpus12/03aggregator-fail/b0f60628a128c7e33ac1b8e87ad7240e5e770a2ef9b1eeba2286831bf02a27be.cbor).

Observed with Apalache 0.62.2, the version this repository pins. It does not
reproduce on the 0.62.3-SNAPSHOT build `129af5d`, which reports the violation
for every row above; no released version carries that fix yet.

## Reproduction

```tla
---- MODULE EmptyRangeFunSet ----
EXTENDS Integers

VARIABLE
\* @type: (Int -> Int);
fn

VARIABLE
\* @type: Int;
step

Init == fn \in [{x \in {1} : FALSE} -> {}] /\ step = 0
Next == UNCHANGED fn /\ step' = step + 1
Inv == step < 0
Bound == step <= 4
====
```

`Inv` is false in the initial state, so a checker that finds that state must
report a violation. TLC, with `INIT Init`, `NEXT Next`, `INVARIANT Inv`,
`CONSTRAINT Bound`, does:

```text
Computing initial states...
Error: Invariant Inv is violated by the initial state:
/\ fn = <<>>
/\ step = 0
```

`fn = <<>>` is the empty function, the single element of the function set. Run
Apalache on the same module:

```sh
java -Xmx1g -jar apalache.jar check \
  --init=Init --next=Next --inv=Inv --length=5 --no-deadlock EmptyRangeFunSet.tla
```

```text
PASS #13: BoundedChecker
All executions are shorter than the provided bound.
The outcome is: ExecutionsTooShort
Checker reports no error up to computation length 5
EXITCODE: OK
```

Replacing the domain with the literal `{}`, or the range with `{1}`, makes
Apalache report the violation. `{x \in {1} : FALSE}` may be replaced by any
other expression that evaluates to the empty set, for example
`ApaFoldSet(LAMBDA acc, x: {x}, {}, {})`, with the same result.

## Reached through the invariant, and through a `LET`-wrapped domain

The `corpus14` run adds two aggregator deviations with this cause, both TLC
counterexample and Apalache pass, and both extend the shape recorded above.

The first reaches the defect from the invariant rather than the initial
predicate, so the checker does explore a state and does evaluate the property:
it answers the emptiness question itself incorrectly. Its invariant reduces to
`[{f \in S : FALSE} -> {}] \subseteq {}`. See the
[`719224ff...5ff3fa.cbor` input](../../corpus14/03aggregator-fail/719224ff4bf8129f3d67be032d26deb2aa71f00b8eafd27434973ee84a5ff3fa.cbor).

```tla
---- MODULE EmptyRangeFunSetInvariant ----
EXTENDS Integers

VARIABLE
\* @type: Int;
step

\* @type: (() => Set(Str));
EmptyRange == {}

\* @type: (() => Set((Int -> Str)));
NoFunctions == {}

Init == step = 0
Next == step' = step + 1
\* @type: (() => Bool);
Inv == [{x \in {step} : FALSE} -> EmptyRange] = NoFunctions
Bound == step <= 4
====
```

The function set is `{<<>>}`, so `Inv` is false and TLC reports
`Invariant Inv is violated by the initial state`. Apalache 0.62.2 reports
`state invariant 0 holds` and `NoError`. Written with `\subseteq` in place of
`=` the result is the same.

The second is the initial-predicate shape of the summary, with one more way of
spelling a non-literal empty domain: a `LET` whose body is the literal `{}`.
Its initial predicate is `var0 \in [(LET LocalOp89 == ... IN {}) -> {}] /\ ...`,
and Apalache finds no initial state. Replacing that `LET`-wrapped `{}` with a
bare `{}` in the IR, changing nothing else, makes Apalache find the state and
report the violation, which isolates the wrapper as the trigger. See the
[`c988393d...ae1cd1.cbor` input](../../corpus14/03aggregator-fail/c988393d3548dc6aeb633a11b18e2bbc558269f2cfcbb9d47945f786faae1cd1.cbor).

Extending the table above:

| Function set | Apalache 0.62.2 | TLC |
| --- | --- | --- |
| `[(LET Unused == {} IN {}) -> {}]` | no initial state | initial state found |
| `[{x \in {1} : FALSE} -> {}] = {}` in an invariant | answered `TRUE` | answered `FALSE` |

Neither reproduces on the 0.62.3-SNAPSHOT build `fefda087`, which reports the
violation for both corpus inputs and for the module above.

## Expected behavior

`[S -> R]` denotes the set of total functions from `S` to `R`. When `S` is
empty that set is `{<<>>}` for every `R`, including an empty `R`, so
`fn \in [S -> {}]` must be satisfiable and must fix `fn` to the empty function.
How the empty domain was obtained must not change the encoding.

## Impact

This is a soundness defect in the silent direction. Apalache reports `OK` on a
specification whose invariant it never evaluated, and an automated consumer
records a `pass` verdict that carries no information. Unlike a spurious
counterexample, nothing is printed for a user to inspect: the run looks like a
successful bounded check, and the `ExecutionsTooShort` warning is indistinguishable
from the legitimate case of a specification whose behaviors end before the bound.

The invariant-level direction is as unsound and equally quiet: the run explores
a state, evaluates the property, and reports `NoError` for a property that is
false, with no warning at all.
