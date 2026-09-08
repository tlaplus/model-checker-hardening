---
state: open
labels: [apalache]
---

# `PrettyWriter` does not delimit the `LET` it synthesizes for a lambda argument

## Summary

Apalache's `PrettyWriter` renders a lambda passed to a higher-order operator by
synthesizing a `LET` that binds the lambda and applies the operator in its body.
That synthesized `LET` is printed without parentheses. Because a `LET` body
extends to the right, any operator following the enclosing expression is
absorbed into the body, and the printed module no longer means what the input IR
says.

This is the same class of defect as
[`apalache-printer-007`](apalache-printer-007.md) but a different code path.
The general fix released in Apalache 0.56.1 is present: an explicit `LetInEx`
used as an operand *is* parenthesized, including when it breaks across lines.
The `LET` that the writer introduces for a lambda argument is not.

Observed with `org.apalache-mc:tla-io_2.13:0.62.3-SNAPSHOT`.

The `corpus1` run contains three crashing instances, all reported by TLC as
error 2102 with a variable left unassigned. See the
[`1a5984ba...` input](../../corpus1/02tlc-crash/1a5984ba4fdc81016ec5b401a078d48d0827a7737354c33c2048daa49ffc7e64.cbor)
and its [stacktrace](../../corpus1/02tlc-crash/1a5984ba4fdc81016ec5b401a078d48d0827a7737354c33c2048daa49ffc7e64.stacktrace).
The crashes are not the whole extent; see Impact.

## Reproduction

For the IR tree

```text
AND(EQ(var0, ApaFoldSeqLeft(LAMBDA p, q: p, FALSE, <<>>)),
    EQ(step, 0))
```

`PrettyWriter` emits:

```tla
Init ==
  var0
      = LET (*@type: ((Bool, Int) => Bool); *) Lambda3(p, q) == p IN
      ApaFoldSeqLeft(Lambda3, FALSE, <<>>)
    /\ step = 0
```

SANY parses that as

```tla
Init == var0 = (LET Lambda3(p, q) == p IN
                  (ApaFoldSeqLeft(Lambda3, FALSE, <<>>) /\ step = 0))
```

so `Init` no longer constrains `step`. Checking the complete module with
`INIT Init`, `NEXT Next`, `INVARIANT Inv` gives:

```text
TLC error code 2102 mapped to exit status 255
Error: current state is not a legal state
While working on the initial state:
/\ step = null
/\ var0 = FALSE
```

Adding the two parentheses the tree requires makes the same module check
successfully:

```tla
Init ==
  var0
      = (LET (*@type: ((Bool, Int) => Bool); *) Lambda3(p, q) == p IN
      ApaFoldSeqLeft(Lambda3, FALSE, <<>>))
    /\ step = 0
```

```text
Model checking completed. No error has been found.
7 states generated, 6 distinct states found, 0 states left on queue.
```

## Expected behavior

The writer must delimit the `LET` it synthesizes for a lambda argument exactly
as it delimits an explicit one, so that the printed module parses back to the
tree it was given. A round-trip test that compares the parsed tree, rather than
SANY acceptance, would cover both paths.

## Impact

This is semantic source corruption, and it is silent: the printed module is
syntactically valid and SANY accepts it, so the corruption surfaces only as a
downstream failure that describes the misprinted tree.

It is not confined to the three crashes, and the affected share of `corpus1` is
bounded rather than pinned. Counting only a `LET` printed without parentheses
immediately after `=` or `/\` at the start of a line gives 8.0% of the 653
`03aggregator-fail` entries; counting every `LET` that appears as an operand
without delimiters gives 75.3% of `03aggregator-fail` and 58.6% of
`03aggregator-pass`. The first number undercounts because the writer also leaves
the synthesized `LET` undelimited after `\in`, `CASE`, `THEN` and other
operands; the second overcounts because a following keyword such as `ELSE`
terminates the body harmlessly.

One class within that range is certain rather than estimated. Eighteen
`corpus1/03aggregator-fail` entries have TLC report `Attempted to evaluate an
expression of form P /\ Q when P was` a non-Boolean value. Corpus3 adds 338
fail/pass deviations in which TLC receives an impossible runtime operand for a
Boolean connective, condition, membership test, sequence composition, or
negation. These account for 0.08% of corpus3's 435,265 deviations. For example,
[`ddaa3b45...bd70`](../../corpus3/03aggregator-fail/ddaa3b45d08b835b780648a06dea50b8913db83d486d5d1e4418ceceec54bd70.cbor)
has a record as the antecedent of an implication in the printed source, while
the corresponding typed IR gives `IMPLIES` a Boolean equality operand.

The IR is built through a type-checking builder, so these runtime type errors
prove that the source TLC parsed is not the tree the writer was given. Another
319 corpus3 entries have only the truncated detail `Attempted to apply the
operator overridden by the Java method`. Replays sampled from that group show
both impossible arithmetic operands, which are further instances of source
corruption, and ordinary finite-set capability failures. The stored detail does
not distinguish them, so the triager conservatively leaves the group unlabelled.

Corpus8 pins that split. All 1,046 of its unclassified aggregator deviations
were replayed against the bundled TLC, and 523 are this defect. 504 report a
module override refusing an operand the IR cannot produce:

| Override | Count | Reported error |
|---|---:|---|
| `Integers.Minus` | 93 | `Cannot cast tlc2.value.impl.BoolValue to tlc2.value.impl.IntValue` |
| `Integers.Mod` | 88 | same |
| `Integers.Plus` | 83 | same |
| `Integers.Expt` | 80 | same |
| `Integers.Divide` | 77 | same |
| `Integers.Times` | 73 | same |
| `Integers.Neg` | 8 | same |
| `Integers.DotDot` | 2 | `IntervalValue` or `SetEnumValue` operand |

The remaining 19 show the same corruption through other symptoms: 11 leave a
state variable unbound (`In evaluation, the identifier step is either undefined
or not an operator`), 3 put a non-Boolean where TLC requires a Boolean, and one
each compares an integer with a set, applies `DOMAIN` to a non-function, and
gives `Inv` a set value (`The invariant of Inv is equal to {}`). None of these
operands can arise from a tree the type-checking builder accepted, so each is a
mismatch between the printed source and the IR. The triager now classifies these
symptoms directly; the truncated `overridden by the Java method` prefix stays
unlabelled because it also covers ordinary capability failures.

Corpus8 also shows the reach beyond failure verdicts: of its 47 unclassified
deviations in which both checkers completed and disagreed on the invariant, 34
render a `LET` as an undelimited operand, so those disagreements describe
different specifications rather than different checkers.

The enrichment in the disagreement bucket follows from the two checkers reading
different artifacts: Apalache consumes the typed IR JSON and sees the intended
tree, while the parser and TLC consume this text and see the corrupted one. A
conformance disagreement produced that way is an artifact of the writer rather
than a difference between the checkers, so affected entries in
`03aggregator-fail` cannot be trusted as conformance results.
