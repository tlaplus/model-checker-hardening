---
state: open
labels: [apalache]
---

# A skolemized `\E c \in a..b` over a state-dependent range prunes every execution through an empty range

## Summary

Apalache skolemizes a bounded existential whose set is a syntactic interval
`\E c \in a..b : P` by picking a symbolic `c` and asserting the bounds `c >= a`
and `c <= b` as ground facts, instead of making them part of the transition's
encoding. When the range is empty in a reachable state -- `a > b`, which a
state-dependent bound produces readily -- the assertions are unsatisfiable in
that state, so every execution through it is lost, no matter which transition
the execution takes there. The check then ends with `NoError` and no warning.

The same pruning occurs through assignment membership `x' \in a..b`, which the
rewriter encodes by the same path.

This is a soundness defect in one direction: Apalache misses violations that
TLC finds. The specification below violates its invariant only on paths through
a state where the range is empty, so Apalache never reports it.

The defect was reported upstream as [Apalache issue
#3503](https://github.com/apalache-mc/apalache/issues/3503) against 0.63.0.
It reproduces with Apalache 0.62.3 (build `3eb15b2`), the version the current
corpora pin, and with `main` at `a2910d423` (`v0.63.0-2-ga2910d423`). It is
recorded here because the fuzzer did not find it: every candidate that could
reach the defect was quarantined by the `integer-range` known-defect signature;
see [Why the fuzzer did not find it](#why-the-fuzzer-did-not-find-it).

## Reproduction

Save this module as `RangeSkolem.tla` (the upstream reporter's minimization):

```tla
---- MODULE RangeSkolem ----
EXTENDS Integers
VARIABLES
    \* @type: Int;
    x,
    \* @type: Str;
    pc
Init == x = 0 /\ pc = "a"
Write == x = 0 /\ \E c \in 1..(2 - x) : x' = c /\ UNCHANGED pc
Start == pc = "a" /\ pc' = (IF x >= 1 THEN "b" ELSE "c") /\ UNCHANGED x
Next == Write \/ Start
Inv == ~(x = 2 /\ pc = "b")
====
```

The range `1..(2 - x)` is empty once `x = 2`, and the only path to the
violation goes through `x = 2`. TLC finds the counterexample `x = 0, pc = "a"`
-> `Write` with `c = 2` -> `Start`, ending in `x = 2, pc = "b"` at depth 2:

```text
Error: Invariant Inv is violated.
```

Apalache loses the path when the `Write` transition is skolemized in the state
`x = 2` and reports:

```text
State 2: Checking 1 state invariants
State 2: state invariant 0 holds.
Step 2: picking a transition out of 2 transition(s)
The outcome is: NoError
Checker reports no error up to computation length 2
EXITCODE: OK
```

```sh
java -jar target/apalache.jar check --length=2 --inv=Inv RangeSkolem.tla
```

Replacing the range with an explicit bound, `\E c \in Int : 1 <= c /\ c <= 2 - x
/\ x' = c /\ UNCHANGED pc`, makes Apalache find the violation
(`EXITCODE: ERROR (12)`), confirming that the encoding of the range bounds, not
the exploration, loses the path.

The variant `Write == x = 0 /\ x' \in 1..(2 - x) /\ UNCHANGED pc` misses the
violation the same way.

## Root cause

`QuantRule.skolemExistsInRange`
(`tla-bmcmt/.../rules/QuantRule.scala`) calls `assertGroundExpr` on `c >= a`
and on `c <= b` when it skolemizes `\E c \in a..b`. Ground assertions hold in
every state of the checking run, so one state with `a > b` poisons all
executions through it. The bounds belong in the skolemized transition's
encoding, conjoined with the quantified body under the pick.

## Where a nonconstant range is accepted

Whether Apalache rejects a nonconstant interval with the input error `Expected
a constant integer range in [ .. ]` depends on the position the interval
occupies, because rejection happens when the rewriter must expand the interval
into an arena set. Measured with Apalache 0.62.3 (`3eb15b2`); every row uses
`R == 1..(2 - x)` over a state variable `x`:

| Position | Verdict |
| --- | --- |
| `\E c \in R : P` quantifier bound | accepted (symbolic; this defect) |
| `\A c \in R : P` quantifier bound | accepted (rewritten to `\E`) |
| `x' \in R` assignment membership | accepted (symbolic; this defect) |
| `e \in R`, `e \notin R` membership in a state predicate | accepted (symbolic) |
| `Cardinality(R)`, `IsFiniteSet(R)` | accepted (arithmetic) |
| `SUBSET R` as a membership operand | accepted (symbolic powerset cell) |
| `CHOOSE c \in R : P` bound | rejected |
| `ys = R`, `ys = IF .. THEN R ELSE ..`, `ys = LET .. IN R` | rejected |
| `R = S`, `R # S` set equality | rejected |
| `b = (e \in R)` equality over a membership | accepted |
| `R \union S`, `R \intersect S`, `R \ S`, `R \subseteq S` | rejected |
| `UNION {R, ..}` | rejected |
| `{R, ..}` set enumeration element | rejected |
| `{v \in R : P}`, `{e : v \in R}` filter or map domain | rejected |
| `[R -> T]`, `[v \in R \|-> e]` function set or constructor domain | rejected |
| `[f: R]` record-set field | rejected |
| `R \X S` cartesian product | rejected |
| `\E c \in (IF .. THEN R ELSE ..) : P` wrapped quantifier bound | rejected |

## Why the fuzzer did not find it

The defect needs a range with state-dependent bounds in a skolemized position
of a transition, and a violation reachable only through a state where the range
is empty. The generator produces such candidates: `INTERVAL` bounds are
arbitrary integer expressions, and action parameters quantify over arbitrary
set expressions, so `\E actionParam \in 1..var0` occurs in generated modules;
quarantined candidates include `CHOOSE bound385 \in var0..var0`
(`corpus77/00-known-defects/082d250b...cbor`) and an action parameter with the
bound `1 .. var0` (`corpus77/00-known-defects/24a510ab...cbor`).

Admission, not detection, blocked the find. The aggregator flags a TLC
counterexample against an Apalache pass -- that direction produced
[`apalache-bmc-017`](apalache-bmc-017.md). But the `integer-range` signature of
`signatures/all-defects.toml` matched `(INT_RANGE _ _)`, any interval anywhere,
and quarantined every candidate containing `..` before either checker ran.
Quarantined entries matching it: 202 of 2,223 in `corpus77`, 285 of 3,621 in
`corpus79`, 181 of 2,536 in `corpus82`.

The blanket pattern existed because the referenced defects are positional in
opposite directions: Apalache rejects a nonconstant range it must expand, and
TLC materializes a large range even under a quantifier
([`tlc-005`](../TLC/tlc-005.md), [`tlc-006`](../TLC/tlc-006.md)). The signature
pattern language matches a node and its descendants and cannot name a parent,
so "an interval except as a quantifier bound" is inexpressible as an exception;
the fix enumerates the expanded positions instead. `integer-range` now matches
an interval only where Apalache expands it (the rejected rows above) or where a
boundary-table literal makes it large enough to endanger TLC, and admits the
symbolic positions: quantifier bounds, membership, `Cardinality`,
`IsFiniteSet`, and the powerset operand.

The residual cost of admitting ranges is triage noise, not hidden defects: a
nonconstant range in an unlisted expanded position makes Apalache exit 255 with
its named diagnosis, and a large wide-mode interval literal makes TLC report an
enumeration error. Neither is a silent divergence.

Two limitations of the fix, both inherited from the pattern language:

- A range under a membership inside a larger equality, as in
  `ys = {v \in S : 3 \in R}`, escapes the `EQ` alternatives' membership
  carve-out and is quarantined although Apalache accepts it.
- An entry whose ranges all sit in symbolic positions but which also matches
  another signature stays quarantined for that reason. All 202 `corpus77`
  specimens still match the narrowed `integer-range`, and 29 of a 40-entry
  `corpus79` sample do, because a large generated module almost always also
  carries a range in an expanded position; the narrowing shows in fresh
  admissions, not in the stored specimens.

## Pipeline verification

A candidate that the blanket signature quarantined and the narrowed one admits
reaches both checkers and produces agreeing verdicts. A paired run of two
300-entry `pbt` corpora on the same seeds, one per database, admitted
`bf95997a...` (`Init == var0 \in [field3 |-> 1]["field3"] .. 1 /\ ...`) only
under the narrowed database: TLC and Apalache both report the counterexample at
depth 0. A 400-entry `gbt` corpus with the interval form weighted 64
quarantined 381 of 2,617 candidates on the narrowed `integer-range`, confirming
that the remaining patterns keep their recall on generated code.

Wrapped in the workflow's `FuzzInput` skeleton (`step` bound 2) and checked
with the pipeline's own invocations, the reproduction module above gives the
aggregator divergence the quarantine suppressed:

- TLC (`SPECIFICATION Spec`, `INVARIANT Inv`, `-deadlock`): `Error: Invariant
  Inv is violated`, a 6-distinct-state graph of depth 3;
- Apalache 0.62.3 (`check --init=Init --next=Next --inv=Inv --length=2
  --no-deadlock`): `NoError`, `EXITCODE: OK`.

## Verification

- fuzztla `ac2372d4`; Apalache 0.62.3 (build `3eb15b2`, the corpus-pinned
  version) and `v0.63.0-2-ga2910d423`; TLC 2026.11.06 (tla2tools `d8fb231`,
  the fuzztla-staged snapshot) and the tla2tools bundled in the corpus-pinned
  Apalache (2026.11.12).
- TLC reports `Invariant Inv is violated` for `RangeSkolem.tla` with
  `SPECIFICATION Spec`-equivalent settings (`INIT Init`, `NEXT Next`,
  `INVARIANT Inv`); Apalache 0.62.3 and `a2910d423` report `NoError` at
  `--length=2`.
- The position table was measured with the corpus-pinned Apalache on one module
  per row.
- `signatures/all-defects.toml` narrows `integer-range` as described;
  `AllDefectsTest` carries the new shape (a set equality with an interval
  matches; a quantifier bound over an interval does not).
