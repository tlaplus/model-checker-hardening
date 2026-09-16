---
state: open
labels: [apalache]
---

# A computed empty function-set domain loses every initial state

## Summary

`[S -> R]` with `S = {}` is the singleton `{<<>>}`, the empty function, for any
`R`. Apalache 0.62.2 encodes `fn \in [S -> {}]` as unsatisfiable whenever `S` is
an empty set produced by an expression rather than written as the literal `{}`.
The initial predicate then has no model, the bounded checker explores nothing,
and the run ends with exit status `OK`.

The same misencoding is observable from an invariant, where the checker does
explore a state: `[S -> {}] = {}` and `[S -> {}] \subseteq {}` are answered
`TRUE`.

In the initial-predicate direction the invariant is never evaluated. The only
signal is the warning
`All executions are shorter than the provided bound.` and the outcome
`ExecutionsTooShort`, which a specification can also reach legitimately, so
nothing in the exit status distinguishes this from a checked property.

Both conditions are required. A literal `{}` domain is encoded correctly, and a
nonempty range is encoded correctly with either domain:

| Function set | Apalache 0.62.2 | TLC |
| --- | --- | --- |
| `[{} -> {}]` | initial state found | initial state found |
| `[{x \in {1} : FALSE} -> {}]` | no initial state | initial state found |
| `[ApaFoldSet(Lambda, {}, {}) -> {}]` | no initial state | initial state found |
| `[{} -> {1}]` | initial state found | initial state found |
| `[ApaFoldSet(Lambda, {}, {}) -> {1}]` | initial state found | initial state found |

This is the complement of [`apalache-bmc-002`](apalache-bmc-002.md), which
records a spurious counterexample for the *literal* empty domain and notes that
the computed-empty-domain variant did not fail. It fails, in the opposite
direction and without any counterexample to inspect.

The `corpus12` run contains one aggregator deviation with this cause, where TLC
reported a violation and Apalache passed. Its initial predicate is
`var0 \in [ApaFoldSet(Lambda77, {}, {}) -> {}] /\ ...`. See the
[`b0f60628...` input](../../corpus12/03aggregator-fail/b0f60628a128c7e33ac1b8e87ad7240e5e770a2ef9b1eeba2286831bf02a27be.cbor).

Observed with Apalache 0.62.2, the version this repository pins. It does not
reproduce on the 0.62.3-SNAPSHOT build `129af5d`, which reports the violation
for every row above; no released version carries that fix yet.

## Reproduction

```tla
---- MODULE EmptyRangeFunSet ----
EXTENDS Integers

VARIABLE
\* @type: (Int -> Int);
fn

VARIABLE
\* @type: Int;
step

Init == fn \in [{x \in {1} : FALSE} -> {}] /\ step = 0
Next == UNCHANGED fn /\ step' = step + 1
Inv == step < 0
Bound == step <= 4
====
```

`Inv` is false in the initial state, so a checker that finds that state must
report a violation. TLC, with `INIT Init`, `NEXT Next`, `INVARIANT Inv`,
`CONSTRAINT Bound`, does:

```text
Computing initial states...
Error: Invariant Inv is violated by the initial state:
/\ fn = <<>>
/\ step = 0
```

`fn = <<>>` is the empty function, the single element of the function set. Run
Apalache on the same module:

```sh
java -Xmx1g -jar apalache.jar check \
  --init=Init --next=Next --inv=Inv --length=5 --no-deadlock EmptyRangeFunSet.tla
```

```text
PASS #13: BoundedChecker
All executions are shorter than the provided bound.
The outcome is: ExecutionsTooShort
Checker reports no error up to computation length 5
EXITCODE: OK
```

Replacing the domain with the literal `{}`, or the range with `{1}`, makes
Apalache report the violation. `{x \in {1} : FALSE}` may be replaced by any
other expression that evaluates to the empty set, for example
`ApaFoldSet(LAMBDA acc, x: {x}, {}, {})`, with the same result.

## Reached through the invariant, and through a `LET`-wrapped domain

The `corpus14` run adds two aggregator deviations with this cause, both TLC
counterexample and Apalache pass, and both extend the shape recorded above.

The first reaches the defect from the invariant rather than the initial
predicate, so the checker does explore a state and does evaluate the property:
it answers the emptiness question itself incorrectly. Its invariant reduces to
`[{f \in S : FALSE} -> {}] \subseteq {}`. See the
[`719224ff...5ff3fa.cbor` input](../../corpus14/03aggregator-fail/719224ff4bf8129f3d67be032d26deb2aa71f00b8eafd27434973ee84a5ff3fa.cbor).

```tla
---- MODULE EmptyRangeFunSetInvariant ----
EXTENDS Integers

VARIABLE
\* @type: Int;
step

\* @type: (() => Set(Str));
EmptyRange == {}

\* @type: (() => Set((Int -> Str)));
NoFunctions == {}

Init == step = 0
Next == step' = step + 1
\* @type: (() => Bool);
Inv == [{x \in {step} : FALSE} -> EmptyRange] = NoFunctions
Bound == step <= 4
====
```

The function set is `{<<>>}`, so `Inv` is false and TLC reports
`Invariant Inv is violated by the initial state`. Apalache 0.62.2 reports
`state invariant 0 holds` and `NoError`. Written with `\subseteq` in place of
`=` the result is the same.

The second is the initial-predicate shape of the summary, with one more way of
spelling a non-literal empty domain: a `LET` whose body is the literal `{}`.
Its initial predicate is `var0 \in [(LET LocalOp89 == ... IN {}) -> {}] /\ ...`,
and Apalache finds no initial state. Replacing that `LET`-wrapped `{}` with a
bare `{}` in the IR, changing nothing else, makes Apalache find the state and
report the violation, which isolates the wrapper as the trigger. See the
[`c988393d...ae1cd1.cbor` input](../../corpus14/03aggregator-fail/c988393d3548dc6aeb633a11b18e2bbc558269f2cfcbb9d47945f786faae1cd1.cbor).

Extending the table above:

| Function set | Apalache 0.62.2 | TLC |
| --- | --- | --- |
| `[(LET Unused == {} IN {}) -> {}]` | no initial state | initial state found |
| `[{x \in {1} : FALSE} -> {}] = {}` in an invariant | answered `TRUE` | answered `FALSE` |

Neither reproduces on the 0.62.3-SNAPSHOT build `fefda087`, which reports the
violation for both corpus inputs and for the module above.

## Recurring in `corpus18`

The `corpus18` run contains nine aggregator deviations with this cause, all
with a TLC counterexample and an Apalache pass. Each has an initial predicate
`var \in [D -> {}]` where `D` evaluates to the empty set. TLC reports the
invariant violated by an initial state that assigns `<<>>` to that variable.
Apalache 0.62.2 reports `ExecutionsTooShort`. The empty domain is spelled in
four more ways, grouped by the operator that yields `{}`:

| Domain `D` | Inputs |
| --- | --- |
| `ApaFoldSet` or `ApaFoldSeqLeft` with an empty base over an empty collection | [`32e3a422`](../../corpus18/03aggregator-fail/32e3a4225c3f61c9291666dc56173d6068db6792864e2d0917068299ee49e7c7.cbor), [`74bf9bcf`](../../corpus18/03aggregator-fail/74bf9bcf247e43365b66e0756572ce617a790dfc19410188d4de8dcb3e68acb9.cbor), [`9ee9461a`](../../corpus18/03aggregator-fail/9ee9461ac3484a55292f7753c4b9192b52be0007e921254f3b25d61a8af4989a.cbor), [`a73fccba`](../../corpus18/03aggregator-fail/a73fccba27238874f1114cc0ba2dbe1787afeb89602c1e3d9d4890dd5881885f.cbor), [`f7c9117e`](../../corpus18/03aggregator-fail/f7c9117e3e97beb1aaa1d8373e2e07a7d52a0e47368782e118ecb7c52604827.cbor) |
| a record field of a fold result | [`745ef01c`](../../corpus18/03aggregator-fail/745ef01c1e86576ac8eedbd4d60d0468d91050ce8447297d41edb6e92d26e9b3.cbor) |
| `VariantGetOrElse` yielding `{}`, directly or as a tuple component of its default | [`72e4713e`](../../corpus18/03aggregator-fail/72e4713e580f3fe712aa9113d11bf71d6dfe616687836f26d02c7773bc6b2040.cbor), [`63cabb3f`](../../corpus18/03aggregator-fail/63cabb3f39e70925e7916b0ad2747c31adb80d811485d9cbd67bb17076a33ffa.cbor) |
| a set map whose last binder ranges over `{}` | [`815c9625`](../../corpus18/03aggregator-fail/815c96254b0d891d1b54ebe1df3ea45401068fc4db635af82c8122f3856b7bc6.cbor) |

In `f7c9117e` the function set is additionally wrapped in a label. None of the
nine reproduces on the 0.62.3-SNAPSHOT build `129af5d`, which reports the
violation for each.

## Recurring in `corpus19`, and a spurious counterexample

The `corpus19` run contains eleven aggregator deviations with this cause. All
were reproduced with Apalache 0.62.2.

Nine have an initial predicate `var0 \in [D -> {}]` with a computed empty `D`,
a TLC counterexample, and an Apalache `ExecutionsTooShort` pass. In
`85633f69` the function set is the base of `ApaFoldSet(Lambda, [D -> {}] \union {}, {})`
and is reached only through the fold. Replacing `D`, and nothing else, with a
literal `{}` in the Apalache IR makes Apalache report the violation in each of
the nine, which isolates the computed domain as the trigger. The entries are
`30cfbdd9`, `461847e0`, `46a4e1d1`, `72f6def7`, `776241b2`, `85633f69`,
`a564e167`, `a74cf715` and `d1a15ff9`.

Two reach the defect through the invariant:

- `79b5a35c` has `Inv == [ApaFoldSeqLeft(Lambda, {}, <<>>) -> {}] \subseteq {}`. TLC
  reports a counterexample and Apalache reports `state invariant 0 holds`.
- `6c54a0d9` has `Inv == ([VariantFilter("Tag1", {}) -> {}] = {} <=> FALSE)`. Apalache
  answers the equality `TRUE`, so it reports a counterexample in state 0 for an
  invariant TLC proves. This is the first instance in the opposite verdict
  direction: the defect yields a spurious counterexample, not only a silent
  pass.

Both invariant shapes reproduce in isolation:

```tla
---- MODULE ComputedEmptyDomains ----
EXTENDS Integers, Sequences, Apalache, Variants

VARIABLE
\* @type: Int;
step

\* @type: (Set(Int), Str) => Set(Int);
Keep(a, b) == a
\* @type: (() => Seq(Str));
NoSeq == <<>>
\* @type: (() => Set(Tag1(Int) | Tag2(Str)));
NoVariants == {}
\* @type: (() => Set(Str));
EmptyRange == {}
\* @type: (() => Set((Int -> Str)));
NoFunctions == {}

Init == step = 0
Next == step' = step + 1
FoldInv == [ApaFoldSeqLeft(Keep, {}, NoSeq) -> EmptyRange] \subseteq NoFunctions
\* @type: (() => Bool);
FilterInv == [VariantFilter("Tag1", NoVariants) -> {}] = {}
====
```

Checked with `--length=1`, Apalache 0.62.2 reports `state invariant 0 holds`
for `--inv=FoldInv` and for `--inv=FilterInv`; both invariants are false.

## Recurring in `corpus22`

The `corpus22` run contains four aggregator deviations with this cause:
`3de9e3af`, `5222e4f8`, `60f819af` and `eeafeb5f`. Each has an initial predicate
`var0 \in [D -> {}]` with a computed empty `D`, TLC reports that the initial
state violates the invariant, and Apalache 0.62.2, rerun on the IR, reports
`All executions are shorter than the provided bound` and `ExecutionsTooShort`.
The domains were not replaced with a literal `{}` to isolate them.

## Recurring in `corpus25`

The `module` corpus25 run, generated like corpus24 with twice its entry budget,
has 115 aggregator deviations where TLC reports "Invariant Inv is violated by
the initial state" and Apalache passes. Every one was rerun: TLC on the printed
module, Apalache 0.62.2 on the entry's IR with the workflow's arguments.

Eight have this cause. None of them mentions `ENABLED` in its invariant, each
has an initial predicate `var0 \in [D -> {}]` with a computed empty `D`, and
each rerun ends with `All executions are shorter than the provided bound` and
`ExecutionsTooShort`, the initial-predicate direction of the summary. They are
`0205fa5f`, `153babe7`, `25624bc9`, `2ae12b1d`, `6344e21c`, `99fa0f6d`,
`9ca09feb` and `e678ecbb`. The domains were not replaced with a literal `{}` to
isolate them.

The other 107 have `ENABLED` in the invariant and are
[apalache-bmc-019](apalache-bmc-019.md). A `[D -> {}]` function set also occurs
somewhere in 80 of those 107, but Apalache explores states and answers the
invariant there, so the `ENABLED` path is what the rerun exhibits.

## Expected behavior

`[S -> R]` denotes the set of total functions from `S` to `R`. When `S` is
empty that set is `{<<>>}` for every `R`, including an empty `R`, so
`fn \in [S -> {}]` must be satisfiable and must fix `fn` to the empty function.
How the empty domain was obtained must not change the encoding.

## Impact

This is a soundness defect in the silent direction. Apalache reports `OK` on a
specification whose invariant it never evaluated, and an automated consumer
records a `pass` verdict that carries no information. Unlike a spurious
counterexample, nothing is printed for a user to inspect: the run looks like a
successful bounded check, and the `ExecutionsTooShort` warning is indistinguishable
from the legitimate case of a specification whose behaviors end before the bound.

The invariant-level direction is as unsound and equally quiet: the run explores
a state, evaluates the property, and reports `NoError` for a property that is
false, with no warning at all.
