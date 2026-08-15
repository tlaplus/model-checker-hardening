# `TlaCheckedBuilder.build` overflows on a long valid composition

## Summary

`TlaCheckedBuilder.build` evaluates its deferred builder computation with a
stack-unsafe Scalaz `State.run`. A sufficiently long expression therefore
throws `StackOverflowError` instead of producing IR.

Observed with `org.apalache-mc:tla-ir-java:0.61.1-SNAPSHOT`, Scalaz 7.3.5,
and OpenJDK 25.0.3.

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
