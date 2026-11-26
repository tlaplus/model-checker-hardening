---
state: open
labels: [apalache]
---

# A sequence applied past its end is free or fixed depending on how the index was computed

## Summary

Apalache gives `s[i]` with `i` outside `1..Len(s)` one of two meanings, and which one
depends on how the cell of `i` was produced, not on its value:
- When the index is a literal, or a cell that Apalache's integer cache knows as a
  constant, the result is the fixed default value of the element type.
- Otherwise the result is unconstrained.

So `<<"a", "b">>[i] = "c"` can hold when `i = 3` was computed as `i + 1`, and cannot hold
when `i` was assigned the literal `3`. Apalache reports counterexamples that it refutes
when it replays them, and it misses behaviors that it admits in an equivalent module.
This is the mechanism of [`apalache-bmc-024`](apalache-bmc-024.md), whose `CHOOSE` is
free or fixed depending on its set's cells, reached through sequence application.

Reproduced with Apalache 0.63.0 (the release JAR) and 0.62.1, and traced in the source at
`a2910d423` (`v0.63.0-2-ga2910d423`). TLC 2026.11.19 (`bc1b506`) rejects the
application, as [function-application-outside-domain](../../conformance/function-application-outside-domain.md)
documents. FuzzTLA `218ef666` found it.

## Reproduction

Save this module as `SeqPastEnd.tla`:

```tla
---- MODULE SeqPastEnd ----
EXTENDS Integers, Sequences
VARIABLE
  \* @type: Int;
  i
\* @type: Seq(Str);
Vals == <<"a", "b">>
Init == i = 0
Next == i' = i + 1
Inv == ~(i = 3 /\ Vals[i] = "c")
====
```

```sh
java -jar apalache.jar check --length=3 --inv=Inv --no-deadlock SeqPastEnd.tla
```

Apalache reports a violation in state 3, in which `Vals[3] = "c"`:

```text
State 3: state invariant 0 violated.
The outcome is: Error
EXITCODE: ERROR (12)
```

Every row below differs from the reproduction only in `Next`, or in `Init` with
`Next == UNCHANGED i` and `--length=0`. In each, `i = 3` holds in the state that the
invariant tests.

| `i = 3` is set by | Can `Vals[i]` be `"c"`? |
| --- | --- |
| `Next == i' = i + 1` | yes |
| `Next == i' \in {i + 1}` | yes |
| `Next == i' = 3 - i \/ i' = i + 1` | yes |
| `Init == i \in {3, 4}`, `Init == i \in 3..3` | yes |
| `Next == i' = 3` | no |
| `Next == (i = 0 /\ i' = 1) \/ (i = 1 /\ i' = 2) \/ (i = 2 /\ i' = 3)` | no |
| `Init == i = 3`, `Init == i = 1 + 2`, `Init == i \in {3}` | no |

## Expected behavior

`s[i]` outside the domain of `s` has one meaning, whatever cell represents `i`.
`ProtoSeqOps.get` documents the unconstrained value as the intended one ("This is
consistent with Specifying Systems"), so every row of the table should admit `"c"`. If
a fixed value were intended instead, no row should.

## Impact

- **Missed counterexamples.** A specification that reaches an out-of-range application
  with a literal or cached index gets the default value, so Apalache misses behaviors
  that the same specification has when the index is computed.
- **Counterexamples that do not replay.** Apalache reports traces through a free
  out-of-range application. A module that fixes the trace's states with literal
  assignments turns the index into a cached constant, and Apalache then refutes the
  trace. FuzzTLA's counterexample replay
  ([ADR 0034](../../docs/decisions/0034-counterexample-replay.md)) found the defect
  this way.

The application must be out of range in a reachable state, so the defect matters only
where a specification relies on a partial application, as generated invariants such as
`<<...>>[step]` do.

## Root cause

`FunAppRule.applySequence` (`tla-bmcmt/.../rules/FunAppRule.scala`, lines 65–90) handles
an application of a sequence:

- A literal index outside `1..capacity` returns `defaultValue` (line 74): *"This is the
  rare case when the spec author has made a typo, e.g., f[0]."*
- Any other index goes to `ProtoSeqOps.get` (`tla-bmcmt/.../rules/support/ProtoSeqOps.scala`).

`ProtoSeqOps.get` rewrites the index to a cell and looks it up in
`rewriter.intValueCache`:

- If the cell is not cached (lines 162–186), an oracle picks an element, constrained
  only by `(index - 1 = oracle) <=> (0 < index <= capacity)`. Out of range, the oracle
  and therefore the result are unconstrained.
- If the cell is cached (lines 188–200), an out-of-range value returns `defaultValue`.
  The comment names the intended use: an index bound by `\A i \in DOMAIN seq`, which is
  always in range.

An assignment `i' = 3` makes `i` the cell of the literal `3`, which is in the integer
cache. `i' = i + 1` makes a fresh arithmetic cell, which is not.

A possible fix is to return an unconstrained cell, not `defaultValue`, in both
out-of-range branches of a constant index, so that all three paths agree. It has not
been tried.

## How it was found

corpus90 replays every counterexample that TLC and Apalache record as an input of its
own ([ADR 0034](../../docs/decisions/0034-counterexample-replay.md)). Apalache passed
179 replays of its own counterexamples. 121 of them apply a sequence to a non-literal
index in the invariant. When the replay module's sequence indices `i` were rewritten as
`(i + idx) - idx`, which is not a cached constant, Apalache confirmed its trace in 56 of
the 179.

An example is replay `40b0c82e65f7` of parent `f0f0c1833cd6`, whose invariant is

```tla
<<"value1_OF_MODEL", "value2_OF_MODEL">>[step] /= "default_OF_MODEL"
```

Apalache's trace of the parent has 3 transitions, with `step` computed as `step + 1`.
The replay assigns `step' = 3`, and Apalache passes it. Rewriting the index as
`(step + idx) - idx` makes Apalache report the counterexample again.

## Signature

None. `signatures/all-defects.toml` lists this finding as uncovered. Every sequence
application with a non-literal index can reach the defect, and a signature matching
them would quarantine most generated modules.

## Verification

- FuzzTLA `218ef666`; TLC 2026.11.19 (`bc1b506`); Apalache 0.63.0 (the release JAR) and
  0.62.1, and the source at `a2910d423` for the line references.
- Every row of the table was run on 0.63.0 with `--no-deadlock`, `--length=3` for the
  `Next` rows and `--length=0` for the `Init` rows. The reproduction reports
  `ERROR (12)` on 0.62.1 too.
