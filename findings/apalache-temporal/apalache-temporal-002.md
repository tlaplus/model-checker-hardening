---
state: open
labels: [apalache]
---

# A nested one-conjunct list in a fold lambda fails type checking under `--temporal`

## Summary

When the body of a `LET` operator passed to `ApaFoldSet` is a bulleted
conjunction list nested in another, such as `/\ /\ a`, and the fold occurs in the
initial predicate or in the temporal property, `apalache-mc check --temporal`
fails in the bounded checker with "internal error in type checking: FoldSet
argument Lam$1 should have the tag ((Bool, Int) => Bool), found Bool", a Java
stack trace and a request to report an issue. It exits with status 255. The same
module is checked without `--temporal`, and the fold is checked with the body
`/\ a`, `(a)` or `a`.

`ApaFoldSeqLeft` with the same nested-list body fails the same way, through a
different guard: the bounded checker reports "Inliner: Unable to unify the
signature Bool of L$1 with the type $callSiteType at call site". See
[the `ApaFoldSeqLeft` variant](#the-same-defect-through-apafoldseqleft).

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

## The same defect through `ApaFoldSeqLeft`

Replacing `ApaFoldSet` with `ApaFoldSeqLeft` reaches a different guard for the
same nested list. The bounded checker reports `Inliner: Unable to unify the
signature Bool of L$1 with the type $callSiteType at call site` and exits 255.
The fold's combinator may also be a module-level operator; the `LET` form is
not required.

`FuzzInput.tla`:

```tla
---- MODULE FuzzInput ----
EXTENDS Integers, Apalache
VARIABLE
  \* @type: Int;
  x
\* @type: (Int, Int) => Int;
L(f, s) == IF (/\ (/\ TRUE)) THEN 0 ELSE 0
\* @type: () => Bool;
Init == x = ApaFoldSeqLeft(L, 0, <<0>>)
\* @type: () => Bool;
Next == UNCHANGED x
\* @type: () => Bool;
Inv == TRUE
\* @type: () => Bool;
Fairness == TRUE
\* @type: () => Bool;
Prop == TRUE
\* @type: () => Bool;
Liveness == Fairness => Prop
====
```

```sh
apalache-mc check --init=Init --next=Next --inv=Inv --temporal=Liveness --length=2 --no-deadlock FuzzInput.tla
```

```text
<unknown>: internal error in type checking: Inliner: Unable to unify the signature Bool of L$1 with the type $callSiteType at call site
EXITCODE: ERROR (255)
```

The crash needs `--temporal` (any property, even `Liveness == TRUE`), a
conjunction list of one conjunct nested in another, and either fold operator;
`/\ TRUE` alone is checked, and three nestings crash like two.

The literal text `$callSiteType` in the diagnostic is a second, smaller
defect: `Inliner.getSubstitution` builds the message as
`s"... $calleeType ..." + "with the type $callSiteType at call site"`, and the
second string is not interpolated, so the actual call-site type never
appears.

corpus29 has two Apalache crashes with this diagnostic,
[`69b942b4...`](../../corpus29/02apa-crash/69b942b4f455b3b540ed3f47da0ac5d93ff20d037f8db158d417378f9644f2cf.cbor)
and
[`d7c2e2fd...`](../../corpus29/02apa-crash/d7c2e2fd2d7d61038323cddd93372bf9ecece5590a4df9df3b4ea0414c254042.cbor).
The triager's signature for this finding matched only the `FoldSet` message,
so both were left unclassified; it now covers this diagnostic as well.
`69b942b4` was reduced from its typed IR JSON to the module above; `d7c2e2fd`
was not reduced. FuzzTLA is `bfc3a25`, the
Apalache build is 0.62.2 (`f0dec98`).

## Expected behavior

The body `/\ /\ a` denotes `a`; Apalache checks the fold as it does with the body
`a`.

## Impact

Specifications that fold with such a lambda cannot be checked for temporal
properties. The error is reported as an internal Apalache failure, and the
fuzzing workflow records these inputs as Apalache crashes.
