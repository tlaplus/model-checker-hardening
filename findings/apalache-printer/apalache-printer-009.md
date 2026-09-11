---
state: open
labels: [apalache]
---

# `PrettyWriter` lets a nested `CASE` absorb the enclosing `CASE`'s later arms

## Summary

Apalache's `PrettyWriter` omits parentheses when a `CASE` expression is printed
as the value of a `CASE` arm. A `CASE` arm list extends to the right, so the
inner `CASE` absorbs every remaining arm of the outer one and the printed source
no longer represents the input IR tree.

This is the class of defect that
[`apalache-printer-007`](apalache-printer-007.md) fixed for `LET` bodies.
Observed with `org.apalache-mc:tla-io_2.13:0.62.3-SNAPSHOT`.

## Reproduction

Build a tree whose second arm belongs to the *outer* `CASE`, so that the label
lies outside the `CHOOSE`:

```java
var innerCase = builder.caseSplit(pair(builder.bool(true), builder.bool(true)));
var choose = builder.choose(builder.name("c", BoolT1$.MODULE$), innerCase);
var outer = builder.caseSplit(
        pair(builder.bool(true), choose),
        pair(builder.bool(false), builder.label(builder.bool(true), "lab")));
```

The tree is

```text
CASE TRUE → (CHOOSE c : (CASE TRUE → TRUE)) □ FALSE → (lab ∷ TRUE)
```

but `PrettyWriter` renders it undelimited:

```tla
Op == CASE TRUE -> CHOOSE c : CASE TRUE -> TRUE [] FALSE -> lab :: TRUE
```

Save that module and run the parser from the pinned distribution:

```sh
java -cp apalache.jar tla2sany.SANY T.tla
```

```text
Semantic errors:

*** Errors: 1

line 2, col 61 to line 2, col 71 of module T

Label lab must contain formal parameter `c'.
```

That diagnostic is the proof. TLA+ requires a label to declare exactly the
identifiers introduced by binders whose scope contains it, so `lab` can only be
required to declare `c` if it lies inside the scope of `CHOOSE c`. The outer
`CASE` has therefore lost its second arm to the inner `CASE`, which sits inside
the `CHOOSE`. Parenthesizing the arm value restores the intended tree and SANY
accepts it:

```tla
Op == CASE TRUE -> (CHOOSE c : (CASE TRUE -> TRUE)) [] FALSE -> (lab :: TRUE)
```

A `CHOOSE` arm value alone does not reproduce the absorption. In

```tla
Op == CASE TRUE -> CHOOSE c : TRUE [] FALSE -> lab(c) :: TRUE
```

SANY reports `Illegal parameter c of label 'lab'`, so there `c` is correctly out
of scope. The nested `CASE` is the absorbing construct.

## Expected behavior

`PrettyWriter` must delimit a `CASE` expression whenever it is printed as an
operand whose surrounding syntax can extend across the arm list, as it now does
for `LET`. A regression test should compare the reparsed tree rather than merely
assert that SANY accepts the output.

## Impact

Semantic source corruption. The parser and TLC receive `PrettyWriter` output
while Apalache receives typed IR JSON, so wherever this shape occurs the two
checkers are not checking the same specification and any resulting conformance
deviation is an artifact rather than a difference between the checkers.

It is also the one shape that keeps FuzzTLA's guarantee that every generated
module parses from holding, even under the shipped generator configuration: a
label correctly generated outside a binder's scope is reported as missing that
binder's name, because the source SANY reads is not the tree the generator
built. A 100-entry `fuzztla run --how=pbt --seed=7921605275006395529` produces
one such entry, whose IR has a `CASE ... OTHER` arm whose value is a `CHOOSE`
with a `CASE` body.
