# Collection sizes

> **Status:** Implemented. [ADR 0011](../decisions/0011-collection-base-size.md)
> records the design and the evaluation that chose the default base size of 3.

Generated modules rarely hold non-empty collections in a state: a starved
expression falls back to `{}`, `<<>>` or a function over an empty domain, and
random input rarely continues a literal past one element. A *base size* makes
collections default to a configured size. Input bytes then make each collection
smaller or larger than the base.

## 1. Configuration

```toml
[generator]
max_collection_size = 8
# Size of a set or sequence literal, and of a collection terminal, when the input is exhausted.
collection_base_size = 3
# Input bytes move a collection's size within base ± spread.
collection_size_spread = 4
# Maximum atoms in one generated collection value, over all nesting levels.
max_value_atoms = 64
```

| Key | Meaning |
| --- | --- |
| `collection_base_size` | Size a collection decodes to without input. `0` keeps terminals empty. Must not exceed `max_collection_size`. |
| `collection_size_spread` | How far input bytes move a size from the base, in `0..127`. |
| `max_value_atoms` | Bound on the scalars inside one collection literal or terminal; nested collections share it. |

## 2. What changes in a module

With `collection_base_size = 3`, a starved expression of type `Set(Int)` becomes
`{1, 2, 3}` instead of `{}`, a `Seq(Str)` becomes `<<"1", "2", "3">>`, and a
function of type `Int -> Bool` becomes `[x \in {1, 2, 3} |-> FALSE]`. A set of
Booleans has at most two elements, and nested collections share
`max_value_atoms`. A set or
sequence literal drawn from input has between `base − spread` and
`base + spread` elements, clamped to `1..max_collection_size`.

Only the elements of values follow the base. The number of `Next` disjuncts,
`CASE` arms, `LET` definitions, conjuncts and record fields is drawn as before.

## 3. Choosing a base

Larger bases put larger values into states, and make `SUBSET` and function sets
exponentially more expensive for both checkers. Start with a base of 3 to 6 and
compare, per corpus, the TLC failures in `Init`, `maxCardinality`,
`maxStateNodes` and timeouts ([exploration-metrics manual][metrics],
[corpus-database manual][database]).

Changing any of the three keys reinterprets stored inputs: a corpus must keep
the values it was created with.

[metrics]: exploration-metrics.md
[database]: corpus-database.md
