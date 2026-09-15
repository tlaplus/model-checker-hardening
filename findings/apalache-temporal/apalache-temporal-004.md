---
state: open
labels: [apalache]
---

# A function constructor with two bound variables under `~>` crashes the renamer

## Summary

When a function constructor with more than one bound variable, such as
`[a \in {1}, b \in {2} |-> x]`, occurs on either side of `~>` in a temporal
property, `apalache-mc check --temporal` fails in `PreprocessingPass` with
`java.lang.IllegalArgumentException: Variable names should never contain more
than one separator` from `IncrementalRenaming.parseName`, a stack trace and a
request to report an issue. It exits with status 255. The same constructor is
checked under `<>` and `[]`, and a constructor with one bound variable is checked
under `~>`.

Observed with Apalache 0.62.2 (build `f0dec98`).

## Reproduction

`FuzzInput.tla`:

```tla
---- MODULE FuzzInput ----
EXTENDS Integers, Sequences, Apalache
VARIABLE
  \* @type: Int;
  x
Init == x = 0
Next == (x < 2 /\ x' = x + 1) \/ UNCHANGED x
Inv == TRUE
Prop == [a \in {1}, b \in {2} |-> x][<<1, 2>>] = 0 ~> FALSE
====
```

```sh
apalache-mc check --init=Init --next=Next --inv=Inv --temporal=Prop --length=3 FuzzInput.tla
```

```text
Unhandled exception
java.lang.IllegalArgumentException: Variable names should never contain more than one separator
	at at.forsyte.apalache.tla.lir.transformations.standard.IncrementalRenaming$.parseName(IncrementalRenaming.scala:43)
	at at.forsyte.apalache.tla.lir.transformations.standard.IncrementalRenaming$.nameCounterMapFromEx(IncrementalRenaming.scala:72)
	at at.forsyte.apalache.tla.lir.transformations.standard.IncrementalRenaming$.$anonfun$nameCounterMapFromEx$1(IncrementalRenaming.scala:78)
	...
EXITCODE: ERROR (255)
```

The last pass reported before the exception is `PreprocessingPass`, in its
`UniqueRenamer` step. With `--write-intermediate=true`, the output of
`TemporalPass` contains the constructor twice, as
`[ a_1 \in {1}, b_1 \in {2} |-> x ]` (the TLA+ printer writes `$` as `_`): once in
the initial-state encoding of `~>` and once in the loop encoding.

TLC checks the same module with `SPECIFICATION Spec` and `PROPERTY Prop`, where
`Spec == Init /\ [][Next]_x`, and reports that `Prop` is violated.

Varying the property, with the module otherwise unchanged:

| `Prop` | Apalache |
| --- | --- |
| `[a \in {1}, b \in {2} \|-> x][<<1, 2>>] = 0 ~> FALSE` | exit 255, renaming |
| `[a \in {1}, b \in {2} \|-> 0][<<1, 2>>] = x ~> FALSE` | exit 255, renaming |
| `x = 0 ~> ([a \in {1}, b \in {2} \|-> x][<<1, 2>>] = 2)` | exit 255, renaming |
| `[a \in {1} \|-> x][1] = 0 ~> FALSE` | counterexample |
| `<>([a \in {1}, b \in {2} \|-> x][<<1, 2>>] = 0)` | `NoError` |
| `[]([a \in {1}, b \in {2} \|-> x][<<1, 2>>] >= 0)` | `NoError` |
| `(\A a \in {1}, b \in {2}: a + b > x) ~> FALSE` | counterexample |
| `{a + b : a \in {1}, b \in {2}} = {x} ~> FALSE` | `NoError` |
| `LET F(p, q) == [a \in {1}, b \in {2} \|-> p][<<1, 2>>] IN ApaFoldSeqLeft(F, x, <<>>) = 0 ~> FALSE`, with `F` annotated `((Int, Int) => Int)` | counterexample |

Quantifiers and set maps with two bound variables do not fail, nor does the
constructor inside a `LET` operator.

## Corpus evidence

In the `module` corpus23, generated with the `action` and `temporal` categories,
four Apalache crashes report this exception: `1c7b1d5f`, `a840f3c3`, `ae7c4002`
and `fac4ad47`. corpus22 has one, `25c22f27`. The property of every one contains a
function constructor with two bound variables below `~>`. `a840f3c3` was reduced,
by replacing subexpressions of its Apalache JSON IR with literals and
same-typed subexpressions, to a property `P ~> FALSE` whose `P` applies a
constructor `[arg23 \in {}, arg24 \in {} |-> ...]`; the reproduction above was
written from that shape.

## Expected behavior

Apalache checks the property, as it does for the same constructor under `<>` or
`[]`.

## Impact

Leads-to properties over functions of several arguments cannot be checked. The
error is reported as an internal Apalache failure, and the fuzzing workflow
records these inputs as Apalache crashes.
