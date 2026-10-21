---
state: open
labels: [tlc]
---

# Formatting a function-domain error takes time exponential in the nesting of failing lazy functions

## Summary

When TLC applies a function outside its domain, it builds the error message
before throwing: `FcnLambdaValue.apply` formats the function with
`toString()`. For a lazy function, `toString()` evaluates every value of the
function and discards any exception. If a value is itself a lazy function whose
body raises another domain error, that error formats its own function the same
way. The work grows with the product of the domain sizes along the nesting, so
a nesting of depth `N` over three-element domains formats `3^N` messages. The
message that TLC finally prints is a few lines long: every failing value is
shown as `<expression ...>`, and all of that evaluation is discarded.

In the reproduction below, TLC needs 0.5 seconds to report the error at depth
8 and 62 seconds at depth 14. The same error, reached without the failing
inner functions, is reported in 0.25 seconds at depth 20. A generated
specification with one reachable state exceeded FuzzTLA's 30-second limit for
both TLC configurations before TLC printed the error.

The slowdown happens in TLC's first evaluation, with the ordinary `FastTool`,
before any output. It is separate from [tlc-018](../TLC/tlc-018.md), in which
the replay that collects the error's call stack grows after an exception it
discards. The fixes proposed there would not remove it. Once the first pass
ends, tlc-018 applies as well: the replay repeats the evaluation under
`CallStackTool`, and its call stack grows with it.

Observed with the TLC snapshot bundled in FuzzTLA, TLC commit `932d35e`
(tla2tools `1.8.0-20261015.150947-106`), FuzzTLA `c524cde`, and Apalache
0.62.3 (`3eb15b2`), on OpenJDK 25.0.4, Linux aarch64.

## Reproduction

`MessageCascade.tla`:

```tla
---- MODULE MessageCascade ----
EXTENDS Naturals
CONSTANT N
VARIABLE x
RECURSIVE G(_)
G(k) == IF k = 0 THEN [s \in {1} |-> 0][2]
        ELSE [s \in {1, 2, 3} |-> [t \in {1} |-> G(k - 1)]][4]
Init == x = 0
Next == x' = G(N)
====
```

`MessageCascade.cfg`:

```text
CONSTANT N = 12
INIT Init
NEXT Next
```

```sh
java -XX:+UseParallelGC -Xmx2g -cp tla2tools.jar tlc2.TLC \
  -workers 1 -noGenerateSpecTE -config MessageCascade.cfg MessageCascade
```

`4` is not in the domain `{1, 2, 3}`. To format the function, TLC evaluates
its three values, the inner functions `[t \in {1} |-> G(k - 1)]`. Printing each
inner function evaluates `G(k - 1)`, which fails in the same way one level
down. TLC reports the error and exits 75:

```text
Error: TLC threw an unexpected exception.
This was probably caused by an error in the spec or model.
See the User Output or TLC Console for clues to what happened.
The exception was a java.lang.RuntimeException
: In applying the function
<<[t \in {1} |-> <expression line 7, col 50 to line 7, col 57 of module MessageCascade>], [t \in {1} |-> <expression line 7, col 50 to line 7, col 57 of module MessageCascade>], [t \in {1} |-> <expression line 7, col 50 to line 7, col 57 of module MessageCascade>]>>,
the first argument is:
4
which is not in its domain.
```

Time from the start of TLC until it prints this error, one worker, 2 GB heap:

| `N` | Outer domain `{1, 2, 3}` | Outer domain `{1, 2}` |
|---:|---:|---:|
| 8 | 0.5 s | |
| 10 | 1.2 s | 0.4 s |
| 11 | 2.8 s | |
| 12 | 6.8 s | |
| 13 | 20.4 s | 0.5 s |
| 14 | 62.4 s | |
| 16 | | 1.8 s |
| 17 | | 3.3 s |
| 18 | | 7.5 s |
| 19 | | 12.9 s |

Each level multiplies the time by the size of the outer domain, about 3 and
about 2. As a control, replacing `[4]` by `[1][1]` makes every application
except the innermost one succeed. TLC then reports the same domain error of
the innermost function, through the same `N` levels of recursion, in 0.25
seconds at `N = 13` and at `N = 20`.

The output after the error also grows as `3^N`. With the replay of tlc-018,
TLC prints 2,031 lines at `N = 4` and 1,535,073 lines (100 MB) at `N = 10`.

## Cause

From `tlatools/org.lamport.tlatools/src` at `932d35e`:

1. `FcnLambdaValue.apply` (`FcnLambdaValue.java:189-191`) builds the message
   eagerly, as `"In applying the function\n" + Values.ppr(this.toString()) +
   ...`, and passes it to `Assert.fail`.
2. `FcnLambdaValue.toString(StringBuffer, int, boolean)`
   (`FcnLambdaValue.java:775-782`) calls `toFcnRcd()` whenever
   `TLCGlobals.expand` is set, which is the default. `toFcnRcd()` evaluates
   the body for every element of the domain. On failure, `toString` catches
   the `Throwable` and prints `<expression ...>` instead.
3. `toFcnRcd()` stores its result in `fcnRcd` only when every body evaluates.
   A failed conversion is not remembered, so every later formatting of the same
   function evaluates its bodies again.
4. A body that applies a function outside its domain enters step 1 again,
   before the exception reaches the `catch` of step 2.

The message of a function whose values are `d` lazy functions therefore formats
`d` messages one level down, all of which are discarded.

In generated specifications, the nesting comes from `ApaFoldSeqLeft` and
`ApaFoldSet`, which TLC evaluates through their recursive definitions in
`Apalache.tla`. A combinator that returns a lazy function over its accumulator
nests the accumulator of the previous step inside the next one. TLC passes the
accumulator as a `LazyValue`, which caches only successful evaluations
(`LazyValue.getValue`), so every inner function that refers to a failed
accumulator evaluates it again.

## Corpus evidence

The `module` corpus67 run, with the
[recursion library](../../docs/manual/recursive-operators.md) and the checkers
TLC with one worker and TLC with `n` workers, has one entry with this cause,
`5319b006`. Both checkers time out after 30 seconds, and the stored
diagnostic is only `TLC worker timed out after PT30S`.

The specification has one reachable state. Its `Next` folds a three-element
sequence with `ApaFoldSeqLeft`, whose combinator folds another three-element
sequence. The inner combinator builds

```tla
VariantGetUnsafe("Tag34", Variant("Tag34",
  [terminalArg32 \in {"1", "2", "3"} |->
    << [terminalArg33 \in { ...three records... } |->
          [field29 |-> ..., field32 |-> parameter28]] >>]))[""]
```

where `parameter28` is the inner accumulator. `""` is not in the domain
`{"1", "2", "3"}`, so every step of the inner fold fails. Formatting each
failure evaluates the inner functions over `parameter28`, which is the failed
result of the previous step.

A standalone rerun, one worker and a 2 GB heap, prints the domain error after
51.5 seconds. The printed message is 633 bytes, and shows the three values as
lazy functions with `<expression line 114, col 31 to line 129, col 56 of
module FuzzInput>` bodies. Thread dumps during this phase show the worker in
`Tool.getNextStates`, under `FcnLambdaValue.apply` (`FcnLambdaValue.java:189`),
`FcnLambdaValue.toString`, `FcnLambdaValue.toFcnRcd`, `SetEnumValue.normalize`
and `tlc2.pprint`. After the error, TLC prints the one-state behavior, replays
the step with `CallStackTool` as described in tlc-018, and runs out of heap in
`CallStack.toString` 15 seconds later. With TLC's default heap of 5.3 GB on this
machine, the run ends the same way after 1 minute 53 seconds.

The triager files `5319b006` under `TIMEOUT`: when corpus67 ran, a timeout
stored no TLC output. Since [ADR 0021](../../docs/decisions/0021-timeout-diagnostics.md),
a timeout's sidecar holds the stacks of TLC's working threads, and the triager
labels this finding by a frame pair that persists for the whole first pass:
`Value.toString` called from `FcnLambdaValue.apply`, without a `CallStackTool`
frame. Replayed through the TLC worker, `5319b006` stores that pair 80 frames
below the top of the TLC worker thread, and is labelled `tlc-performance-002`.

## Expected behavior

The time to report an evaluation error is bounded by the evaluation that
raised it plus the size of the printed message. Formatting a value for an error
message does not evaluate expressions whose failure it then discards. For
example, the message could show lazy function values unexpanded, as it already
does when their evaluation fails, or the message could be built only when it is
printed.

## Impact

An ordinary evaluation error, which TLC diagnoses correctly, can take minutes
to report. The specification can be small and have one reachable state.
Users and automated workflows that limit TLC's run time record a timeout
instead of the error, and the timeout carries no diagnostic that points to the
error. The cost appears only when the evaluation fails: the same specification
without the failing application is checked at once.
