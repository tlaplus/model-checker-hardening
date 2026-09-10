# Apalache and TLC conformance deviations

This directory records checker differences confirmed by an analyzed PBT
session. Among aggregator deviations, 87.82% are TLC-fail/Apalache-pass and
12.18% are TLC-pass/Apalache-fail. Additional groups with stable semantic or
capability diagnostics together account for 1.42% of Apalache crash outcomes.

The examples are reduced TLA+ modules, not CBOR corpus inputs. A failure does
not necessarily identify a checker defect: several rows exercise undefined
expressions, infinite sets, intentional resource guards, or documented semantic
differences. Confirmed defects link to `findings/`.

The workflow gives TLC `PrettyWriter` source and gives Apalache normalized typed
IR JSON. For evaluation-order rows, the MWE isolates the TLA+ operation reached
by the failing checker; feeding that isolated module through the other input
path need not reproduce the observed pass.

Unless an origin names a corpus, `Share` uses all aggregator deviations in the
original analyzed session as the denominator for aggregator rows and all
Apalache crash outcomes for crash-derived rows. Rows marked
`Aggregator (corpus4)` use corpus4's 4,282 aggregator deviations. The
`Aggregator (corpus6)` row uses corpus6's 8,644 aggregator deviations. Rows
marked `Aggregator (corpus3)` use corpus3's 435,265 aggregator deviations. The
`Apalache crash (corpus6)` row uses its 61 Apalache crash-classified results.
Rows marked `Aggregator (corpus9)` use corpus9's 250,189 aggregator deviations.
Rows marked `Aggregator (corpus10)` use corpus10's 257,852 aggregator
deviations. Percentages are rounded to two decimal places, so table rows may not sum exactly
to 100%.

| Origin | Share | TLC | Apalache | Short title | Representative example | Assessment |
|---|---:|---|---|---|---|---|
| Aggregator | 28.17% | 🔴 Fail | 🟢 Pass | Function application outside domain | [MWE](function-application-outside-domain.md#representative-mwe) | Undefined expression |
| Aggregator | 24.48% | 🔴 Fail | 🟢 Pass | `CHOOSE` without a witness | [MWE](choose-without-witness.md#representative-mwe) | Known semantic difference |
| Aggregator | 17.40% | 🔴 Fail | 🟢 Pass | `Head` of an empty sequence | [MWE](head-of-empty-sequence.md#representative-mwe) | Undefined expression |
| Aggregator | 11.36% | 🔴 Fail | 🟢 Pass | Unmatched `CASE` | [MWE](case-without-matching-arm.md#representative-mwe) | Undefined expression |
| Aggregator | 2.15% | 🔴 Fail | 🟢 Pass | Invalid `SubSeq` bounds | [MWE](subseq-outside-domain.md#representative-mwe) | Undefined expression |
| Aggregator | 2.06% | 🔴 Fail | 🟢 Pass | `Tail` of an empty sequence | [MWE](tail-of-empty-sequence.md#representative-mwe) | Undefined expression |
| Aggregator | 0.86% | 🔴 Fail | 🟢 Pass | TLC reaches `0^0` | [MWE](zero-power-zero-tlc-fails.md#representative-mwe) | Evaluation order |
| Aggregator | 0.41% | 🔴 Fail | 🟢 Pass | Integer outside TLC range | [MWE](integer-outside-tlc-range.md#representative-mwe) | TLC capability limit |
| Aggregator | 0.29% | 🔴 Fail | 🟢 Pass | Nonpositive modulo divisor | [MWE](modulo-nonpositive-divisor.md#representative-mwe) | Operator-domain difference |
| Aggregator | 0.18% | 🔴 Fail | 🟢 Pass | TLC reaches division by zero | [MWE](division-by-zero-tlc-fails.md#representative-mwe) | Evaluation order |
| Aggregator | 0.15% | 🔴 Fail | 🟢 Pass | `CHOOSE` over `Int` or `Nat` | [MWE](choose-over-infinite-set.md#representative-mwe) | TLC enumeration limit |
| Aggregator | 0.09% | 🔴 Fail | 🟢 Pass | Infinite-domain quantification | [MWE](quantification-over-infinite-set.md#representative-mwe) | TLC enumeration limit |
| Aggregator (corpus3) | 0.02% | 🔴 Fail | 🟢 Pass | Subset test over an infinite set | [MWE](subset-test-over-infinite-set.md#representative-mwe) | TLC enumeration limit |
| Aggregator (corpus3) | 0.01% | 🔴 Fail | 🟢 Pass | Cardinality of an infinite set | [MWE](cardinality-of-infinite-set.md#representative-mwe) | Undefined expression |
| Aggregator (corpus3) | 0.01% | 🔴 Fail | 🟢 Pass | Difference with an infinite set | [MWE](difference-with-infinite-set.md#representative-mwe) | TLC enumeration limit |
| Aggregator | 0.06% | 🔴 Fail | 🟢 Pass | Infinite set as state value | [MWE](infinite-set-as-state-value.md#representative-mwe) | TLC representation limit |
| Aggregator | 0.03% | 🔴 Fail | 🟢 Pass | Union containing an infinite set | [MWE](union-containing-infinite-set.md#representative-mwe) | TLC enumeration limit |
| Aggregator | 0.03% | 🔴 Fail | 🟢 Pass | Filtering `Nat` | [MWE](filter-over-infinite-set.md#representative-mwe) | TLC enumeration limit |
| Aggregator (corpus4) | 0.02% | 🔴 Fail | 🟢 Pass | Function over an infinite domain | [MWE](function-over-infinite-domain.md#representative-mwe) | TLC representation limit |
| Aggregator (corpus4) | 0.02% | 🔴 Fail | 🟢 Pass | Finite set containing `Nat` | [MWE](finite-set-containing-infinite-set.md#representative-mwe) | TLC representation limit |
| Aggregator (corpus9) | <0.01% | 🔴 Fail | 🟢 Pass | TLC cannot enumerate `STRING` | [MWE](string-set-tlc-fails.md#representative-mwe) | TLC representation limit |
| Aggregator (corpus9) | <0.01% | 🔴 Fail | 🟢 Pass | Membership against a filter over `Nat` | [MWE](filter-over-infinite-set.md#membership-against-the-filter) | TLC enumeration limit |
| Aggregator (corpus3) | <0.01% | 🔴 Fail | 🟢 Pass | Intersection of infinite sets | [MWE](intersection-of-infinite-sets.md#representative-mwe) | TLC enumeration limit |
| Aggregator (corpus3) | <0.01% | 🔴 Fail | 🟢 Pass | Cartesian product with an infinite set | [MWE](cartesian-product-with-infinite-set.md#representative-mwe) | TLC enumeration limit |
| Aggregator (corpus3) | <0.01% | 🔴 Fail | 🟢 Pass | Function set with an infinite component | [MWE](function-set-over-infinite-set.md#representative-mwe) | TLC representation limit |
| Aggregator (corpus3) | <0.01% | 🔴 Fail | 🟢 Pass | TLC reaches a negative exponent | [MWE](negative-exponent-tlc-fails.md#representative-mwe) | Undefined expression |
| Aggregator (corpus2) | <0.01% | 🔴 Fail | 🟢 Pass | Label inside an `EXCEPT` replacement | [MWE](label-inside-except.md#representative-mwe) | TLC/SANY language restriction |
| Aggregator (corpus1) | 2.91% | 🔴 Fail | 🟢 Pass | Constant-level `FALSE` invariant | [MWE](constant-false-invariant.md#representative-mwe) | TLC restriction |
| Aggregator (corpus1) | 0.15% | 🔴 Fail | 🟢 Pass | `IsFiniteSet` of `Int` or `Nat` | [MWE](../findings/apalache-bmc/apalache-bmc-007.md#reproduction) | [Soundness defect](../findings/apalache-bmc/apalache-bmc-007.md) |
| Aggregator (corpus1) | 0.31% | 🔴 Fail | 🟢 Pass | Non-enumerable initial assignment | [MWE](non-enumerable-initial-assignment.md#representative-mwe) | TLC enumeration limit |
| Aggregator | 0.09% | 🔴 Fail | 🟢 Pass | `LET` operand grouping | [MWE](let-operand-grouping.md#representative-mwe) | [Printer defect](../findings/apalache-printer/apalache-printer-007.md) |
| Aggregator (corpus3) | 0.08% | 🔴 Fail | 🟢 Pass | Synthesized `LET` changes operand types | [Finding](../findings/apalache-printer/apalache-printer-008.md) | [Printer defect](../findings/apalache-printer/apalache-printer-008.md) |
| Aggregator | 4.04% | 🟢 Pass | 🔴 Fail | Apalache reaches modulo by zero | [MWE](modulo-by-zero-apalache-fails.md#representative-mwe) | Evaluation order |
| Aggregator | 4.01% | 🟢 Pass | 🔴 Fail | Apalache reaches division by zero | [MWE](division-by-zero-apalache-fails.md#representative-mwe) | Evaluation order |
| Aggregator | 1.92% | 🟢 Pass | 🔴 Fail | Unsupported `Seq(S)` | [MWE](sequence-set-unsupported.md#representative-mwe) | Known Apalache limitation |
| Aggregator | 1.15% | 🟢 Pass | 🔴 Fail | Apalache reaches `0^0` | [MWE](zero-power-zero-apalache-fails.md#representative-mwe) | Evaluation order |
| Aggregator | 1.00% | 🟢 Pass | 🔴 Fail | Unsupported `STRING` | [MWE](string-set-unsupported.md#representative-mwe) | Apalache capability limit |
| Aggregator (corpus6) | 0.01% | Counterexample | 🟢 Pass | Division with a negative divisor | [MWE](division-negative-divisor.md#representative-mwe) | [Soundness defect](../findings/apalache-bmc/apalache-bmc-008.md) |
| Aggregator | 0.06% | 🟢 Pass | Counterexample | Empty-domain function set | [MWE](empty-function-set.md#representative-mwe) | [Soundness defect](../findings/apalache-bmc/apalache-bmc-002.md) |
| Aggregator (corpus9) | <0.01% | 🟢 Pass | Counterexample | `CHOOSE` with several witnesses | [MWE](choose-multiple-witnesses.md#representative-mwe) | Known semantic difference |
| Aggregator (corpus9) | <0.01% | Counterexample | 🟢 Pass | `DOMAIN` of an infinite-domain function | [Finding](../findings/apalache-bmc/apalache-bmc-015.md#reproduction) | [Soundness defect](../findings/apalache-bmc/apalache-bmc-015.md) |
| Aggregator (corpus10) | <0.01% | Counterexample | 🟢 Pass | Union with `Int` or `Nat` | [Finding](../findings/apalache-bmc/apalache-bmc-016.md#reproduction) | [Soundness defect](../findings/apalache-bmc/apalache-bmc-016.md) |
| Aggregator (corpus10) | <0.01% | 🔴 Fail | 🟢 Pass | Non-enumerable next-state assignment | [MWE](non-enumerable-initial-assignment.md#reached-through-the-next-state-action) | TLC enumeration limit |
| Apalache crash | 0.16% | Supported | Input error | Nonconstant integer range | [MWE](nonconstant-integer-range.md#representative-mwe) | Known Apalache limitation |
| Apalache crash (corpus6) | 6.56% | 🟢 Pass | Input error | Apalache reaches a negative power | [MWE](negative-power-apalache-fails.md#representative-mwe) | Evaluation order |
| Apalache crash | 1.05% | Varies | Crash | Symbolic-set filtering | [MWE](set-filter-symbolic-set.md#representative-mwe) | [Unhandled defect](../findings/apalache-bmc/apalache-bmc-001.md) |
| Apalache crash | 0.16% | Varies | Crash | Symbolic-set equality | [MWE](symbolic-set-equality.md#representative-mwe) | [Unhandled defect](../findings/apalache-bmc/apalache-bmc-003.md) |
| Apalache crash | 0.05% | Varies | Guard | Function-set expansion | [MWE](function-set-expansion-guard.md#representative-mwe) | Intentional resource guard |
| Apalache crash (corpus2) | 1.47% | Varies | Crash | Choosing a symbolic-set value | [MWE](choose-symbolic-set-element.md#representative-mwe) | [Unhandled defect](../findings/apalache-bmc/apalache-bmc-006.md) |

Resource-only timeouts and heap exhaustion are excluded because they do not
establish a semantic conformance difference. Known checker issues not observed
in the analyzed PBT session are also excluded.

[Vacuous initial predicate](vacuous-initial-predicate.md) has no table row
because both checkers pass it. It is recorded because it is the second
ingredient of the constant-level `FALSE` invariant row, and because an entry
with no initial state verifies nothing while still consuming a corpus slot and
both checkers.

## corpus1 and printer corruption

`corpus1` is the first session generated with `generator.kind = "module"`, and
its 653 aggregator deviations must be read with one caveat. The workflow gives
TLC `PrettyWriter` source and gives Apalache typed IR JSON, and
[`apalache-printer-008`](../findings/apalache-printer/apalache-printer-008.md)
makes that source mean something other than the IR whenever the writer
synthesizes a `LET` for a lambda argument and leaves it undelimited. Where that
happens the two checkers are not checking the same specification, so the
deviation is an artifact rather than a conformance difference.

The affected share is bounded but not pinned. Counting only a `LET` printed
immediately after `=` or `/\` at the start of a line gives 8.0% of the 653;
counting every `LET` that appears as an operand without delimiters gives 75.3%,
which overcounts because a following keyword such as `ELSE` terminates the body
harmlessly. One class is certain: the 18 deviations where TLC reports
`Attempted to evaluate an expression of form P /\ Q when P was` cannot arise
from the IR, which is built through a type-checking builder, so a non-Boolean
conjunct proves the source differs from the tree.

The two `corpus1` rows above were confirmed against that: neither involves a
`LET`, and both reproduce from a hand-written MWE on both checkers. The
remaining `corpus1` groups match existing rows by diagnostic, but their shares
are not reported here, and this session should not be used to revise the
percentages above until the writer is fixed and the corpus regenerated.

The corpus2 aggregator contains 43,279 deviations: 38,199 TLC-fail/Apalache-pass
pairs (88.26%) and 5,080 TLC-pass/Apalache-fail pairs (11.74%). Its high-volume
groups match the rows above. The remaining low-frequency diagnostics are
undefined runtime operands, infinite-set representation limits, or one
non-reproduced TLC parse failure; they do not establish another checker defect.

Independently of TLC, corpus2 contains 12,565 Apalache failure verdicts across
aggregated and residual results. The 12,457 specification-evaluation failures
fall into five existing classes: modulo by zero (4,459), division by zero
(4,263), unsupported `Seq(S)` (1,741), `0^0` (1,229), and unsupported `STRING`
(765). Of 108 counterexamples, 107 contain bounded `CHOOSE`, which Apalache
encodes as a nondeterministic symbolic choice. The remaining input combines an
empty-domain function set with undefined sequence, `CASE`, and variant
operations, so its counterexample does not establish a new defined-expression
defect. This independent classification adds no new finding.

Independently of Apalache, corpus2 contains 45,981 TLC failure verdicts across
aggregated and residual results. Partial or undefined operator evaluations
account for 45,460: function application outside its domain (14,588),
`CHOOSE` without a witness (12,871), `Head(<<>>)` (8,732), unmatched `CASE`
(5,227), invalid `SubSeq` bounds (1,165), `Tail(<<>>)` (1,083), nonpositive
modulo divisors (588), `0^0` (568), division by zero (561), and 77 related
unsafe variant, non-Boolean, or non-sequence evaluations. Another 286 failures
are existing infinite-set enumeration or representation limits, and 234 exceed
TLC's integer range. The sole parse-classified failure is a reproducible
semantic-analysis restriction: SANY does not implement labels inside `EXCEPT`
replacement expressions. It is documented above as an input-path capability
difference and does not establish a new TLC defect. This independent
classification adds no new TLC finding.

## corpus3 residuals

The triager originally left 1,389 of corpus3's 435,265 aggregator deviations as
`NEW`. Conservative diagnostic signatures now associate 1,029 of them with
existing or newly documented conformance classes and with
[`apalache-printer-008`](../findings/apalache-printer/apalache-printer-008.md).
The added conformance classes are infinite-set cardinality, difference,
intersection, subset testing, Cartesian products, and function sets, plus
negative exponents. None establishes a new checker defect.

The remaining 360 entries cannot be classified safely from their stored
metadata. Of these, 319 retain only TLC's truncated prefix `Attempted to apply
the operator overridden by the Java method`. Replays show that this group mixes
printer-induced runtime type errors with ordinary infinite-set capability
failures. Another 40 use the obsolete Apalache failure code 12, which represented
a counterexample before the corpus format gained the `counterexample` verdict;
they have no diagnostic detail, and the current envelope reader intentionally
rejects that encoding. The final entry retains only `Cannot decide if element:`.
These entries need original full diagnostics or replay with their historical
checker and generator revisions before they can support another classification.

## corpus8 residuals

The triager left 1,046 of corpus8's 279,870 aggregator deviations (0.37%) as
`NEW`. Every one was replayed against the bundled TLC through the same module
and configuration the workflow uses, so the full diagnostic is available rather
than the stored line. The replays establish no new finding and no new
conformance mismatch: every entry falls into a class this directory or
`findings/` already documents.

999 of the 1,046 are TLC-fail deviations. 523 are printer corruption rather than
a checker difference, and the remaining 476 are documented conformance classes
whose root cause is not in the stored line:

| Count | Replayed root cause | Class |
|---:|---|---|
| 523 | runtime type errors on a type-checked IR | [`apalache-printer-008`](../findings/apalache-printer/apalache-printer-008.md) |
| 200 | unmatched `CASE` | [case-without-matching-arm](case-without-matching-arm.md) |
| 148 | `CHOOSE` without a witness | [choose-without-witness](choose-without-witness.md) |
| 98 | function applied outside its domain | [function-application-outside-domain](function-application-outside-domain.md) |
| 11 | `Head(<<>>)` | [head-of-empty-sequence](head-of-empty-sequence.md) |
| 6 | integer outside TLC's range | [integer-outside-tlc-range](integer-outside-tlc-range.md) |
| 3 | infinite-domain quantification | [quantification-over-infinite-set](quantification-over-infinite-set.md) |
| 2 | `IsFiniteSet` of a filter over `Int` or `Nat` | [`apalache-bmc-007`](../findings/apalache-bmc/apalache-bmc-007.md#reached-through-a-set-filter) |
| 2 | `0^0` | [zero-power-zero-tlc-fails](zero-power-zero-tlc-fails.md) |
| 2 | comparing an overridden value with a finite set | [finite-set-containing-infinite-set](finite-set-containing-infinite-set.md) |
| 1 each | infinite-set difference, division by zero, negative exponent, `UNION` with an infinite member | the corresponding rows above |

The 523 printer-corruption entries are the same defect the corpus1 section
describes, now pinned rather than estimated. 504 report a TLC module override
refusing an impossible operand: `Cannot cast tlc2.value.impl.BoolValue to
tlc2.value.impl.IntValue` inside `Integers.Minus` (93), `Mod` (88), `Plus` (83),
`Expt` (80), `Divide` (77), `Times` (73), `Neg` (8) and `DotDot` (2), plus five
interval and two set operands. The other 19 are the same corruption seen through
a different symptom: an unbound state variable (11), a non-Boolean where TLC
needs a Boolean (3), an integer compared with a set (1), `DOMAIN` of a
non-function (1), a set-valued `Inv` (1), and two reported through a generic
exception. The IR is built through a type-checking builder, so none of these
operands can come from the tree Apalache checked.

156 of the 999 arrive inside `TLC threw an unexpected exception` with the real
diagnostic quoted in a `java.lang.RuntimeException`: function-domain errors
(57), `CHOOSE` without a witness (53), unmatched `CASE` (38), non-enumerable
quantifier bounds (3) and others. That wrapper is the open finding
[`tlc-001`](../findings/TLC/tlc-001.md); it changes how the failure is reported,
not what failed.

The other 47 entries are deviations in which both checkers completed and
disagreed on the invariant: 30 TLC-counterexample/Apalache-pass and 17
TLC-pass/Apalache-counterexample. They carry no failure detail at all, so no
diagnostic signature can reach them. 34 of the 47 render a `LET` as an
undelimited operand and are therefore printer artifacts rather than conformance
results. Each of the remaining 13 contains at least one documented cause: a
bounded `CHOOSE`, which Apalache encodes as a nondeterministic symbolic choice;
`IsFiniteSet` over `Int` or `Nat`, which is
[`apalache-bmc-007`](../findings/apalache-bmc/apalache-bmc-007.md); or an
empty-domain function set, which is
[`apalache-bmc-002`](../findings/apalache-bmc/apalache-bmc-002.md) and makes
Apalache's initial predicate vacuous.

Signatures added from these replays reclassify 19 corpus8 entries and 25
corpus7 entries. The rest stayed `NEW` for a structural reason rather than an
unknown cause: `TlcFailureDetail` stored TLC's *first* `Error:` line, and for
980 of them that line is a wrapper -- `Attempted to apply the operator
overridden by the Java method` (508), `Evaluating invariant Inv failed.` (316),
`TLC threw an unexpected exception.` (156) -- whose root cause is on a later
line. `TlcFailureDetail` now strips those wrappers and stores the first line of
the failure they report, within the same 80-character limit; the mean stored
detail grows from 43 to 57 characters and the corpus does not grow measurably,
because each entry is one file and already occupies a filesystem block.

Replaying corpus8's 1,046 residuals through the new extraction and the current
catalog classifies all 999 TLC-fail entries. The 47 both-completed deviations
remain out of reach at any detail length: a completed checker stores no detail. This applies to corpora recorded from now on. corpus7
and corpus8 keep the wrapper encoding, and their labels above come from the
replay rather than from their stored details. Storing the innermost diagnostic instead would let the
triager classify them without a replay.

## corpus9 residuals

corpus9 is the first session generated with custom operators, at commit
`0861dfb`. The triager left 40 of its 250,189 aggregator deviations (0.02%) as
`NEW`. Every one was decoded with the generator revision that produced the
corpus, and the printed source, the typed IR, and both checkers were replayed.

Because the workflow gives TLC `PrettyWriter` source and gives Apalache typed IR
JSON, the first question for each entry is whether the two tools were given the
same expression. Parsing the printed source back to IR with `apalache parse` and
comparing it to the workflow IR — modulo type annotations, source locations,
labels, and n-ary conjunction shape — answers it mechanically. 33 of the 40
differ: the printed `Inv` does not denote the tree Apalache checked. That is
[`apalache-printer-007`](../findings/apalache-printer/apalache-printer-007.md)
and [`apalache-printer-008`](../findings/apalache-printer/apalache-printer-008.md),
now pinned per entry rather than estimated as in the corpus1 section.

A structural difference does not by itself prove the deviation is an artifact:
one entry's absorbed operand (`LET ... IN X >= var0` for
`(LET ... IN X) >= var0`) is semantics-preserving. Four of the 33 have an
identified cause and are counted with their class below; the remaining 29 cannot
be read as conformance results while the writer is unfixed.

The 7 entries whose printed source does denote the IR are genuine results:

| Count | Cause | Class |
|---:|---|---|
| 3 | `~IsFiniteSet(Int)` and `~IsFiniteSet(Nat)` as the whole invariant | [`apalache-bmc-007`](../findings/apalache-bmc/apalache-bmc-007.md) |
| 1 | `~([{} -> {}] \subseteq {})` | [`apalache-bmc-002`](../findings/apalache-bmc/apalache-bmc-002.md) |
| 1 | membership in `DOMAIN [i \in Nat \|-> ...]` | [`apalache-bmc-015`](../findings/apalache-bmc/apalache-bmc-015.md) |
| 1 | a bounded `CHOOSE` with several witnesses | [choose-multiple-witnesses](choose-multiple-witnesses.md) |
| 1 | the step-bound mismatch described below | neither checker |

Three of the 33 carry a TLC failure detail and are now reachable by signature:
cardinality of an overridden `Seq(S)`
([function-over-infinite-domain](function-over-infinite-domain.md), previously
missed because the stored line is truncated before its closing period),
cardinality of `STRING` ([string-set-tlc-fails](string-set-tlc-fails.md)), and a
membership test against a filter over `Nat`
([filter-over-infinite-set](filter-over-infinite-set.md)). The other 37 entries
are deviations in which both checkers completed, so they store no detail and no
diagnostic signature can reach them; they stay `NEW`, as the corpus8 section
explains.

### The step bound was not the same bound for both checkers

Two corpus9 deviations were produced by the workflow rather than by either
checker. A generated module carried `Bound == step <= N` as TLC's state
constraint while Apalache was given `--length=N`, and the two are not
equivalent: TLC evaluates the invariant on a successor state before the
constraint discards it, so it checked states with `step` in `0..N+1` while
Apalache checked `0..N`. An invariant that first fails at `step = N + 1` was a
TLC counterexample and an Apalache pass:

```tla
---- MODULE StepBound ----
EXTENDS Integers
VARIABLE
\* @type: Int;
step
Init == step = 0
Next == step' = step + 1
Inv == 6 /= step
Bound == step <= 5
====
```

TLC reports `Invariant Inv is violated` with a seven-state trace ending at
`step = 6`; Apalache with `--length=5` reports `NoError`.

The generator now emits `step <= maximumSteps - 1`, which makes the two bounds
admit the same states; the module above becomes `Bound == step <= 4` and both
checkers pass it. Verified for step bounds 0, 1 and 5 against invariants that
first fail at each step from 0 to 7. Corpora recorded before this change keep
the wider constraint, so their deviations at exactly one step past the bound are
artifacts.

## corpus10 residuals

The triager left 46 of corpus10's 257,852 aggregator deviations (0.02%) as
`NEW`. corpus10 is the first session generated after the step-bound fix
described above, so no residual is the one-step artifact corpus9 had.

Each entry was checked the way the corpus9 section describes: the printed source
was parsed back to IR with `apalache parse` and compared with the workflow IR,
modulo type annotations, source locations, labels, n-ary conjunction shape,
where a `LET` binding sits relative to the expression that uses it, and a
negative integer literal reparsed as unary minus. 10 of the 46 differ, always in
the same way -- the `LET` the writer synthesizes for a lambda argument absorbed
the operator that followed it, so a conjunct or an argument the IR has is missing
from the parsed tree. Those are
[`apalache-printer-008`](../findings/apalache-printer/apalache-printer-008.md)
and not conformance results.

The other 36 print what Apalache checked. Every one contains at least one
documented cause. Where several are present, the entry is counted once, under
the union row if it has one, then the `IsFiniteSet` row, then the `CHOOSE` row:

| Count | Cause | Class |
|---:|---|---|
| 15 | `IsFiniteSet` over an infinite set expression | [`apalache-bmc-007`](../findings/apalache-bmc/apalache-bmc-007.md) |
| 8 | a function set whose domain is empty | [`apalache-bmc-002`](../findings/apalache-bmc/apalache-bmc-002.md) |
| 7 | a bounded `CHOOSE`, which Apalache encodes as a nondeterministic symbolic choice | [choose-multiple-witnesses](choose-multiple-witnesses.md) |
| 6 | an invariant that unites a finite set with `Int` or `Nat` | [`apalache-bmc-016`](../findings/apalache-bmc/apalache-bmc-016.md) |

Six of the eight empty-domain function sets are empty by construction, for
example `[{x \in S : FALSE} -> {}]`. The other two are empty only in the
reachable states -- one domain is a set map over a variable that `Init` sets to
`{}` and `Next` leaves unchanged -- and were confirmed by reading the module
rather than by folding the expression.

The last group is new. `S \union Nat` and `S \union Int` evaluate to `S`, with
no warning, so membership, equality, `Cardinality`, `IsFiniteSet` and bounded
quantification over the union all answer as if the infinite operand were empty.
It is wrong in both directions and appears in the corpus that way: four entries
are a TLC counterexample against an Apalache pass, one is the reverse, and one
is a TLC finiteness failure against an Apalache counterexample.
[`apalache-bmc-016`](../findings/apalache-bmc/apalache-bmc-016.md) records it.

Four of the 46 carry a TLC failure detail and are now reachable by signature: a
membership whose right side is not enumerable, reached from the next-state
action instead of the initial predicate
([non-enumerable-initial-assignment](non-enumerable-initial-assignment.md#reached-through-the-next-state-action));
two `IsFiniteSet` operands written as set expressions rather than filters,
`Nat \cap Int` and `{0} \cup Int \ Nat`
([`apalache-bmc-007`](../findings/apalache-bmc/apalache-bmc-007.md)); and one
`Successor state is not completely specified by action Next`, which the IR
comparison confirms is the lambda-`LET` absorbing a conjunct of `Next`, the
next-state analogue of the unassigned-variable symptom
[`apalache-printer-008`](../findings/apalache-printer/apalache-printer-008.md)
already lists. The remaining 42 are deviations in which both checkers completed,
so they store no detail and stay `NEW`, as the corpus8 section explains.
