---
state: open
labels: [apalache]
---

# `UNION` rejects a powerset-valued operand during bounded checking

## Summary

Apalache accepts and typechecks a `UNION` whose operand is a powerset, but the
bounded checker's internal type check rejects the operand's `PowSet` arena
representation. The first instance chooses the sole member of `{SUBSET {}}`;
the expression is well typed and equivalent to `UNION (SUBSET {})`, which is
`{}`. A state variable or a fold that holds `SUBSET S` fails the same way; see
[Other powerset operands](#other-powerset-operands).

Observed in one `corpus3` input and reproduced with Apalache 0.62.2, build
`f0dec98`. See the [`28a21652...` input](../../corpus3/02apa-crash/28a216522c59d522685f553e02e1dcabe25a2a09ca7af515ddf6939b479514ea.cbor)
and its [stacktrace](../../corpus3/02apa-crash/28a216522c59d522685f553e02e1dcabe25a2a09ca7af515ddf6939b479514ea.stacktrace).

## Reproduction

```tla
---- MODULE UnionChoosePowerset ----

VARIABLE
\* @type: Set(Str);
result

\* @type: (() => Set(Str));
Rhs == UNION (CHOOSE sets \in {SUBSET {}} : TRUE)

Init == result = Rhs
Next == UNCHANGED result
Inv == TRUE

====
```

Run:

```sh
java -Xmx1g -jar target/apalache.jar check \
  --init=Init --next=Next --inv=Inv --length=0 --no-deadlock \
  UnionChoosePowerset.tla
```

Snowcat reports that all expressions are typed. The bounded checker then
terminates with:

```text
internal error in type checking: Applying UNION to
CHOOSE sets$1 \in {SUBSET {}} : TRUE of type PowSet[Set(Str)]
EXITCODE: ERROR (255)
```

## Other powerset operands

`SetUnionRule.apply` (`SetUnionRule.scala:37` in `tla-bmcmt` at `3eb15b2`)
accepts only a cell of type `CellTFrom(SetT1(SetT1(_)))`. `SUBSET S` rewrites to
a `PowSet` cell, so every operand that evaluates to that cell is rejected,
whatever expression produced it. `UnionVar.tla`:

```tla
---- MODULE UnionVar ----
VARIABLE
\* @type: Set(Set(Int));
x
Init == x = SUBSET {1, 2}
Next == UNCHANGED x
Inv == UNION x = {1, 2}
====
```

With the command above and Apalache 0.62.3 (`3eb15b2`):

```text
UnionVar.tla:7:8-7:14: internal error in type checking: Applying UNION to x of type PowSet[Set(Int)]
EXITCODE: ERROR (255)
```

The `graph` corpus71 run, with the recursion library, adds 13 Apalache crashes
of this kind. In 11, the operand is a state variable that an action assigned
`SUBSET S` or the library's `SetPowerset(S)`, for example `UNION var0` with
types `PowSet[Set(Bool)]`, `PowSet[Set((Int -> Int))]` and
`PowSet[Set(Set(Int))]`. In one, it is a tuple component `var0[1]`, and in one,
an `ApaFoldSeqLeft` that yields a powerset.
Observed with FuzzTLA `e180a389` and TLC `606dfa4` (not involved).

## Expected behavior

The bounded checker should preserve Snowcat's type for the operand, and
`SetUnionRule` should accept a `PowSet` cell, by expanding it or by rewriting
`UNION (SUBSET S)` to `S`. The first reproduction evaluates to the empty set. Internal arena representations must not
invalidate a source-level type-correct application.

## Impact

A small, well-typed set expression passes every front-end phase but fails before
an invariant result because the bounded checker disagrees with the front-end
typechecker.
