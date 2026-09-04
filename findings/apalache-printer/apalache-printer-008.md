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

It is not confined to the three crashes. Scanning `corpus1` for a `LET`
printed without parentheses immediately after `=` or `/\` at the start of a
line finds it in 3 of 150 sampled `03aggregator-pass` entries and 10 of 150
sampled `03aggregator-fail` entries. That scan recognizes only two embeddings,
so those rates are lower bounds.

The enrichment in the disagreement bucket follows from the two checkers reading
different artifacts: Apalache consumes the typed IR JSON and sees the intended
tree, while the parser and TLC consume this text and see the corrupted one. A
conformance disagreement produced that way is an artifact of the writer rather
than a difference between the checkers, so affected entries in
`03aggregator-fail` cannot be trusted as conformance results.
