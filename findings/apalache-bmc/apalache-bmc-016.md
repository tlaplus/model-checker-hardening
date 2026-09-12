---
state: open
labels: [apalache]
---

# `\union` with `Int` or `Nat` silently drops the infinite operand

## Summary

Apalache evaluates `S \union Nat` and `S \union Int` as `S`. The infinite
operand contributes nothing and no warning is emitted, so a membership test, an
equality, a cardinality, a finiteness test and a bounded quantifier over the
union all answer as if the infinite set were empty. `UNION {Nat, S}` behaves the
same way, and `Nat \union Int` evaluates to `{}`.

`step \in Nat` on its own is answered correctly, so the defect is specific to
the union, not to the representation of `Nat`. It is a soundness defect in both
directions: Apalache reports a counterexample for the true invariant
`step \in Nat \union {1}` and proves the false invariant
`Nat \union {1} = {1}`.

This is the same shape of failure as
[`apalache-bmc-015`](apalache-bmc-015.md), where a function constructed over an
infinite domain gets an empty `DOMAIN`, and it compounds
[`apalache-bmc-007`](apalache-bmc-007.md), which makes `IsFiniteSet` answer
`TRUE` for an infinite set: here the union hides the infinite operand before
`IsFiniteSet` ever sees it.

Observed with Apalache 0.62.2, build `f0dec98`. The `corpus10` run contains six
aggregator deviations whose invariant unions a finite set with `Int` or `Nat`,
in both directions: four are a TLC counterexample against an Apalache pass, one
is the reverse, and one is a TLC finiteness failure against an Apalache
counterexample. The
[`4534b84d...` input](../../corpus10/03aggregator-fail/4534b84d099a3022906e63677f1314ca1d99bee523f3d56c84ccf6b05fab4cd9.cbor)
is the smallest: its invariant reduces to
`(step \in Nat \union DOMAIN [t \in {} |-> 0]) <=> var0` with `var0 = FALSE`
in every reachable state. The left operand is true, so the invariant is false in
every state and TLC reports it; Apalache passes. A literal `{}` as the right
operand is the one case Apalache answers correctly -- `step \in Nat \union {}`
holds -- so the module below uses `{1}`, which is the shortest form that
exposes the defect.

The `corpus14` run adds one more, again a TLC counterexample against an Apalache
pass. Its invariant is `step \notin ({f \in {} : var1} \union Nat)`, where the
filter is empty, so the union is `Nat` and `step` is in it for every reachable
state. TLC reports the violation; Apalache answers the membership as if `Nat`
were empty and passes. See the
[`76966998...c108b.cbor` input](../../corpus14/03aggregator-fail/769669983a0d82373fb6e77fd5027c9e0fcd30dbc63f028caba2d4f4608c108b.cbor).
Note that the empty operand here is a *computed* empty set on the left of the
union, which does not shield the defect the way a literal `{}` on the right does.

## Reproduction

Save this module as `UnionWithNat.tla`:

```tla
---- MODULE UnionWithNat ----
EXTENDS Integers

VARIABLE
\* @type: Int;
step

Init == step = 0
Next == step' = step + 1
Inv == step \in Nat \union {1}
Bound == step <= 5
====
```

Every reachable state has `step \in 0..5`, so `Inv` holds. Apalache disagrees at
the initial state:

```sh
java -jar target/apalache.jar check \
  --init=Init --next=Next --inv=Inv --length=5 UnionWithNat.tla
```

```text
State 0: Checking 1 state invariants
State 0: state invariant 0 violated.
Found 1 error(s)
The outcome is: Error
EXITCODE: ERROR (12)
```

TLC checks the same module with `INIT Init`, `NEXT Next`, `INVARIANT Inv`,
`CONSTRAINT Bound` and reports `Model checking completed. No error has been
found.`

## What Apalache believes about the union

Each invariant below is checked in the same module, replacing `Inv` (the rows
using `Cardinality` and `IsFiniteSet` also extend `FiniteSets`). The answers are
consistent with `Nat \union {1}` being the finite set `{1}`, and every one of
them is wrong:

| Invariant | Apalache | Correct |
|---|---|---|
| `step \in Nat \union {1}` | violated | holds |
| `0 \in Nat \union {1}` | violated | holds |
| `Nat \union {1} = {1}` | holds | violated |
| `Cardinality(Nat \union {1}) = 1` | holds | violated |
| `IsFiniteSet(Nat \union {1})` | holds | violated |
| `\A x \in Nat \union {1} : x = 1` | holds | violated |
| `\E x \in Nat \union {1} : x = 7` | violated | holds |
| `step \in UNION {Nat, {1}}` | violated | holds |
| `Nat \union Int = {}` | holds | violated |
| `step \in Nat \union DOMAIN [t \in {} \|-> 0]` | violated | holds |

`Int` behaves like `Nat`. The last row is the corpus form: an operand that is
empty but not written as the literal `{}` is dropped like any other, while the
literal `{}` alone is answered correctly.

The union also suppresses the diagnostic Apalache produces for the infinite set
written directly. `step >= Cardinality(Nat)` is rejected with `Input error (see
the manual): Cardinality expected a finite set, found: InfSet[CellTFrom(Int)]`,
which [`apalache-cli-001`](../apalache-cli/apalache-cli-001.md) records;
`step >= Cardinality(Nat \union {1})` is answered, with the union counted as one
element. `IsFiniteSet(Nat)` answers `TRUE` already, which is
[`apalache-bmc-007`](apalache-bmc-007.md).

The neighbouring set operators fail loudly instead of answering wrongly:
`Nat \intersect Int` and `Nat \ {1}` both reach
`scala.NotImplementedError: A set filter over InfSet[CellTFrom(Int)] is not
implemented`, which is [`apalache-bmc-001`](apalache-bmc-001.md). `\union` is
the operator that returns an answer.

## Expected behavior

`S \union Nat` contains every element of `S` and every natural number. If the
bounded checker cannot represent the union of a finite set with an infinite one,
it must report an unsupported operator or an input error, as it does for
`Cardinality` of a non-finite set, rather than answer as if the infinite operand
were empty.

## Impact

This is a soundness defect with no warning attached. A specification that writes
`x \in AllowedIds \union Nat`, or any union with `Int` or `Nat`, is verified
against a strictly smaller set than the one it names: real violations outside
the finite operand are missed, and true invariants are reported as violated,
sending users to debug a counterexample for a property that holds. Because the
union also makes `IsFiniteSet` and `Cardinality` answer instead of diagnosing,
it silently defeats the checks that would otherwise reveal the infinite set.
