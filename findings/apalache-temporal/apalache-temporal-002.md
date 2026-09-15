---
state: open
labels: [apalache]
---

# A nested one-conjunct list in an `ApaFoldSet` lambda fails type checking under `--temporal`

## Summary

When the body of a `LET` operator passed to `ApaFoldSet` is a bulleted
conjunction list nested in another, such as `/\ /\ a`, and the fold occurs in the
initial predicate or in the temporal property, `apalache-mc check --temporal`
fails in the bounded checker with "internal error in type checking: FoldSet
argument Lam$1 should have the tag ((Bool, Int) => Bool), found Bool", a Java
stack trace and a request to report an issue. It exits with status 255. The same
module is checked without `--temporal`, and the fold is checked with the body
`/\ a`, `(a)` or `a`.

Observed with Apalache 0.62.2 (build `f0dec98`).

## Reproduction

`FuzzInput.tla`:

```tla
---- MODULE FuzzInput ----
EXTENDS Integers, Apalache
VARIABLE
  \* @type: Int;
  x
Fold ==
  LET \* @type: ((Bool, Int) => Bool);
  Lam(a, b) ==
    /\ /\ a
  IN ApaFoldSet(Lam, TRUE, {1})
Init == x = 0
Next == (x < 2 /\ x' = x + 1) \/ UNCHANGED x
Inv == TRUE
Prop == <>Fold
====
```

```sh
apalache-mc check --init=Init --next=Next --inv=Inv --temporal=Prop --length=3 FuzzInput.tla
```

```text
State 0: state invariant 0 holds.
State 0: state invariant 1 holds.
Step 0: picking a transition out of 1 transition(s)
<unknown>: internal error in type checking: FoldSet argument Lam$1 should have the tag ((Bool, Int) => Bool), found Bool.
at.forsyte.apalache.infra.AdaptedException: <unknown>: internal error in type checking: FoldSet argument Lam$1 should have the tag ((Bool, Int) => Bool), found Bool.
	at at.forsyte.apalache.infra.passes.PassChainExecutor.run(PassChainExecutor.scala:46)
EXITCODE: ERROR (255)
```

Varying the lambda body and the position of `Fold`:

| Body of `Lam` | `Init` | `Inv` | `Prop` | `--temporal=Prop` | Apalache |
| --- | --- | --- | --- | --- | --- |
| `/\ /\ a` | `x = 0` | `TRUE` | `<>Fold` | yes | exit 255, type checking |
| `/\ /\ a` | `x = 0` | `TRUE` | `Fold` | yes | exit 255, type checking |
| `/\ /\ a` | `x = 0 /\ Fold` | `TRUE` | `<>(x = 2)` | yes | exit 255, type checking |
| `/\ /\ a` | `x = 0` | `Fold` | `<>(x = 2)` | yes | counterexample |
| `/\ /\ a` | `x = 0 /\ Fold` | `TRUE` | `TRUE` | no | `NoError` |
| `/\ a` | `x = 0` | `TRUE` | `<>Fold` | yes | `NoError` |
| `(a)` | `x = 0` | `TRUE` | `<>Fold` | yes | `NoError` |
| `a` | `x = 0` | `TRUE` | `<>Fold` | yes | `NoError` |

## Corpus evidence

In the `module` corpus22, generated with the `action` and `temporal` categories,
25 Apalache crashes report "FoldSet argument ... should have the tag ..., found
Bool" from the bounded checker. The named lambda is defined in `Init` in 11 of
them, in the property in 10, and in `Next` in 4. `36cd5261` was reduced, by replacing
subexpressions of its Apalache JSON IR with literals, to a property
`ApaFoldSet(Lambda26, FALSE, {})` whose lambda body is a one-element conjunction
nested in another; the crash persisted only while that nesting was kept. The
other crashes were not reduced, and the `Next` placements were not reproduced:
a fold with `/\ /\ a` in `Next` was checked without the crash.

## Expected behavior

The body `/\ /\ a` denotes `a`; Apalache checks the fold as it does with the body
`a`.

## Impact

Specifications that fold with such a lambda cannot be checked for temporal
properties. The error is reported as an internal Apalache failure, and the
fuzzing workflow records these inputs as Apalache crashes.
