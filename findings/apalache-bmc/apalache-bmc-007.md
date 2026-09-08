---
state: open
labels: [apalache]
---

# `IsFiniteSet` evaluates to `TRUE` for `Int` and `Nat`

## Summary

Apalache's bounded checker evaluates `IsFiniteSet(Int)` and `IsFiniteSet(Nat)`
as `TRUE`. An invariant asserting that an infinite predefined set is finite is
reported as holding, and its negation, which does hold, is reported as
violated. The checker returns a wrong answer rather than an error or an
unsupported-operator diagnosis.

Apalache knows these sets are infinite elsewhere: applying `Cardinality` to
`Nat` is diagnosed as a non-finite set, which
[`apalache-cli-001`](../apalache-cli/apalache-cli-001.md) records.

Observed with Apalache 0.62.2, build `f0dec98`. Found in `corpus1` as one
aggregator deviation, where TLC rejected `Inv` because it evaluates to `FALSE`
at the constant level while Apalache reported no error. See the
[`49dfbbd2...` input](../../corpus1/03aggregator-fail/49dfbbd229f0c15079182c66d2d94fba6228a3f9ecd09d325c8ae132743e2384.cbor).

## Reproduction

```tla
---- MODULE IsFiniteSetInt ----
EXTENDS Integers, FiniteSets

VARIABLE
\* @type: Int;
step

Init == step = 0
Next == step' = step + 1
Inv == IsFiniteSet(Int)
Bound == step <= 5
====
```

Checked with `--init=Init --next=Next --inv=Inv --length=5`, Apalache reports:

```text
State 0: Checking 1 state invariants
The outcome is: NoError
EXITCODE: OK
```

Replacing the invariant with its negation, `Inv == ~IsFiniteSet(Int)`, which is
the assertion that does hold, reports a counterexample instead:

```text
Found 1 error(s)
The outcome is: Error
EXITCODE: ERROR (12)
```

`IsFiniteSet(Nat)` behaves the same way. TLC evaluates both to `FALSE`; because
the result is constant-level it declines the invariant with error 2230 rather
than checking it.

## Reached through a set filter

The defect is not confined to the predefined set written directly. Filtering it
first behaves the same way, with Apalache 0.62.2:

```tla
---- MODULE IsFiniteSetFilter ----
EXTENDS Integers, FiniteSets

VARIABLE
\* @type: Int;
step

Init == step = 0
Next == step' = step + 1
Inv == IsFiniteSet({x \in Int : step >= step})
Bound == step <= 5
====
```

```text
State 5: state invariant 0 holds.
The outcome is: NoError
EXITCODE: OK
```

`Inv == ~IsFiniteSet({x \in Int : step >= step})` again reports a counterexample
for the assertion that does hold. TLC does not answer either way: it declines
the operand as one whose finiteness it cannot decide.

```text
Error: Attempted to apply the operator overridden by the Java method
public static tlc2.value.IBoolValue tlc2.module.FiniteSets.IsFiniteSet(tlc2.value.impl.Value),
but it produced the following error:
Attempted to check if expression of form {x \in S : p(x)} is a finite set, but cannot check if S:
Int
is finite.
```

Corpus8 contains two aggregator deviations of this shape, both TLC-fail and
Apalache-pass, with `Nat` in one and `Int` in the other.

## Expected behavior

`Int` and `Nat` are infinite, so `IsFiniteSet` applied to either must evaluate
to `FALSE`. If the bounded checker cannot decide finiteness for a symbolic set
representation, it should report an unsupported operator or an input error, as
it does for `Cardinality` on a non-finite set, rather than answer `TRUE`.

## Impact

This is a soundness defect: the checker reports that a false invariant holds. A
specification whose invariant depends on `IsFiniteSet` of a predefined infinite
set is verified vacuously, and the pass carries no information. The dual case is
equally damaging, since a correct invariant is reported as violated and sends a
user to debug a counterexample for a property that is true.

Both directions are cheap to detect and neither produces a warning, so nothing
in the output distinguishes these runs from a sound verdict.
