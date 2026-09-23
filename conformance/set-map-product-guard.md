# Set-map guard over duplicate elements

Observed in corpus47: 1 of its 126 Apalache crash outcomes, `f4eead9b`.

Apalache refuses a set map `{e : x1 \in S1, ..., xn \in Sn}` when the
product of the element counts of `S1`, ..., `Sn` exceeds 1,000,000. It exits
255 with `rewriter error: Too many elements to enumerate: N`. TLC has a guard of
the same size, `Attempted to construct a set with too many elements
(>1000000)`, so a map over a product that is really this large fails in both
tools. The two tools differ when a bound set is itself the result of a map.
Apalache counts that set's elements before duplicates are removed, while TLC
counts its distinct values. A set with two values can then count as thousands of
elements in Apalache, and a small product exceeds the guard.

The guard is `IntTupleIterator` in `at.forsyte.apalache.tla.bmcmt.util`, which
`MapBase.rewriteSetMapManyArgs` builds over the element pointers of the bound
sets. The limit is `Limits.MAX_PRODUCT_SIZE = 1000000` in
`at.forsyte.apalache.tla.bmcmt`. The counts below show that a map result keeps
one element per tuple of its bound sets, even when tuples map to equal values.
This is an intentional resource guard, like the
[function-set expansion guard](function-set-expansion-guard.md), not an
unhandled exception.

Observed with Apalache 0.62.2 (build `f0dec98`), TLC commit `142d0ba`
(tla2tools `1.8.0-20260917.033119-76`), and FuzzTLA `d943ec2`.

## Representative MWE

`MapNested.tla`:

```tla
---- MODULE MapNested ----
EXTENDS Integers
VARIABLE
  \* @type: Set(Str);
  x
Init == x = { a : a \in { a : a \in {"a", "b"}, b \in 1..1000 }, c \in 1..1000 }
Next == UNCHANGED x
Inv == x = {"a", "b"}
====
```

```sh
apalache-mc check --length=0 --inv=Inv MapNested.tla
java -cp tla2tools.jar tlc2.TLC -config MapNested.cfg MapNested.tla
```

with `MapNested.cfg` naming `INIT Init`, `NEXT Next` and `INVARIANT Inv`.

| Tool | Result |
| --- | --- |
| Apalache | `rewriter error: Too many elements to enumerate: 2000000`, exit 255 |
| TLC | `Model checking completed. No error has been found.` |

The inner map equals `{"a", "b"}`. Apalache counts it as 2 × 1,000 = 2,000
elements, so the outer product is 2,000 × 1,000 = 2,000,000. TLC's outer product
is 2 × 1,000.

## Through a fold

Generated modules reach the guard through a fold whose combinator maps the
accumulator with further bound variables. Each step multiplies the accumulator's
count:

```tla
---- MODULE MapFold ----
EXTENDS Integers, Apalache
VARIABLE
  \* @type: Set(Str);
  x
\* @type: (Set(Str), Int) => Set(Str);
F(acc, i) == { a : a \in acc, b \in 1..100 }
Init == x = ApaFoldSet(F, {"a", "b"}, {1, 2, 3})
Next == UNCHANGED x
Inv == x = {"a", "b"}
====
```

Apalache reports `Too many elements to enumerate: 2000000` in the third step:
the accumulator counts 2 × 100 × 100 elements, and the map multiplies that by
100. TLC completes with no error, and `x` is `{"a", "b"}` after every step.

## Boundary

| `Init` | Apalache | TLC |
| --- | --- | --- |
| `x = { a : a \in 1..1000, b \in 1..1001 }` | `Too many elements to enumerate: 1001000` | `Attempted to construct a set with too many elements (>1000000)` |
| `MapNested` | `Too many elements to enumerate: 2000000` | no error |
| `MapFold` | `Too many elements to enumerate: 2000000` | no error |

The first row is not a deviation: the product has 1,001,000 distinct tuples, and
both tools refuse it.

## Corpus evidence

In corpus47's `f4eead9b`, the next-state action quantifies over

```tla
ApaFoldSet(Lambda22, { "value1_OF_MODEL", "value2_OF_MODEL" }, { 1, 2, 3 })
```

where `Lambda22(parameter20, parameter21)` maps `mapped23 \in parameter20` with
five more bound variables over sets of 2, 3, 3, 2 and 3 elements, 108
combinations. The accumulator grows from 2 elements to 216 and to 23,328.
The third step exceeds the guard at 23,328 × 108 = 2,519,424, the count in the
diagnostic. The map returns `mapped23`, so the accumulator always equals the
initial two-element set. The workflow does not run TLC after an Apalache crash.
Rerun with `SPECIFICATION Spec` and `INVARIANT Inv`, TLC reports no error in 6
distinct states.

The recall-first database quarantines these shapes with the signature
`set-map-duplicate-product`. It matches a fold whose combinator contains a set
map with at least two bound variables, and a set map with at least two bound
variables whose first or second bound set contains such a map.
