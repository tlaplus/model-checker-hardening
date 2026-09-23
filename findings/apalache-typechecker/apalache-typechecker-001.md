---
state: open
labels: [apalache]
---

# Snowcat generalizes a parameter that only `LET` definitions constrain

## Summary

Snowcat infers `(a) => Int` for `F(n) == LET k == n + 1 IN k`, although `n + 1`
forces `n` to be an integer. A constraint on an enclosing parameter that arises
inside a `LET` definition is solved only locally and never reaches the enclosing
operator. If no other part of the body constrains the parameter, Snowcat leaves it
as a free type variable and generalizes it.

The consequences depend on the mode:

- With the default `--infer-poly=true`, ill-typed calls pass type checking. Apalache
  then either computes an arbitrary value or crashes in the rewriter:
  - `F({TRUE})` evaluates to `-1`;
  - `Abs({TRUE})`, with `Abs(n) == LET a == IF n < 0 THEN -n ELSE n IN a`, raises
    `TBuilderTypeException`.
- With `--infer-poly=false`, Snowcat rejects the monomorphic `F` as polymorphic.

Observed with Apalache 0.62.2 (build `f0dec98`), tla2tools 1.8.0-SNAPSHOT (Maven
snapshot `1.8.0-20260917.033119-76`, tlaplus/tlaplus commit `142d0ba`), and FuzzTLA
`f08b942` with the diff-linked recursion library of
[ADR 0015](../../docs/decisions/0015-diff-linked-libraries.md).

## Reproduction

`LetPoly.tla`:

```tla
---- MODULE LetPoly ----
EXTENDS Integers
Direct(n) == n + 1
ViaLet(n) == LET k == n + 1 IN k
ViaLetIf(n) == LET a == IF n < 0 THEN -n ELSE n IN a
====
```

```sh
apalache-mc typecheck --output=LetPoly.json LetPoly.tla
```

The operator types in `LetPoly.json`:

```text
Direct    (Int) => Int
ViaLet    (d) => Int
ViaLetIf  (a) => Int
```

With `--infer-poly=false`, the same module fails:

```text
[LetPoly.tla:4:1-4:32]: Operator ViaLet has a parameterized type, while polymorphism is disabled: ((d) => Int)
EXITCODE: ERROR (120)
```

An ill-typed call then passes type checking. `LetPolyCall.tla`:

```tla
---- MODULE LetPolyCall ----
EXTENDS Integers
ViaLet(n) == LET k == n + 1 IN k
VARIABLE
  \* @type: Int;
  x
Init == x = ViaLet({TRUE})
Next == UNCHANGED x
Inv == x = 0
====
```

```sh
apalache-mc check --length=0 --inv=Inv LetPolyCall.tla
```

```text
State 0: state invariant 0 violated.
EXITCODE: ERROR (12)
```

The counterexample is `State0 == x = -1`.

- Replacing `ViaLet` with `Direct` gives `Argument {TRUE} should have type Int but
  has type Set(Bool)` and `EXITCODE: ERROR (120)`.
- TLC, with `INIT Init`, `NEXT Next` and `INVARIANT Inv`, reports `Cannot cast
  tlc2.value.impl.SetEnumValue to tlc2.value.impl.IntValue`.
- With `ViaLetIf` in place of `ViaLet`, `apalache-mc check` crashes:

  ```text
  at.forsyte.apalache.tla.typecomp.package$TBuilderTypeException: Operator IF_THEN_ELSE cannot be applied to arguments of types (Bool, Int, Set(Bool))
  ```

Other shapes show the same pattern. The parameter keeps its type only when the body
also constrains it outside the `LET` definitions, as in the first row:

```text
LET k == n + 1 IN k + n             (Int) => Int
LET G(y) == y + n IN G(1)           (i) => Int
LET k == LET j == n + 1 IN j IN k   (d) => Int
LET k == n + 1 IN {k}               (a) => Set(Int)
```

## Cause

`EtcTypeChecker.computeRec` handles `EtcLet` in
`tla-typechecker/src/main/scala/at/forsyte/apalache/tla/typecheck/etc/EtcTypeChecker.scala`.
At Apalache commit `1371658a2`, this is lines 235–345; the version was read, not run.

1. The handler types the definition body with a fresh `letInSolver`. A constraint on
   a variable of the enclosing context, such as `n`'s type `a = Int` from `n + 1`, is
   added to `letInSolver` only.
2. Once that solver finishes, its substitution is applied to the definition's type
   and discarded. The enclosing `solver` never learns `a = Int`.
3. Top-level operators are also typed as `EtcLet` bindings, so this happens at every
   nesting level. The enclosing operator's parameter stays free, and
   `principalDefType.usedNames.filter(solver.isFreeVar)` then quantifies over it.

A fix would propagate the local substitution's bindings of variables that are free
in the enclosing context back to the enclosing solver, for example as equality
clauses, before generalizing.

## Corpus evidence

FuzzTLA typechecks custom-operator libraries with `--infer-poly=true` and trusts the
resulting signatures. In a 10-minute campaign of the recursion library, the generator
therefore passed records, sequences and sets to the integer operators `IntDigitSum`
and `IntTriangle`. Their Apalache definitions have the `ViaLetIf` and `ViaLet`
shapes.

Apalache crashed with `IF_THEN_ELSE cannot be applied to arguments of types (Bool,
Int, …)` on 13 inputs, for example `78698d3e`, which applies `IntDigitSum` to a
sequence of records. After `@type: (Int) => Int;` annotations were added to the
affected operators, a second campaign had no such crash.

## Expected behavior

`ViaLet` and `ViaLetIf` have type `(Int) => Int`, like `Direct`. `ViaLet({TRUE})` is a
type error, and `--infer-poly=false` accepts all three operators.

## Impact

Any operator whose parameter is constrained only inside `LET` definitions is affected.
This is common in operators that compute through local definitions. For example, the
parameter may be clamped or normalized once and then used only through that
definition.

- Under the default setting, Snowcat accepts ill-typed calls of such operators. The
  model checker then reports counterexamples built on arbitrary values, or crashes
  in the rewriter.
- With polymorphism disabled, Snowcat rejects correct specifications. The only
  workaround is to annotate every affected operator with `@type`.
- Tools that use Snowcat's inferred signatures, such as FuzzTLA's custom-operator
  libraries, inherit the unsound types.
