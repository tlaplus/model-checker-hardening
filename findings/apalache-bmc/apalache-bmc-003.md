---
state: open
labels: [apalache]
---

# Equality crashes on symbolic-set arena representations

## Summary

Apalache's bounded checker aborts while comparing values represented by
symbolic sets. It reports `Unexpected equality test over types` even when both
reported arena types are identical.

The original inspected corpus contains three instances. The later `corpus2`
run contains 44: 25 compare the same symbolic representation and 19 compare a
symbolic representation with a materialized set. Observed with Apalache 0.62.0;
both representative reproductions still fail with Apalache 0.62.2, build
`f0dec98`.

## Power-set reproduction

```tla
---- MODULE SymbolicSetEquality ----

VARIABLE
\* @type: Seq(Set(Set(Bool)));
sets

\* @type: (() => Seq(Set(Set(Bool))));
Rhs == <<(SUBSET (DOMAIN [b \in {} |-> FALSE])) \union {}>>

Init == sets = Rhs
Next == UNCHANGED sets
Inv == sets = Rhs

====
```

The module reaches the bounded checker and terminates with:

```text
checker error: Unexpected equality test over types
PowSet[Set(Bool)] and PowSet[Set(Bool)]
```

## Function-set reproduction

The function-set variant uses a quantified expression to materialize the
literal function set before comparing the resulting Boolean state value:

```tla
---- MODULE FunctionSetEquality ----

VARIABLE
\* @type: Bool;
value

\* @type: (() => Set((Bool -> Int)));
Functions == [{} -> {}]

Rhs == \A f \in Functions : FALSE

Init == value = Rhs
Next == UNCHANGED value
Inv == value = Rhs

====
```

Apalache terminates with:

```text
checker error: Unexpected equality test over types
FinFunSet[CellTFrom(Set(Bool)), CellTFrom(Set(Int))]
and CellTFrom(Set((Bool -> Int)))
```

TLC checks both modules without an invariant violation or implementation
exception. The remaining corpus signature is another `PowSet` comparison with
its materialized `CellTFrom` representation.

## Expected behavior and impact

Equality must handle two values of the same TLA+ type even when arena
construction selected different internal representations. If a particular
comparison is unsupported, Apalache should reject it through its classified
unsupported-input path rather than crash.

The defect affects ordinary invariant equality and prevents checking models
that retain powersets or function sets in state.
