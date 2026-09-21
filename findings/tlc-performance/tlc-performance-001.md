---
state: open
labels: [tlc]
---

# A terminating fold over 220 elements exhausts TLC's Java stack

## Summary

TLC overflows a 1 MB Java stack while evaluating a terminating sequence fold
over 220 elements. A reduced specification has one reachable state and an
invariant whose fold returns its initial accumulator unchanged. It fails while
checking the initial state, before any state-space exploration. Increasing the
stack to 4 MB makes it pass.

The original generated specification has 12 reachable states, five possible
non-stuttering transitions, and sequences of at most 220 elements. It overflows
with 512 KB, 1 MB, and 2 MB stacks on the tested JVM; with 4 MB it checks all
states successfully. Replacing the fold with its equivalent result makes the
same model pass with a 512 KB stack.

This is a stack-efficiency and robustness finding, not evidence of a wrong
verification verdict. The diagnostic suggests an incorrect recursive definition
and infinite recursion, although the recursion here terminates after one call
per sequence element. Apalache checks the original typed input successfully.

Reproduced with FuzzTLA commit `275f5d641f4a1392dfa7c200d0bac80bba1ff8e2`,
TLC commit `142d0ba` (tla2tools Maven snapshot
`1.8.0-20260917.033119-76`), and Apalache 0.62.2, commit `f0dec98`.
The local tests used Ubuntu OpenJDK `25.0.4+7-1-24.04-Ubuntu` on Linux aarch64.

## Reproduction

The reduced input inlines the recursive definition of `ApaFoldSeqLeft` as
`Fold`, so it needs only TLC's standard modules.

`FuzzInput.tla`:

```tla
---- MODULE FuzzInput ----
EXTENDS Integers, Sequences
RECURSIVE Fold(_, _, _)
Fold(Op(_, _), acc, seq) ==
    IF seq = <<>> THEN acc
    ELSE Fold(Op, Op(acc, Head(seq)), Tail(seq))
VARIABLE x
Init == x = 0
Next == UNCHANGED x
Keep(a, b) == [field2 |-> a]["field2"]
Inv == Fold(Keep, [field1 |-> x], [i \in 1..220 |-> ""]).field1 >= x
Spec == Init /\ [][Next]_x
====
```

`FuzzInput.cfg`:

```text
SPECIFICATION Spec
INVARIANT Inv
```

With the TLC distribution's `tla2tools.jar` on the classpath:

```sh
java -XX:+UseParallelGC -Xmx512m -Xss1m -cp tla2tools.jar \
  tlc2.TLC -workers 1 -cleanup FuzzInput
```

The reduced input exits 75:

```text
Computing initial states...
Error: This was a Java StackOverflowError. It was probably the result
of an incorrect recursive function definition that caused TLC to enter
an infinite loop when trying to compute the function or its application
to an element in its putative domain.
While working on the initial state:
x = 0

Error: The error occurred when TLC was evaluating the nested
expressions at the following positions:
    The error call stack is empty.

1 states generated, 1 distinct states found, 1 states left on queue.
```

Changing only `-Xss1m` to `-Xss4m` exits 0, with two generated states and one
distinct state. The fold terminates because every recursive call removes one
element. `Keep(a, b) = a`, so the fold returns `[field1 |-> x]` and the invariant
is equivalent to `x >= x`.

## Original input and controls

The source is corpus38 input
`ee1e1d3798647fcd1efaa3c4afda5449d42cb3c17f7a7c8c7153d70b2435a781`, stored in
`02tlc-crash` and `02apa-pass`. Its TLA+ export is byte-for-byte identical to
the investigated `/tmp/c38new/run-soe/FuzzInput.tla`.

Its initial sequence has length 1 or 3. Each non-stuttering transition doubles
the sequence and appends four elements, while incrementing `step`, initially 0
and bounded by 5. Thus `L(k) = (L(0) + 4) * 2^k - 4`; the two branches end at
lengths 156 and 220. The invariant uses `ApaFoldSeqLeft` with an accumulator
`[field1 |-> step]` and a combinator that returns its accumulator through a
record construction and field selection. The surrounding tuple and `Head`
select this fold result, making the invariant equivalent to `step >= step`.

All rows below used one TLC worker and a 512 MB maximum heap:

| Input | Java stack | Result |
| --- | --- | --- |
| Original | 512 KB | stack overflow, exit 255 |
| Original | 1 MB | stack overflow, exit 255 |
| Original | 2 MB | stack overflow, exit 255 |
| Original | 4 MB | pass, 24 generated states, 12 distinct states |
| Original with `Inv == step >= step` | 512 KB | pass, same state counts |
| One-state reduction above | 1 MB | stack overflow, exit 75 |
| One-state reduction above | 4 MB | pass, one distinct state |

The original also overflows with this JVM's default stack size, reported as
2040 KB. These are observed configurations, not portable failure thresholds:
stack use depends on the JVM, compilation, and surrounding expressions. The
original corpus crash was recorded on Linux amd64; the table records the local
aarch64 reruns.

JVM exception logging on the original input shows the overflow unwinding
through `Tool.evalImpl`, `Tool.evalApplImpl`, `FastTool.eval`, and
`LazyValue.getValue`, while the worker checks an invariant. Together with the
one-state reduction and the no-fold control, this locates the failure in
expression evaluation rather than traversal of the state graph. The experiments
do not establish the exact stack cost per fold element.

## Apalache comparison

Re-export the original entry as typed JSON and check the same invariant:

```sh
./bin/fuzztla print --apalache-ir --corpus=corpus38 \
  corpus38/02tlc-crash/ee1e1d3798647fcd1efaa3c4afda5449d42cb3c17f7a7c8c7153d70b2435a781.cbor \
  > FuzzInput.json
java -Xmx1g -jar target/apalache.jar check \
  --init=Init --next=Next --inv=Inv --length=5 --no-deadlock FuzzInput.json
```

Apalache 0.62.2 (`f0dec98`) exits 0 in 70.897 seconds, with no stack-size
adjustment:

```text
State 5: state invariant 0 holds.
The outcome is: NoError
Checker reports no error up to computation length 5
EXITCODE: OK
```

Every reachable state is reachable within five transitions: a behavior has at
most five non-stuttering transitions, and stuttering introduces no new states.
The bounded check therefore covers this model's reachable states for the
invariant. This is not a liveness comparison or a general performance benchmark.

Passing the original `.tla` file directly to Apalache instead fails type
checking with exit 120: tuple/sequence syntax is ambiguous, first in the unused
`Op1` definition. The successful comparison uses the original typed corpus
input, without simplifying its fold or transition relation.

## Expected behavior

TLC should evaluate this modest terminating fold without exhausting a 1 MB
stack. An iterative implementation of the fold, or reduced evaluator stack
usage, would address the resource limitation; neither change was implemented
or evaluated here. If stack exhaustion occurs, the diagnostic should describe
finite recursion exceeding the stack as a possible cause.

## Impact

Small finite models can fail because a library operator consumes substantial
stack during invariant evaluation. Source size and state count do not expose
that cost, and the diagnostic directs users toward a nonexistent termination
error. Automated fuzzing records a TLC crash despite the invariant holding.

`-Xss4m` is a verified workaround for these inputs, not a general bound for
recursive folds. Simplifying this particular identity fold also avoids the
failure, but does not improve evaluation of other folds.

## Original specification

The complete, unmodified `FuzzInput.tla` from the corpus entry:

```tla
------------------------------- MODULE FuzzInput -------------------------------

EXTENDS Integers, Sequences, FiniteSets, TLC, Apalache, Variants

VARIABLE
  (*
    @type: Seq(Str);
  *)
  var0

VARIABLE
  (*
    @type: Int;
  *)
  step

(*
  @type: (() => Set(Tag0((Int -> Int))));
*)
Op1 ==
  (label2 ::
    <<
      {(Variant("Tag0", [ indexedArg3 \in {1} |-> 1 ]))}, {(Variant("Tag0", [
        indexedArg4 \in {2} |->
          2
      ]))}, {(Variant("Tag0", [ indexedArg5 \in {3} |-> 3 ]))}
    >>)[
    1
  ]

(*
  @type: (() => Bool);
*)
Init ==
  var0
      \in { (SubSeq(<<<<<<"1", "2", "3">>>>>>[1][1], 1, 1)), <<"1", "2", "3">> }
    /\ step = 0

(*
  @type: (() => Bool);
*)
Next ==
  ((step < 5
        /\ var0'
          = Append((var0 \o var0), (VariantGetUnsafe("Tag3", (VariantGetUnsafe("Tag5",
          (VariantGetOrElse("Tag6", (Variant("Tag6", (Variant("Tag5", (Variant("Tag3",
          "")))))), (Variant("Tag5", (Variant("Tag3", "")))))))))))
            \o <<"1", "2", "3">>
        /\ step' = step + 1))
    \/ UNCHANGED (<<var0, step>>)

(*
  @type: (() => Bool);
*)
Inv ==
  (Head((<<
    ((LET (*
      @type: (({ field1: Int }, Str) => { field1: Int });
    *)
    Lambda8(parameter6, parameter7) == [field2 |-> parameter6]["field2"]
    IN
    ApaFoldSeqLeft(Lambda8, [field1 |-> step], var0))), [field1 |-> 1], [field1 |->
        step], [field1 |-> 1], [field1 |-> step], [field1 |-> 1]
  >>
    \o <<[field1 |-> 1], [field1 |-> 2], [field1 |-> 3]>>)))[
    "field1"
  ]
    >= step

(*
  @type: (() => Bool);
*)
Fairness == TRUE

(*
  @type: (() => Bool);
*)
Spec == Init /\ []([Next]_(<<var0, step>>)) /\ Fairness

(*
  @type: (() => Bool);
*)
Prop == TRUE

(*
  @type: (() => Bool);
*)
Liveness == Fairness => Prop

================================================================================
```

`FuzzInput.cfg`:

```text
SPECIFICATION Spec
INVARIANT Inv
```

The original imports the repository's [Apalache.tla](../../src/main/resources/tla2sany/StandardModules/Apalache.tla)
and [Variants.tla](../../src/main/resources/tla2sany/StandardModules/Variants.tla)
in addition to TLC's standard modules. Place those two files beside the
specification when running it with a standalone TLC distribution.
