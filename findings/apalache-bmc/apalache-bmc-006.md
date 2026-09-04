---
state: open
---

# `CherryPick` cannot select symbolic-set values from a finite set

## Summary

Apalache's bounded checker aborts when bounded `CHOOSE` selects among values
represented as symbolic sets. `CherryPick.pickByOracle` reports `Do not know how
pick an element` and the tool exits with status 255 instead of evaluating the
well-typed finite choice or returning a classified unsupported-input result.

The `corpus2` run contains five instances: four with `PowSet` elements and one
with an `InfSet` element. Observed and reproduced with Apalache 0.62.2, build
`f0dec98`.

## Reproduction

```tla
---- MODULE ChooseSymbolicSet ----

VARIABLE
\* @type: Set(Set(Bool));
x

\* @type: (() => Set(Set(Bool)));
Rhs == CHOOSE s \in {
  SUBSET (DOMAIN [b \in {TRUE} |-> FALSE]),
  SUBSET {TRUE}
} : TRUE

Init == x = Rhs
Next == UNCHANGED x
Inv == TRUE

====
```

Run:

```sh
java -Xmx1g -jar target/apalache.jar check \
  --init=Init --next=Next --inv=Inv --length=0 --no-deadlock \
  ChooseSymbolicSet.tla
```

Apalache reaches `BoundedChecker` and terminates with:

```text
rewriter error: Do not know how pick an element from a set of type:
PowSet[Set(Bool)]
EXITCODE: ERROR (255)
```

## Expected behavior

Bounded `CHOOSE` over this finite two-element set should produce one of its set
values. If selecting a symbolic-set representation is unsupported, Apalache
should reject the expression through its classified unsupported-input path
rather than report an internal rewriting error.

## Impact

The defect prevents checking specifications that choose among powersets or
other symbolic set values. The failure depends on arena representation rather
than the TLA+ type and aborts checking before an invariant result.
