---
state: open
labels: [apalache]
---

# `TlaCheckedBuilder.build` overflows on a long valid composition

## Summary

`TlaCheckedBuilder.build` evaluates its deferred builder computation with a
stack-unsafe Scalaz `State.run`. A sufficiently long expression therefore
throws `StackOverflowError` instead of producing IR.

Observed with `org.apalache-mc:tla-ir-java:0.61.1-SNAPSHOT`, Scalaz 7.3.5,
and OpenJDK 25.0.3.

The Apalache 0.62.2 release source still materializes the builder state with
`State.run`, so this finding remains open.

## Regression test

Add this test to
`tla-ir-java/src/test/scala/org/apalache_mc/tla/jir/TestJavaFacade.scala`:

```scala
test("checked builder materializes long instruction chains without overflowing the stack") {
  val builder = new TlaCheckedBuilder()
  val expression = (0 until 10_000).foldLeft(builder.bool(false)) { (operand, _) =>
    builder.not(operand)
  }

  assert(builder.build(expression).typeTag == Typed(BoolT1))
}
```

Run it with:

```sh
sbt "tla_ir_java / Test / testOnly org.apalache_mc.tla.jir.TestJavaFacade"
```

Before the fix, the new test fails in the final `build` call with:

```text
Exception in thread "main" java.lang.StackOverflowError
    at at.forsyte.apalache.tla.typecomp.subbuilder.BoolBuilder.$anonfun$not$1(...)
    at scalaz.IndexedStateT.apply(StateT.scala:14)
    at scalaz.IndexedStateT.$anonfun$apply$1(StateT.scala:19)
    at scalaz.IdInstances$$anon$1.bind(Id.scala:22)
    at scalaz.IndexedStateT.apply(StateT.scala:14)
    ...
```

`build` should return the typed `TlaEx` without consuming stack proportional to
the number of composed builder instructions.

## Reached from `apalache-mc check`

The model checker builds its encoding with the same builder, so a specification
alone reaches the overflow. An invariant that compares an integer range of a few
thousand elements with the empty set is enough:

```tla
---- MODULE FuzzInput ----
EXTENDS Integers, FiniteSets
VARIABLE
  \* @type: Int;
  x
Init == x = 0
Next == (x < 2 /\ x' = x + 1) \/ UNCHANGED x
Inv == 0 .. 2000 \subseteq {}
====
```

```sh
apalache-mc check --init=Init --next=Next --inv=Inv --length=3 FuzzInput.tla
```

```text
State 0: Checking 1 state invariants
Unhandled exception
java.lang.StackOverflowError
	at at.forsyte.apalache.tla.types.TypeUnifier.unify(TypeUnifier.scala:49)
	at at.forsyte.apalache.tla.typecomp.signatures.FlexibleEquality$.commonSupertype(FlexibleEquality.scala:23)
	...
	at at.forsyte.apalache.tla.typecomp.unsafe.ProtoBuilder.buildBySignatureLookup(ProtoBuilder.scala:23)
	...
EXITCODE: ERROR (255)
```

The failure occurs in `BoundedChecker`. Below `ApalacheInternalBuilder.selectInSet`,
the printed trace, which the JVM cuts at 1,024 frames, repeats
`scalaz.IndexedStateT.apply`, `IndexedStateT.$anonfun$apply$1` and
`IdInstances$$anon$1.bind`: the frame pattern of the regression test above. It was
observed with the Apalache 0.62.2 release launcher, which sets no thread stack
size.

| `Inv` | Apalache 0.62.2 |
| --- | --- |
| `-1000 .. 0 \subseteq {}` | counterexample |
| `0 .. 2000 \subseteq {}` | exit 255, `StackOverflowError` |
| `0 .. 5000 \subseteq {}`, also as `--temporal` property | exit 255, `StackOverflowError` |
| `0 .. 5000 = {}` | exit 255, `StackOverflowError` |
| `{} \subseteq 0 .. 5000` | `NoError` |
| `\A v \in 0 .. 5000: v >= 0` | `NoError` |
| `Cardinality(0 .. 5000) = 5001` | `NoError` |
| `x \in 0 .. 5000` in `Init`, `Inv == x >= 0` | `NoError` |

In the `module` corpus23, one Apalache crash, `d0f6bfa2`, is this overflow: its
temporal property is `-20480 .. 0 \subseteq {}`, and the stack trace starts in
`TypeUnifier.unify` under the same `typecomp` frames.

## Root cause

Apalache represents a checked builder instruction as
`State[TBuilderContext, T]`. The materialization helper executes it with:

```scala
builderState.run(TBuilderContext.empty)._2
```

For `State` over Scalaz's strict `Id`, each bind invokes the next `run`
recursively. The repeating `IndexedStateT.apply` / `Id.bind` frames in the
trace are this deferred instruction chain; the Boolean operation at the top is
only where the thread exhausts its stack.

Scalaz provides the stack-safe evaluator `runRec`, and its `Id` instance
implements `BindRec`. The materialization helper can therefore use:

```scala
builderState.runRec(TBuilderContext.empty)._2
```

Evaluating the failing builder state through `runRec` succeeds even with
`-Xss256k`. The regression test checks the public Java facade and inspects only
the root type tag, so it exercises materialization without introducing a
second recursive traversal of the deep IR tree.
