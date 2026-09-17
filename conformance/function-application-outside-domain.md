# Function application outside its domain

Observed share: 28.17% of aggregator deviations; TLC failed and Apalache passed.

TLC treats application outside a function's domain as a specification-evaluation
error. Apalache's symbolic encoding can assign an unconstrained value to the
application instead. The expression is semantically undefined, so this is a
checker-policy difference rather than a soundness claim about a defined value.

## Representative MWE

```tla
---- MODULE FunctionOutsideDomain ----
EXTENDS Integers
VARIABLE
\* @type: Int;
result
Init == result = [i \in {1} |-> i][2]
Next == UNCHANGED result
Inv == TRUE
====
```

TLC reports that argument `2` is not in the function domain. Apalache accepts
the typed expression. Generators that require portable positive tests must
guard the application with `2 \in DOMAIN f`.

## Sequences and tuples

A sequence is a function with domain `1..Len(s)`, and indexing it outside that
range is the same undefined application. TLC stores sequences and tuples as
tuple values and reports a different message:

```text
Error: Attempted to access index 2 of tuple
<<1>>
which is out of bounds.
```

for `Init == result = <<1>>[2]`. corpus17, the first corpus whose generator
reads sequences and tuples directly, has 10,582 deviations with this message
(25 with a negative index), for example `<<>>[step]` and `s[0]`.

A known-defect signature does not pay off for this class. On corpus17's 98,819
checked entries, `(FUN_APP (TUPLE) _)` matches 45.3% of the entries with 64.4%
recall, and `(FUN_APP (: _ "Seq(a)") 0)` matches 60.8% with 89.0% recall. Only
about 16% of either's matches fail with this message, and about 81% fail TLC at
all, against a 75.8% base rate. TLC also reports the failure within seconds, so
quarantining would discard many inputs that Apalache still checks while saving
little time.

## Under a temporal property

When the application sits inside a `[][A]_v` property, TLC stores the wrapper
`Error: Evaluating action property Prop failed.` as the entry's detail and prints
the application error only on the following lines. The aggregator keeps a single
line, so the triager leaves these deviations as `NEW`. corpus28 has four:
`6a805821`, `6c69f64a`, `d4308ac1` and `f1512c5e`. On a rerun with TLC commit
`957faa0`, three report `In applying the function <<>>` with an argument outside
its domain, and `d4308ac1` reports `Attempted to access index 0 of tuple <<>>`.
Apalache 0.62.2 (build `f0dec98`), rerun on the IR, reports `NoError` for all
four. Three are `copy` mutants; `f1512c5e` came from
PBT. The FuzzTLA commit is `41bda26`.
