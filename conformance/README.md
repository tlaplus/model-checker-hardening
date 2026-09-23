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
deviations. Rows marked `Aggregator (corpus12)` use corpus12's 50,545
aggregator deviations. Rows marked `Aggregator (corpus14)` use corpus14's 62,487
aggregator deviations. Rows marked `Aggregator (corpus29)` use corpus29's 41,688
aggregator deviations. Rows marked `Aggregator (corpus43)` use corpus43's
41,895 aggregator deviations. The `Apalache crash (corpus47)` row uses corpus47's 126
Apalache crash outcomes. Percentages are rounded to two decimal places, so table rows may not sum exactly
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
| Aggregator (corpus43) | 0.50% | 🔴 Fail | 🟢 Pass | Bag with a non-positive multiplicity | [MWE](bag-nonpositive-multiplicity.md#representative-mwe) | Undefined expression |
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
| Smoke corpus ([ADR 0007](../docs/decisions/0007-levels-and-temporal-properties.md)) | 4.17% | 🔴 Fail | 🟢 Pass | Constant `FALSE` or tautological property | [MWE](constant-property-tlc-rejects.md#representative-mwe) | TLC restriction |
| Smoke corpus ([ADR 0007](../docs/decisions/0007-levels-and-temporal-properties.md)) | quarantined | 💥 Crash | 🟢 Pass | Temporal formulas TLC cannot check | [MWE](tlc-temporal-formula-limits.md#representative-mwe) | TLC capability limit |
| Design ([ADR 0007](../docs/decisions/0007-levels-and-temporal-properties.md)) | not measured | 🟢 Pass | 🔴 Fail | Unsupported `ENABLED` | [MWE](enabled-apalache-unsupported.md#representative-mwe) | Known Apalache limitation |
| Design ([ADR 0007](../docs/decisions/0007-levels-and-temporal-properties.md)) | not measured | 🟢 Pass | 🔴 Fail | Unsupported fairness | [MWE](fairness-apalache-unsupported.md#representative-mwe) | Known Apalache limitation |
| Aggregator (corpus6) | 0.01% | Counterexample | 🟢 Pass | Division with a negative divisor | [MWE](division-negative-divisor.md#representative-mwe) | [Soundness defect](../findings/apalache-bmc/apalache-bmc-008.md) |
| Aggregator | 0.06% | 🟢 Pass | Counterexample | Empty-domain function set | [MWE](empty-function-set.md#representative-mwe) | [Soundness defect](../findings/apalache-bmc/apalache-bmc-002.md) |
| Aggregator (corpus9) | <0.01% | 🟢 Pass | Counterexample | `CHOOSE` with several witnesses | [MWE](choose-multiple-witnesses.md#representative-mwe) | Known semantic difference |
| Aggregator (corpus29) | 0.05% | 🟢 Pass | Counterexample | Order-sensitive fold over a set | [MWE](order-sensitive-set-fold.md#representative-mwe) | Known semantic difference |
| Aggregator (corpus9) | <0.01% | Counterexample | 🟢 Pass | `DOMAIN` of an infinite-domain function | [Finding](../findings/apalache-bmc/apalache-bmc-015.md#reproduction) | [Soundness defect](../findings/apalache-bmc/apalache-bmc-015.md) |
| Aggregator (corpus10) | <0.01% | Counterexample | 🟢 Pass | Union with `Int` or `Nat` | [Finding](../findings/apalache-bmc/apalache-bmc-016.md#reproduction) | [Soundness defect](../findings/apalache-bmc/apalache-bmc-016.md) |
| Aggregator (corpus29) | <0.01% | Counterexample | 🟢 Pass | Order-sensitive fold over a set | [MWE](order-sensitive-set-fold.md#representative-mwe) | Known semantic difference |
| Aggregator (corpus12) | <0.01% | 🟢 Pass | Counterexample | `IsFiniteSet` of `Int` or `Nat`, negated | [Finding](../findings/apalache-bmc/apalache-bmc-007.md#the-dual-direction-observed-in-a-corpus) | [Soundness defect](../findings/apalache-bmc/apalache-bmc-007.md) |
| Aggregator (corpus12) | <0.01% | Counterexample | 🟢 Pass | `CASE` with several true guards | [MWE](case-multiple-true-guards.md#representative-mwe) | Known semantic difference |
| Aggregator (corpus12) | <0.01% | Counterexample | 🟢 Pass | Computed empty function-set domain | [Finding](../findings/apalache-bmc/apalache-bmc-017.md#reproduction) | [Soundness defect](../findings/apalache-bmc/apalache-bmc-017.md) |
| Aggregator (corpus12) | <0.01% | Counterexample | 🟢 Pass | Applying an infinite-domain function | [Finding](../findings/apalache-bmc/apalache-bmc-015.md#reached-through-application-not-only-domain) | [Soundness defect](../findings/apalache-bmc/apalache-bmc-015.md) |
| Aggregator (corpus10) | <0.01% | 🔴 Fail | 🟢 Pass | Non-enumerable next-state assignment | [MWE](non-enumerable-initial-assignment.md#reached-through-the-next-state-action) | TLC enumeration limit |
| Aggregator (corpus14) | <0.01% | 🔴 Fail | Varies | Membership test with `Int` or `Nat` as the element | [MWE](infinite-set-as-membership-element.md#representative-mwe) | TLC representation limit |
| Aggregator (corpus14) | <0.01% | Counterexample | 🟢 Pass | Empty-range function set in an invariant | [Finding](../findings/apalache-bmc/apalache-bmc-017.md#reached-through-the-invariant-and-through-a-let-wrapped-domain) | [Soundness defect](../findings/apalache-bmc/apalache-bmc-017.md) |
| Apalache crash | 0.16% | Supported | Input error | Nonconstant integer range | [MWE](nonconstant-integer-range.md#representative-mwe) | Known Apalache limitation |
| Apalache crash (corpus6) | 6.56% | 🟢 Pass | Input error | Apalache reaches a negative power | [MWE](negative-power-apalache-fails.md#representative-mwe) | Evaluation order |
| Apalache crash | 1.05% | Varies | Crash | Symbolic-set filtering | [MWE](set-filter-symbolic-set.md#representative-mwe) | [Unhandled defect](../findings/apalache-bmc/apalache-bmc-001.md) |
| Apalache crash | 0.16% | Varies | Crash | Symbolic-set equality | [MWE](symbolic-set-equality.md#representative-mwe) | [Unhandled defect](../findings/apalache-bmc/apalache-bmc-003.md) |
| Apalache crash | 0.05% | Varies | Guard | Function-set expansion | [MWE](function-set-expansion-guard.md#representative-mwe) | Intentional resource guard |
| Apalache crash (corpus47) | 0.79% | 🟢 Pass | Guard | Set map over a mapped set | [MWE](set-map-product-guard.md#representative-mwe) | Intentional resource guard |
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

## corpus14 residuals

The triager left 11 of corpus14's 62,487 aggregator deviations (0.02%) as `NEW`.
Each was re-checked by printing the entry's TLA+ source and typed IR with
`fuzztla print --corpus corpus14` and running TLC and Apalache 0.62.2 on them
directly. All 11 reproduce the verdict pair the envelope records, and every one
has a documented cause:

| Count | Cause | Class |
|---:|---|---|
| 4 | `IsFiniteSet` of `Int` or `Nat`, or of a set expression that evaluates to one | [`apalache-bmc-007`](../findings/apalache-bmc/apalache-bmc-007.md) |
| 2 | a bounded `CHOOSE` whose predicate has several witnesses | [choose-multiple-witnesses](choose-multiple-witnesses.md) |
| 2 | a function set with an empty range and a computed empty domain | [`apalache-bmc-017`](../findings/apalache-bmc/apalache-bmc-017.md) |
| 2 | `Nat` or `Int` as the element of a membership test | [infinite-set-as-membership-element](infinite-set-as-membership-element.md) |
| 1 | an invariant that unites a finite set with `Nat` | [`apalache-bmc-016`](../findings/apalache-bmc/apalache-bmc-016.md) |

The `apalache-bmc-017` pair is what the new table row above records. One reaches
the defect from the invariant rather than the initial predicate, which is the
first observation of that direction; the other adds a `LET` whose body is the
literal `{}` to the known list of non-literal empty domains.

The membership pair is the only group that carries a TLC failure detail, and it
is now reachable by signature. Its message,
`Attempted to check if the non-enumerable value`, names the element rather than
the set, so none of the
[finite set containing `Nat`](finite-set-containing-infinite-set.md)
alternatives matched it.

The remaining nine are deviations in which both checkers completed, so they
store no detail and stay `NEW`, as the corpus8 section explains. That is the
shape of this residual: `IsFiniteSet`, `CHOOSE`, the union defect and the
function-set defect are all wrong-answer deviations with no diagnostic on either
side, and no aggregator signature can ever retire them.

## corpus29 residuals

corpus29 was generated with FuzzTLA `bfc3a25`. Its first triage left 3 Apalache
crashes, 46 TLC crashes and 111 aggregator deviations as `NEW`. Each was
re-checked with `fuzztla print --corpus corpus29` and TLC commit `142d0ba`
(tla2tools `1.8.0-20260917.033119-76`) or Apalache 0.62.2 (build `f0dec98`).
With the signatures added since, 1 Apalache crash, 45 TLC crashes and 109
deviations remain `NEW`:

| Count | Stage | Cause | Class |
|---:|---|---|---|
| 42 | TLC crash | temporal formula TLC cannot translate; the four `cannot handle` ones quantify over a state-dependent domain | [tlc-temporal-formula-limits](tlc-temporal-formula-limits.md) |
| 2 | TLC crash | `StackOverflowError` (1005) on a 2,000- and a 4,000-line module | not reduced, see below |
| 1 | TLC crash | `OutOfMemoryError` after an initial-state evaluation error | not reduced, see below |
| 1 | Apalache crash | `SetInRule.powSetIn is not implemented for infinite type`, from `Nat \notin SUBSET (...)` | not reduced; the message is noted in [infinite-set-as-membership-element](infinite-set-as-membership-element.md) |
| 37 | aggregator | TLC pass, Apalache counterexample: an eventuality fails in the initial state | [`tlc-008`](../findings/TLC/tlc-008.md) |
| 22 | aggregator | TLC pass, Apalache counterexample: order-sensitive `ApaFoldSet` | [order-sensitive-set-fold](order-sensitive-set-fold.md) |
| 1 | aggregator | TLC counterexample, Apalache pass: order-sensitive `ApaFoldSet` | [order-sensitive-set-fold](order-sensitive-set-fold.md) |
| 16 | aggregator | TLC counterexample, Apalache pass: `ENABLED` in a `CASE` guard | [`apalache-bmc-019`](../findings/apalache-bmc/apalache-bmc-019.md) |
| 1 | aggregator | TLC pass, Apalache counterexample: `CHOOSE` with several witnesses | [choose-multiple-witnesses](choose-multiple-witnesses.md) |
| 3 | aggregator | TLC pass, Apalache counterexample: `23d225ff`, `34ca0062`, `64f6a67d` | not reduced |
| 29 | aggregator | TLC fail with the `Evaluating action property` wrapper | [function-application-outside-domain](function-application-outside-domain.md#under-a-temporal-property) |

The triage that followed the first run reclassified the other three: the
`ApaFoldSeqLeft` form of
[`apalache-temporal-002`](../findings/apalache-temporal/apalache-temporal-002.md)
(2 crashes), a `%` error inside the invariant that TLC printed without its own
`Error:` prefix ([`tlc-002`](../findings/TLC/tlc-002.md), `5ec18198`), and
`SubSeq` with an upper bound past the end
([subseq-outside-domain](subseq-outside-domain.md), 2 deviations).

The crashes that were not reduced:

- `08a3eb9a` and `2097bfa0` overflowed the stack after the first initial state.
  On a rerun with the default thread stack on another host, TLC reports an
  ordinary evaluation error instead (`CHOOSE` without a witness and a function
  application outside its domain) and exits 75. The overflow depends on the
  stack size, not on the specification alone.
- `8382764e` reports `In applying the function ... which is not in its domain`
  while computing the initial states and then runs out of memory, with a 512 MB
  or a 4 GB heap alike, and exits 255. After the error `ModelChecker` replays
  `Init` with a `CallStackTool` to print the nested expressions. A class
  histogram during the replay shows 400 MB in two `SemanticNode[]` arrays, the
  `CallStack`, and the error surfaces in `CallStack.toString`. A plausible
  mechanism is that `FcnLambdaValue.toString` swallows an exception while it
  formats the message: the `CallStackTool` froze the stack when the exception
  passed, so every later push is kept. A small module that follows this path
  did not reproduce the growth, so the mechanism is unconfirmed.

## corpus30 residuals

corpus30 was generated with FuzzTLA `80f63a5` and TLC commit `142d0ba`
(tla2tools `1.8.0-20260917.033119-76`). Its triage left 30 TLC crashes and 83
aggregator deviations as `NEW`; the Apalache crashes all matched the catalog or
were worker timeouts (6,954 of 7,486). Each `NEW` entry was re-checked with
`fuzztla print --corpus corpus30` and the same TLC, or Apalache 0.62.2 (build
`f0dec98`):

| Count | Stage | Cause | Class |
|---:|---|---|---|
| 26 | TLC crash | temporal formula TLC cannot translate: `must be of forms` | [tlc-temporal-formula-limits](tlc-temporal-formula-limits.md) |
| 2 | TLC crash | temporal formula TLC cannot translate: `cannot handle` | [tlc-temporal-formula-limits](tlc-temporal-formula-limits.md) |
| 2 | TLC crash | `StackOverflowError` (1005) after the initial states; both rerun to ordinary evaluation errors at a 1 MB stack and overflow only at 512 KB or less | stack-size dependent, see below |
| 33 | aggregator | TLC pass, Apalache counterexample: an evaluation error in the initial state exits 0; 16 also mask an initial invariant violation | [`tlc-008`](../findings/TLC/tlc-008.md) |
| 12 | aggregator | TLC pass, Apalache counterexample: order-sensitive `ApaFoldSet` in `Inv` or `Prop` | [order-sensitive-set-fold](order-sensitive-set-fold.md) |
| 1 | aggregator | TLC pass, Apalache counterexample: `IsFiniteSet(Nat)` in `Inv`, `9522bda4` | [`apalache-bmc-007`](../findings/apalache-bmc/apalache-bmc-007.md) |
| 1 | aggregator | TLC pass, Apalache counterexample: `CHOOSE` over `DOMAIN <<...>>` with a vacuous predicate, `a6befd0a` | [choose-multiple-witnesses](choose-multiple-witnesses.md) |
| 13 | aggregator | TLC fail with the `Evaluating action property Prop failed.` wrapper (11 Apalache pass, 2 counterexample) | [function-application-outside-domain](function-application-outside-domain.md#under-a-temporal-property) |
| 2 | aggregator | TLC fail storing a `StackOverflowError` detail; a rerun reports an ordinary evaluation error and exits 75 | stack-size dependent, see the corpus29 section |
| 12 | aggregator | TLC counterexample, Apalache pass: a `CASE` with an `ENABLED` guard and no `OTHER` | [`apalache-bmc-019`](../findings/apalache-bmc/apalache-bmc-019.md) |
| 1 | aggregator | TLC counterexample, Apalache pass: order-sensitive `ApaFoldSet` in `Init`, `f1dcd41c` | [order-sensitive-set-fold](order-sensitive-set-fold.md) |
| 1 | aggregator | TLC counterexample, Apalache pass: a `SUBSET`-valued fold combinator, `05b268ce` | [`apalache-bmc-020`](../findings/apalache-bmc/apalache-bmc-020.md) |
| 6 | aggregator | TLC counterexample, Apalache pass: `ENABLED` in a non-`CASE` position (an `IF` condition, a set-filter predicate, a tuple element) | not reduced; possible variants of [`apalache-bmc-019`](../findings/apalache-bmc/apalache-bmc-019.md) |
| 1 | aggregator | TLC fail, exit 150, `the identifier var1 is either undefined or not an operator` raised while TLC prepares liveness checking, `abb1ffd8` | not reduced |

The 12 `CASE` rows carry the `apalache-bmc-019` shape in the text of the
invariant's operator tree but were not individually reduced. Of the two
`cannot handle` crashes, `6279a66a` adds the state-dependent `identifier var0
is either undefined or not an operator` tail; `9f62c5a3` prints the bare line.

The two `StackOverflowError` crashes are the corpus29 class. With a 1 MB
thread stack, `f36f1791` reports `CHOOSE x \in S: P, but no element of S
satisfied P` and `fa4216c8` reports a function application outside its domain;
both overflow at 512 KB or less. The deepest observed frames are ordinary
expression evaluation, `Tool.evalImpl`/`evalApplImpl` recursion past 1,000
frames, so the producer is expression depth, not a specific operator: a
synthetic invariant of 1,000 nested `(1 + ...)` additions overflows at 512 KB
and passes at 1 MB.

`abb1ffd8` is the one deviation whose detail has a catalog text at a new exit
status: the triager's signature for
`In evaluation, the identifier \w+ is either undefined or not an operator.`
expects exit 75 ([`apalache-printer-008`](../findings/apalache-printer/apalache-printer-008.md)),
and the state-dependent-domain rows of
[tlc-temporal-formula-limits](tlc-temporal-formula-limits.md) are crashes. Its
property is `P ~> FALSE` where `P` reads `var1` inside an `ApaFoldSeqLeft`
lambda; a rerun reports the message with error 2280, which maps to exit status
150.

## corpus31 residuals

corpus31 was generated with FuzzTLA `80f63a5`, the first run that quarantines
candidates against `signatures/all-defects.toml` (3,239 entries in
`00-known-defects`). Its triage left 17 TLC crashes and 42 aggregator deviations
as `NEW`; the Apalache crashes all matched the catalog or were worker timeouts.
Each `NEW` deviation was rerun with FuzzTLA `66a1262`, TLC commit `142d0ba`
(tla2tools `1.8.0-20260917.033119-76`) and Apalache 0.62.2 (build `f0dec98`);
all 42 reproduced their verdicts. No entry needs a new finding or class:

| Count | Stage | Cause | Class |
|---:|---|---|---|
| 17 | TLC crash | temporal formula TLC cannot translate: `must be of forms` | [tlc-temporal-formula-limits](tlc-temporal-formula-limits.md) |
| 31 | aggregator | TLC pass, Apalache counterexample: order-sensitive `ApaFoldSet` in `Inv` or `Prop` | [order-sensitive-set-fold](order-sensitive-set-fold.md) |
| 4 | aggregator | TLC pass, Apalache counterexample: order-sensitive `ApaFoldSet` in `Next` | [order-sensitive-set-fold](order-sensitive-set-fold.md#in-the-next-state-action) |
| 1 | aggregator | TLC pass, Apalache counterexample: order-sensitive `ApaFoldSet` in `Init`, `a6d495e7` | [order-sensitive-set-fold](order-sensitive-set-fold.md) |
| 2 | aggregator | TLC pass, Apalache counterexample: an evaluation error in the initial state exits 0; `611e21df` also masks an initial invariant violation | [`tlc-008`](../findings/TLC/tlc-008.md) |
| 1 | aggregator | TLC pass, Apalache counterexample: `CHOOSE` in `Next` with several witnesses, `2af3949d` | [choose-multiple-witnesses](choose-multiple-witnesses.md) |
| 3 | aggregator | TLC counterexample, Apalache pass: order-sensitive `ApaFoldSet` in `Init` (`1dbd8909`), `Inv` (`b8c1da55`) and `Prop` (`fb460876`) | [order-sensitive-set-fold](order-sensitive-set-fold.md) |

The 31 `Inv`/`Prop` folds were rerun but not individually reduced. Every
combinator returns its element argument or a value built only from it, such as
`VariantGetOrElse("Tag0", b, FALSE)`, `<<b>>`, `IF a /= b <=> FALSE THEN a ELSE b`,
or an inner fold seeded with `b`. In `b489b9be` the combinator is
`CHOOSE r \in R : b`: TLC never evaluates the no-witness case `b = FALSE`, because
the combinator ignores its accumulator and TLC passes arguments lazily.

In `2af3949d` the only transition quantifies over
`CHOOSE s \in {{}, {"1"}, {"1", "2"}} : var0`. TLC picks `{}`, so the transition
is disabled and `Inv == var0` holds; Apalache picks a non-empty set and reaches
`var0 = FALSE` in state 1.

`fb460876` nests an `ApaFoldSeqLeft` whose combinator returns its accumulator in
an `ApaFoldSet` combinator, so the set fold returns its last element. An MWE with
`Init == flag = ApaFoldSet(L8, FALSE, {TRUE, FALSE})` confirms TLC computes `TRUE`
and Apalache `FALSE`; the property `<>fold ~> FALSE` then fails only in TLC.

## corpus33 residuals

corpus33 quarantined 3,243 candidates in `00-known-defects`. Its triage left 12
TLC crashes and 43 aggregator deviations as `NEW`. Every Apalache crash (67)
matched the catalog or was a worker timeout (38). Each `NEW` deviation was
rerun with FuzzTLA `d4f274c` and TLC commit `142d0ba` (tla2tools
`1.8.0-20260917.033119-76`), with `SPECIFICATION Spec`, `INVARIANT Inv` and
`PROPERTY Prop`. The Apalache verdicts are those recorded by Apalache 0.62.2
(build `f0dec98`). No entry needs a new finding or class:

| Count | Stage | Cause | Class |
|---:|---|---|---|
| 12 | TLC crash | a label directly on `[][A]_v`: `must be of forms` | [`tlc-013`](../findings/TLC/tlc-013.md) |
| 1 | parser fail | set-map body `var0 \in S <=> FALSE` printed without parentheses, `044bf489` | [`apalache-printer-010`](../findings/apalache-printer/apalache-printer-010.md) |
| 37 | aggregator | TLC pass, Apalache counterexample: order-sensitive `ApaFoldSet` | [order-sensitive-set-fold](order-sensitive-set-fold.md) |
| 2 | aggregator | TLC counterexample, Apalache pass: order-sensitive `ApaFoldSet` in `Inv`, `2a9e4c79` and `341992c0` | [order-sensitive-set-fold](order-sensitive-set-fold.md) |
| 1 | aggregator | TLC pass, Apalache counterexample: `Inv` negates `CHOOSE` over `{TRUE, FALSE}` with a predicate true for both, `e89bc48f` | [choose-multiple-witnesses](choose-multiple-witnesses.md) |
| 1 | aggregator | TLC pass, Apalache counterexample: `[](P) => var0` raises `CHOOSE` without a witness and masks an initial invariant violation, exit 0, `a3b8df93` | [`tlc-008`](../findings/TLC/tlc-008.md) |
| 2 | aggregator | TLC fail storing a `StackOverflowError` detail; with a 4 MB stack both report `CHOOSE` without a witness in `Inv` and exit 75, `20a64241` and `30f63219` | stack-size dependent, see the corpus30 section |

On TLC's side, 39 of the 43 deviations rerun to a clean pass. The other two
reproduce their counterexamples with exit 12. The fold combinators were read
but not individually reduced. Most return their element argument, directly or
through an inner fold seeded with it. `341992c0` and `b9f8856f` fold with
`a => b`, and `26b07768` with `<<b>>`. `e89bc48f` also contains folds, but its
combinators produce a constant, so the fold order does not decide its verdict.

`044bf489` is a `bitflip` mutant. SANY rejects it with
`Multiply-defined symbol 'var0'`. With a fresh bound name in place of the state
variable, SANY would read the same text as a filter.

`fuzztla print --corpus corpus33` fails because the corpus configuration
predates the `feature_coverage` rename. `fuzztla print` without `--corpus`
decodes the entries.

## corpus35 residuals

corpus35 quarantined 3,252 candidates in `00-known-defects`. Its triage left 13
TLC crashes, 1 Apalache crash and 36 of the 37,675 aggregator deviations as
`NEW`; the parser reports were empty. Each `NEW` entry was rerun with FuzzTLA
`ef6df80` and TLC commit `142d0ba` (tla2tools `1.8.0-20260917.033119-76`), with
`SPECIFICATION Spec`, `INVARIANT Inv` and `PROPERTY Prop`. The Apalache verdicts
are those recorded by Apalache 0.62.2 (build `f0dec98`). One entry needs a new
finding:

| Count | Stage | Cause | Class |
|---:|---|---|---|
| 13 | TLC crash | a label directly on `[][A]_v`: `must be of forms` | [`tlc-013`](../findings/TLC/tlc-013.md) |
| 1 | Apalache crash | `OutOfMemoryError` in `BoundedChecker` at step 2 on a 1.4 MB IR, `4503a44d` | resource exhaustion, not a tool diagnosis |
| 30 | aggregator | TLC pass, Apalache counterexample: order-sensitive `ApaFoldSet` | [order-sensitive-set-fold](order-sensitive-set-fold.md) |
| 2 | aggregator | TLC counterexample, Apalache pass: order-sensitive `ApaFoldSet`, in `Inv` for `46f201a4` and in `Prop` for `9f739ce8` | [order-sensitive-set-fold](order-sensitive-set-fold.md) |
| 2 | aggregator | TLC pass, Apalache counterexample: `Next` picks with a `CHOOSE` whose predicate is true for both elements, `4bfacb8a` and `9a8c3677` | [choose-multiple-witnesses](choose-multiple-witnesses.md) |
| 1 | aggregator | TLC pass, Apalache counterexample: a function applied outside its domain in an eventuality of the initial state masks an initial invariant violation, exit 0, `e89ad464` | [`tlc-008`](../findings/TLC/tlc-008.md) |
| 1 | aggregator | TLC counterexample, Apalache pass: `Prop == <>(~(FALSE ~> FALSE)) => FALSE`, a valid property, exit 12, `c9e9762e` | new, [`tlc-014`](../findings/TLC/tlc-014.md) |

Every recorded verdict reproduced. 32 of the 36 deviations rerun to a clean
pass, `46f201a4` reports `Invariant Inv is violated by the initial state` with
exit 12, `9f739ce8` a violated temporal property with exit 13, `c9e9762e` a
violated temporal property with exit 12, and `e89ad464` prints its evaluation
error and the masked invariant violation while exiting 0.

The fold combinators were read but not individually reduced; most return their
element argument, directly or through an inner fold seeded with it. In 14 of the
32 the order-sensitive fold occurs only in `Prop`, so the deviation is reached
through the temporal property rather than through the invariant.

The Apalache `OutOfMemoryError` carries no stack: the worker JVM prints
`Terminating due to java.lang.OutOfMemoryError: Java heap space` and exits.
Rerunning the stored IR with the configured 1 GiB heap reproduces it inside
`PASS #13: BoundedChecker`, after 4m35s in the original run, on an input whose
IR is 1.4 MB. corpus34 has one entry of the same kind. Like the 3 worker
timeouts in the same report, it establishes no conformance difference; unlike
them it has no bucket, so it lands in `NEW`.

## corpus36 residuals

corpus36 quarantined 3,258 candidates in `00-known-defects`. Its triage left 9
TLC crashes and 34 of the 37,827 aggregator deviations as `NEW`; the parser
reports were empty, and the 33 Apalache crash outcomes all matched the catalog
or were worker timeouts (5). Each `NEW` entry was rerun with FuzzTLA `3f4ce33`
and TLC commit `142d0ba` (tla2tools `1.8.0-20260917.033119-76`), with
`SPECIFICATION Spec`, `INVARIANT Inv` and `PROPERTY Prop`, and Apalache 0.62.2
(build `f0dec98`). One entry needs a new finding:

| Count | Stage | Cause | Class |
|---:|---|---|---|
| 9 | TLC crash | a label directly on `[][A]_v`: `must be of forms` | [`tlc-013`](../findings/TLC/tlc-013.md) |
| 25 | aggregator | TLC pass, Apalache counterexample: order-sensitive `ApaFoldSet` | [order-sensitive-set-fold](order-sensitive-set-fold.md) |
| 2 | aggregator | TLC counterexample, Apalache pass: order-sensitive `ApaFoldSet` in `Prop`, `3953dd8f` and `b87f6eae` | [order-sensitive-set-fold](order-sensitive-set-fold.md) |
| 4 | aggregator | TLC pass, Apalache counterexample: an out-of-bounds tuple index under a labeled or negated `[]` exits 0, `216d4b2e`, `68ea3716`, `a6d4f140` and `e2ea7704` | [`tlc-008`](../findings/TLC/tlc-008.md) |
| 2 | aggregator | TLC pass, Apalache counterexample: a `CHOOSE` in `Inv` whose predicate is true for every element, `050ed260` and `91f493fb` | [choose-multiple-witnesses](choose-multiple-witnesses.md) |
| 1 | aggregator | TLC counterexample, Apalache pass: `\E p \in ApaFoldSet(...)` over a constant, function-set-valued fold keeps the transition disabled, `eebf3287` | new, [`apalache-bmc-021`](../findings/apalache-bmc/apalache-bmc-021.md) |

Every recorded verdict reproduced. 31 of the 34 deviations rerun to a clean
pass, `3953dd8f` and `b87f6eae` report a violated temporal property with exit
13, and `eebf3287` reports `Invariant Inv is violated` with exit 12. The four
`tlc-008` entries exit 0 after printing `Attempted to access index 0 of tuple`;
none of them also masks an invariant violation.

The nine TLC crashes are one shape, `label :: [][A]_v`, the row that corpus32,
corpus33 and corpus35 also contributed. Five of the nine wrap a constant action,
`label :: [][FALSE]_FALSE`.

The fold combinators of the 27 order-sensitive entries were read but not
individually reduced; all return their element argument, directly or wrapped in
a one-element tuple, a record field, a singleton function application or a
`CASE`. `eebf3287` also carries a fold, but its combinator ignores both
parameters, so the fold is order-insensitive and the deviation is the new
encoding defect rather than this class.

TLC2 prints its run date as its `Version`, not a build stamp, so the corpus's
stacktraces do not identify the tla2tools snapshot. The workflow shades
tla2tools into `fuzztla.jar`, with no Maven metadata for it; the 862 shaded
`tlc2`, `tla2sany`, `util` and `pcal` entries are byte-identical to
`1.8.0-20260917.033119-76`, which fixes the TLC commit above. The two extra
entries are FuzzTLA's `Apalache.tla` and `Variants.tla`.

## corpus40 residuals

corpus40 quarantined 3,258 candidates in `00-known-defects`. Its triage left 10
TLC crashes and 31 of the 40,069 aggregator deviations as `NEW`; the parser
reports were empty, and the 31 Apalache crash outcomes all matched the catalog
or were worker timeouts (7). Each `NEW` entry was rerun with FuzzTLA `56933a3`
and TLC commit `142d0ba` (tla2tools `1.8.0-20260917.033119-76`), with
`SPECIFICATION Spec`, `INVARIANT Inv` and `PROPERTY Prop`. Apalache 0.62.2
(build `f0dec98`) was rerun on `cadd3b01`, `6f624d7f` and `e6a186ec`; the other
Apalache verdicts are the recorded ones. One entry needs a new finding:

| Count | Stage | Cause | Class |
|---:|---|---|---|
| 10 | TLC crash | a label directly on `[][A]_v`: `must be of forms` | [`tlc-013`](../findings/TLC/tlc-013.md) |
| 20 | aggregator | TLC pass, Apalache counterexample: order-sensitive `ApaFoldSet` | [order-sensitive-set-fold](order-sensitive-set-fold.md) |
| 2 | aggregator | TLC counterexample, Apalache pass: order-sensitive `ApaFoldSet`, in `Inv` for `5e7948ee` and in `Prop` for `52f42327` | [order-sensitive-set-fold](order-sensitive-set-fold.md) |
| 4 | aggregator | TLC pass, Apalache counterexample: a `CHOOSE` whose predicate is true for several elements, in a `Next` guard for `8c2d5e43` and `a848f8da`, in `Prop` for `ba47e906` and `e6a186ec` | [choose-multiple-witnesses](choose-multiple-witnesses.md) |
| 2 | aggregator | TLC pass, Apalache counterexample: an evaluation error in an eventuality of the initial state masks an initial invariant violation, exit 0, `319127dc` and `9d2522df` | [`tlc-008`](../findings/TLC/tlc-008.md) |
| 1 | aggregator | TLC pass, Apalache counterexample: a fold combinator `CASE var0 -> FALSE [] TRUE -> var0` in `Prop` with both guards true, `cadd3b01` | [case-multiple-true-guards](case-multiple-true-guards.md) |
| 1 | aggregator | TLC counterexample, Apalache pass: `\E p \in ApaFoldSet(...)` over a function-set-valued fold keeps the transition disabled, `6f624d7f` | [`apalache-bmc-021`](../findings/apalache-bmc/apalache-bmc-021.md) |
| 1 | aggregator | TLC fail, Apalache counterexample: `step` reported undefined in a `~>` property whose fold combinator computes `parameter10 * step`, error 2280, `db6bd5b4` | new, [`tlc-015`](../findings/TLC/tlc-015.md) |

Every recorded TLC verdict reproduced. 25 of the 31 deviations rerun to a clean
pass; `319127dc` and `9d2522df` print their evaluation error and
`Invariant Inv is violated by the initial state` and still exit 0.
`5e7948ee` reports the initial invariant violation with error 2107,
`52f42327` a violated temporal property with error 2116, `6f624d7f`
`Invariant Inv is violated` with error 2110, and `db6bd5b4` fails with error
2280.

The ten TLC crashes are the `label :: [][A]_v` row of corpus32 to corpus36.
Seven have the subscript `FALSE`, such as `label3 :: [][FALSE]_FALSE` in
`a57df571`; `72b2a90b` and `cc5b2792` have `var0`, and `70aeb893` an
`ApaFoldSeqLeft`.

The fold combinators of the 22 order-sensitive entries were read but not
individually reduced. All return their element argument, directly, through an
inner fold seeded with it, through `EXCEPT ![step] = p`, or through an `IF`
whose condition is constant `FALSE` in context. `cadd3b01` and `9d2522df` fold
only over sequences, and their deviations have the causes in the table.
`e6a186ec` has an order-insensitive fold under an unused `LET` in `Inv`.

`db6bd5b4` is the corpus30 `abb1ffd8` diagnostic, which that section left
unreduced. It reduces to a `RECURSIVE` operator whose operator argument
multiplies a state variable. The `Naturals.tla` stub `a*b == TRUE` makes that
argument constant-level for SANY, and TLC's liveness translation does not look
inside operator arguments when it recomputes the level; `tlc-015` gives the
reproduction and the code path.

## corpus41 residuals

corpus41 quarantined 3,343 candidates in `00-known-defects`. Its triage left 22
TLC crashes and 67 of the 60,471 aggregator deviations as `NEW`; the parser
report was empty, and the 146 Apalache crash outcomes all matched the catalog
or were worker timeouts (98). Each `NEW` entry was rerun with FuzzTLA `af10a91`
and TLC commit `142d0ba` (tla2tools `1.8.0-20260917.033119-76`, shaded into
`fuzztla.jar`), with `SPECIFICATION Spec`, `INVARIANT Inv` and `PROPERTY Prop`.
Apalache 0.62.2 (build `f0dec98`) was rerun on `01ef2cc4`; the other Apalache
verdicts are the recorded ones. No entry needs a new finding:

| Count | Stage | Cause | Class |
|---:|---|---|---|
| 20 | TLC crash | a label directly on `[][A]_v`: `must be of forms` | [`tlc-013`](../findings/TLC/tlc-013.md) |
| 2 | TLC crash | `StackOverflowError` (1005) in the recursive `ApaFoldSeqLeft` of `Apalache.tla` over an exponentially growing sequence; with a 4 MB stack the run exhausts the heap in `Sequences.Concat` | resource limit, see below |
| 56 | aggregator | TLC pass, Apalache counterexample: order-sensitive `ApaFoldSet` | [order-sensitive-set-fold](order-sensitive-set-fold.md) |
| 3 | aggregator | TLC counterexample, Apalache pass: order-sensitive `ApaFoldSet`, in `Inv` for `40498b3a` and `79d122aa` and in `Prop` for `8ebe61e4` | [order-sensitive-set-fold](order-sensitive-set-fold.md) |
| 3 | aggregator | TLC pass, Apalache counterexample: an evaluation error in the initial state exits 0; `06c8d6ea` and `cbbcda02` negate a `[]` property through `=>` and also mask an initial invariant violation, `d5483d29` has a labeled `[]` | [`tlc-008`](../findings/TLC/tlc-008.md) |
| 2 | aggregator | TLC pass, Apalache counterexample: a `CHOOSE` whose predicate is true for several elements, in `Inv` for `240770dd` and in a `Next` guard for `4f23e165` | [choose-multiple-witnesses](choose-multiple-witnesses.md) |
| 1 | aggregator | TLC pass, Apalache counterexample: `Inv == ~(CASE var0 -> FALSE [] TRUE -> var0)` with both guards true at `var0 = TRUE`, `01ef2cc4` | [case-multiple-true-guards](case-multiple-true-guards.md) |
| 1 | aggregator | TLC counterexample, Apalache pass: `\E p \in ApaFoldSet(...)` over a function-set-valued fold keeps the transition disabled, `8dd6ee9e` | [`apalache-bmc-021`](../findings/apalache-bmc/apalache-bmc-021.md) |
| 1 | aggregator | TLC fail, Apalache counterexample: `Attempted to construct a set with too many elements (>1000000)` for a `CHOOSE` without a witness over a finite function set, `e2c03d31` | [choose-without-witness](choose-without-witness.md), see below |

Every recorded TLC verdict reproduced. The 59 order-sensitive entries were
confirmed by rerunning TLC with a local `Apalache.tla` whose `ApaFoldSet`
applies the combinator in the reverse order,
`__Op(ApaFoldSet(__Op, __v, __T), __w)`. All 59 change their TLC verdict. 56
move to Apalache's verdict: the three TLC counterexamples pass, and the others
report a violation, `The invariant of Inv is equal to FALSE` or `The property
of Prop is equal to FALSE`. `297eb9e9`, `5766e077` and `de888de8` reach an
evaluation error instead. The other eight deviations keep their TLC verdict
under the reversed fold and have the causes in the table.

The 20 TLC crashes with error 2214 are the `label :: [][A]_v` row of corpus32
to corpus40. Fourteen have the subscript `FALSE`, such as
`label3 :: [][(FALSE <=> FALSE)]_FALSE` in `8508b195`; four have `var0`, and
`16c1aa75` and `52ce1b0d` a function and a record constructor.

`d5483d29` is the labeled `[]` case of `tlc-008`: its property
`label10 :: [](CASE <<TRUE, FALSE>>[step] -> FALSE)` fails on index 0 in the
initial state and TLC exits 0. In a reduced module,
`lbl :: [](<<TRUE, FALSE>>[x] = TRUE)` exits 0, and the same property without
the label exits 12 on the initial invariant violation.

The two `StackOverflowError` crashes, `0c11b981` and `b933c6f6`, render to the
same specification. Its `Next` folds `var1` with a combinator that doubles its
accumulator, so the length of `var1` grows exponentially with the step. With
the 1 MB default stack TLC overflows in the recursive `ApaFoldSeqLeft`; with 4
MB or 64 MB it fails with `Java heap space` in `Sequences.Concat`, exit 75. The
producer is the specification's value size, not a TLC defect.

`e2c03d31` evaluates `CHOOSE b \in [[S -> T] -> {1, 2, 3}] : acc` with
`|[S -> T]| = 27`, so the bound has 3^27 elements, and the fold's accumulator
`acc` is `FALSE`. TLC stops at its enumeration limit before evaluating the
predicate. With `S` reduced to one element, TLC reports `CHOOSE x \in S: P, but
no element of S satisfied P` and exits 75, the choose-without-witness class.
The conformance documents describe the enumeration limit only for infinite
sets; the triager has no signature for it on a finite set.

## corpus42 residuals

corpus42 quarantined 3,295 candidates in `00-known-defects`. Its triage left 12
of the 139 TLC crashes and 62 of the 40,567 aggregator deviations as `NEW`; the
parser report was empty, and the 61 Apalache crash outcomes all matched the
catalog or were worker timeouts (30). Each `NEW` entry was rendered with FuzzTLA
`a13a4d1` and rerun with TLC commit `142d0ba` (tla2tools
`1.8.0-20260917.033119-76`), with `SPECIFICATION Spec`, `INVARIANT Inv` and
`PROPERTY Prop`. The Apalache 0.62.2 verdicts are the recorded ones. No entry
needs a new finding:

| Count | Stage | Cause | Class |
|---:|---|---|---|
| 11 | TLC crash | a label directly on `[][A]_v`: `must be of forms` | [`tlc-013`](../findings/TLC/tlc-013.md) |
| 1 | TLC crash | `CHOOSE` without a witness escapes as error 1000 while TLC fingerprints the initial state, `3e2b8922` | [`tlc-001`](../findings/TLC/tlc-001.md), see below |
| 53 | aggregator | TLC pass, Apalache counterexample: order-sensitive `ApaFoldSet` | [order-sensitive-set-fold](order-sensitive-set-fold.md) |
| 6 | aggregator | TLC counterexample, Apalache pass: order-sensitive `ApaFoldSet` | [order-sensitive-set-fold](order-sensitive-set-fold.md) |
| 2 | aggregator | TLC pass, Apalache counterexample: a `CHOOSE` whose predicate is true for several elements, in `Inv` for `8cd1b7d5` and in a `Next` guard for `03ab4962` | [choose-multiple-witnesses](choose-multiple-witnesses.md) |
| 1 | aggregator | TLC pass, Apalache counterexample: `Prop == ~[](...)` fails on a function application outside the domain in the initial state; TLC prints the initial `Inv` violation and exits 0, `db7bc163` | [`tlc-008`](../findings/TLC/tlc-008.md) |

Every recorded TLC verdict reproduced. The 59 order-sensitive entries were
confirmed as in corpus41, with a local `Apalache.tla` whose `ApaFoldSet`
applies the combinator in the reverse order. All 59 change their TLC verdict.
The six TLC counterexamples: five pass, and `dadcbb0b` reaches an unmatched
`CASE`. Of the 53 TLC passes, 52 report a violation of `Inv` or `Prop`, and
`2c526202` reaches a `CHOOSE` without a witness. The three other deviations
contain no `ApaFoldSet`, keep their TLC verdict under the reversed fold and
have the causes in the table.

The eleven error-2214 crashes are the `label :: [][A]_v` row of corpus32 to
corpus41, for example `label6 :: [][FALSE]_var0` in `1a74aa18`.

`3e2b8922` is a new surfacing of `tlc-001`, not a new defect. Its `Init`
assigns `var0` a set whose first element is a record field holding
`Variant("Tag0", <<ApaFoldSeqLeft(L, FALSE, ...)>>)`, where `L` evaluates
`CHOOSE b \in {1, 2, 3}: acc` with the accumulator `FALSE`. `Variant` is a
function constructor, which TLC keeps as a lazy `FcnLambdaValue`. It reduces to

```tla
Init == x = { <<[t \in {1} |-> CHOOSE i \in {1}: FALSE]>>[1], [t \in {1} |-> TRUE] }
```

which exits 255 with `The exception was a java.lang.RuntimeException` and the
`CHOOSE` message. With `-debug`, the error is raised in
`FcnLambdaValue.toFcnRcd`, called from `FcnRcdValue.compareTo` while
`SetEnumValue.normalize` sorts the set for `TLCStateMut.fingerPrint` in
`ModelChecker$DoInitFunctor.addElement`. The `tlc-001` reproduction takes the
same path through `UnionValue.fingerPrint` and a lazy `SetPredValue`: an
evaluation error in a lazy value forced by fingerprinting escapes the evaluator's
error classification. Replacing the `CHOOSE` with `<<5>>[2]` gives the same
wrapper with an out-of-bounds message; without the second function in the set,
or without the tuple around the first, TLC reports the ordinary error and exits
75. The `tlc-001` signature matches only the function-domain message, so the
triager leaves this entry `NEW`.

## corpus43 residuals

corpus43 is the first corpus that applies Community Modules operators
(CommunityModules release `202609120237`, commit `9aae8ea`), linked with
`link = "instance"` as described in the
[community-modules manual](../docs/manual/community-modules.md). It quarantined
3,069 candidates in `00-known-defects`. Its triage left 58 of the 248 TLC
crashes, 54 of the 472 Apalache crash outcomes and 938 of the 41,895 aggregator
deviations as `NEW`; the parser report was empty, and the other Apalache crash
outcomes matched the catalog or were worker timeouts (353). The `NEW` TLC
crashes were classified from their stored transcripts. The rerun entries were
rendered with FuzzTLA `a13a4d1` and checked with TLC commit `142d0ba`
(tla2tools `1.8.0-20260917.033119-76`) and `CommunityModules.jar` on the class
path, with `SPECIFICATION Spec`, `INVARIANT Inv` and `PROPERTY Prop`. Apalache
0.62.2 (build `f0dec98`) was rerun on `075a7ff4` and `377f54ed`; the other
Apalache verdicts are the recorded ones. Four
entry groups need new findings:

| Count | Stage | Cause | Class |
|---:|---|---|---|
| 39 | TLC crash | `IsInjective` override rejects a record or an unevaluated function constructor, error 2283 | [`tlc-016`](../findings/TLC/tlc-016.md), exit 255 as in [`tlc-002`](../findings/TLC/tlc-002.md) |
| 10 | TLC crash | a label directly on `[][A]_v`: `must be of forms` | [`tlc-013`](../findings/TLC/tlc-013.md) |
| 7 | TLC crash | `LongestCommonPrefix({})`, error 2283 | [choose-without-witness](choose-without-witness.md), exit 255 as in `tlc-002` |
| 1 | TLC crash | `AntiFunction` of a non-injective function in a temporal property, error 2154, `3914df96` | [`tlc-017`](../findings/TLC/tlc-017.md), exit 255 as in `tlc-002` |
| 1 | TLC crash | `SumBag` of a function with multiplicity 0 under `[][...]_FALSE`, error 2176, `932e82c8` | [bag-nonpositive-multiplicity](bag-nonpositive-multiplicity.md), exit 255 as in `tlc-002` |
| 54 | Apalache crash | `OutOfMemoryError: Java heap space` at 1 GB, no stack trace | [`apalache-performance-001`](../findings/apalache-performance/apalache-performance-001.md), see below |
| 427 | aggregator | TLC fail: `AntiFunction` of a non-injective function; Apalache counterexample (223) or pass (204) | [`tlc-017`](../findings/TLC/tlc-017.md) |
| 209 | aggregator | TLC fail: `FoldBag` of a value not in `Nat`, through `SumBag` or `ProductBag`; Apalache counterexample (164) or pass (45) | [bag-nonpositive-multiplicity](bag-nonpositive-multiplicity.md) |
| 173 | aggregator | TLC fail: `IsInjective` rejects its argument; Apalache counterexample (134) or pass (39) | [`tlc-016`](../findings/TLC/tlc-016.md) |
| 59 | aggregator | TLC fail: `LongestCommonPrefix` of `{}`; Apalache counterexample (49) or pass (10) | [choose-without-witness](choose-without-witness.md) |
| 35 | aggregator | TLC pass and Apalache counterexample (22), or the reverse (13): the order of `SetToSeq` | [order-sensitive-set-fold](order-sensitive-set-fold.md), see below |
| 25 | aggregator | TLC pass and Apalache counterexample (21), or the reverse (4): order-sensitive `ApaFoldSet` | [order-sensitive-set-fold](order-sensitive-set-fold.md) |
| 5 | aggregator | TLC pass, Apalache counterexample: a `CHOOSE` with several witnesses, `4c1fe437`, `6d4a9f23`, `be938c32`, `e3faf71f` and `f2edbd22` | [choose-multiple-witnesses](choose-multiple-witnesses.md) |
| 2 | aggregator | `IndexFirstSubSeq(<<>>, t)` is 1 in TLC and 0 in Apalache, `0081cf2a` and `e06e2dbe` | [`apalache-rewiring-001`](../findings/apalache-rewiring/apalache-rewiring-001.md) |
| 2 | aggregator | TLC pass, Apalache counterexample: an evaluation error in the initial state exits 0, under `<>` in `faa6c649` and under nested `[]` in `ffc934bc` | [`tlc-008`](../findings/TLC/tlc-008.md) |
| 1 | aggregator | TLC counterexample, Apalache pass: `Prop == CASE var0 -> FALSE [] IsFiniteSet(...) -> var0` with both guards true, `22c43654` | [case-multiple-true-guards](case-multiple-true-guards.md) |

The four error-message groups of the aggregator are identified by TLC's stored
diagnostic line: `The value` for `AntiFunction`, which is the line after the
override wrapper, and the `IsInjective`, `LongestCommonPrefix` and `FoldBag`
messages. Four `AntiFunction` entries (`0051805f`, `012c6491`, `02a570ec`,
`01433fbe`) and two `FoldBag` entries (`01a17416`, `05e43a7e`) were rerun, and
of the TLC crashes `177b83d4`, the source of the `tlc-016` reduction. The TLC
crashes report the same messages as the aggregator entries
but exit 255, because the error constants 2283
(`TLC_MODULE_ONE_ARGUMENT_ERROR`), 2154 (`TLC_MODULE_VALUE_JAVA_METHOD_OVERRIDE`)
and 2176 (`TLC_MODULE_APPLYING_TO_WRONG_VALUE`) are unmapped, the `tlc-002`
defect; `tlc-002` now lists them. `LongestCommonPrefix` is
defined as a `CHOOSE` over `CommonPrefixes(S)`, which is empty for `S = {}`;
Apalache evaluates the same definition. The ten error-2214 crashes are the
`label :: [][A]_v` row of corpus32 to corpus42.

All 70 pass/counterexample deviations were rerun, and the recorded TLC verdict
reproduced for 68. The two others are the `tlc-008` entries, for which the
rerun prints the evaluation error that the recorded pass hides. Each entry was
then rerun twice more: with `ApaFoldSet` applying the combinator in reverse
order, as in corpus41, and with the `SetToSeq` alias of the specification
replaced by `Reverse(SetToSeq(...))`. The reversed fold changes the TLC
verdict of the 25 fold entries: 23 move to Apalache's verdict, and `4b2090cb`
and `772a075a` reach a `CHOOSE` without a witness. The reversed sequence
changes the verdict of 35 of the 37 entries that apply `SetToSeq`: 33 move to
Apalache's verdict and 2 reach an evaluation error. Apalache defines `SetToSeq`
as `__ApalacheFoldSet` with `Append`, so the order of the sequence is the
order of the fold, while TLC's Java override returns one fixed order. The two
other `SetToSeq` entries also apply `IndexFirstSubSeq` to `<<>>` and keep their
verdict under both reversals; they are the `apalache-rewiring-001` rows.

A bag with a non-positive multiplicity is a new class, documented in
[bag-nonpositive-multiplicity](bag-nonpositive-multiplicity.md). The generator types a bag as `a -> Int`, so a multiplicity can be 0 or
negative, and the rerun entries have multiplicity 0, as in
`SumBag([x \in {1, 2, 3} |-> step])` with `step = 0` (`932e82c8`). Such a
function is not a bag. The Community Modules definition of `FoldBag` then
applies `pow[B[x]]` outside the domain `Nat \ {0}` of `pow`, so TLC's error
follows the definition. Its message, `which is not an element of Nat: 0 (in:
1:>0)`, names the wrong set. Apalache rewires `SumBag(B)` as the sum of
`y * B[y]` and `ProductBag(B)` as the product of `y ^ B[y]`, which are defined
for every multiplicity.

The 54 Apalache crashes share heavy use of `SubSeq` and `\o`, which the
rewired `SequencesExt` operators produce: 94% and 93% of them against 31% and
22% of the other entries past the parser. `075a7ff4` runs out of heap memory in
a fresh JVM with `-Xmx1g`, so the crash is not caused by state accumulated in
the worker. The smallest entry, `377f54ed`, reduces to
`LongestCommonPrefix(SubSeqs(<<1, 2, 3>>))`, which is the reproduction of
`apalache-performance-001`. 20 of the 54 entries apply `LongestCommonPrefix`;
the other 34 were not reduced.

The triager now classifies the four error-message groups and their TLC
crashes: `IsInjective` as `tlc-016`, `AntiFunction` as `tlc-017`,
`LongestCommonPrefix({})` as choose-without-witness (crashes as `tlc-002`), and
`FoldBag` as bag-nonpositive-multiplicity (crashes as `tlc-002`). Rerun on
corpus43, it leaves 10 TLC crashes (the `tlc-013` labels), the 54 Apalache
out-of-memory exits, which print no stack trace to match, and the 70
pass/counterexample deviations as `NEW`.

## corpus44 residuals

corpus44 repeats the corpus43 configuration with Community Modules operators
and the recall-first `signatures/all-defects.toml`, which quarantined 3,629
candidates in `00-known-defects`. Its triage left 10 of the 168 TLC crashes, 2
of the 144 Apalache crash outcomes and 77 of the 39,853 aggregator deviations
as `NEW`. The parser report was empty, and the other Apalache crash outcomes
matched the catalog or were worker timeouts (78). Every `NEW` entry was
rendered with FuzzTLA `91871fd`. The aggregator entries were rerun with TLC
commit `142d0ba` (tla2tools `1.8.0-20260917.033119-76`) and
`CommunityModules.jar` (commit `9aae8ea`) on the class path, with
`SPECIFICATION Spec`, `INVARIANT Inv`, and `PROPERTY Prop` unless `Prop` is
`TRUE`. Apalache 0.62.2 (build `f0dec98`) was rerun on the crashes and on
`41ca4840`. Two entry groups need new findings:

| Count | Stage | Cause | Class |
|---:|---|---|---|
| 10 | TLC crash | a label directly on `[][A]_v`: `must be of forms` | [`tlc-013`](../findings/TLC/tlc-013.md) |
| 2 | Apalache crash | `OutOfMemoryError: Java heap space` at 1 GB, no stack trace, `041ad8a7` and `ed052a2b` | not reduced, see below |
| 41 | aggregator | TLC pass and Apalache counterexample (36), or the reverse (5): the order of `SetToSeq` | [order-sensitive-set-fold](order-sensitive-set-fold.md) |
| 25 | aggregator | TLC pass and Apalache counterexample (24), or the reverse (1): order-sensitive `ApaFoldSet` | [order-sensitive-set-fold](order-sensitive-set-fold.md) |
| 3 | aggregator | the other order reaches an undefined expression: `Head(<<>>)` through `SetToSeq` in `ea69bae0`, an unmatched `CASE` through `SetToSeq` in `c860a666`, a `CHOOSE` without a witness through `ApaFoldSet` in `e3f84a75` | [order-sensitive-set-fold](order-sensitive-set-fold.md) |
| 3 | aggregator | TLC pass, Apalache counterexample: a `CHOOSE` with several witnesses, `b5623fe7`, and through `Inverse` in `3889be64` and `f06cd79a` | [choose-multiple-witnesses](choose-multiple-witnesses.md) |
| 3 | aggregator | TLC counterexample, Apalache pass: `ExistsSurjection(S, {})` with `S # {}`, `5ae1c837`, `531d2ddf` and `d6adcdd4` | [`apalache-rewiring-002`](../findings/apalache-rewiring/apalache-rewiring-002.md) |
| 1 | aggregator | TLC counterexample, Apalache pass: `VariantFilter` of a set with a `FALSE` payload and another tag leaves no initial state, `41ca4840` | [`apalache-bmc-022`](../findings/apalache-bmc/apalache-bmc-022.md) |
| 1 | aggregator | TLC pass, Apalache counterexample: `SeqMod(_, 0)` on the left of `~>` in the initial state exits 0, `c7308e5a` | [`tlc-008`](../findings/TLC/tlc-008.md) |

All 77 aggregator deviations are pass/counterexample pairs. Each was rerun
three times: as recorded, with `ApaFoldSet` applying the combinator in reverse
order, and with the `SetToSeq` alias replaced by `Reverse(SetToSeq(...))`, as
for corpus43. The recorded TLC verdict reproduced for 76 entries. The
exception is `c7308e5a`, whose rerun prints the modulo error that the recorded
pass hides, which is the `tlc-008` defect. A reversal moves 66 entries to
Apalache's verdict, where TLC's `The invariant of Inv is equal to FALSE` and
`The property of Prop is equal to FALSE` count as counterexamples. In 3 more
entries, a reversal reaches an evaluation error at the operation named in
the table. For these entries, Apalache's order reaches the same undefined
expression. The three `CHOOSE` rows were classified by inspection: in
`b5623fe7`, the predicate of `CHOOSE` is the state variable `var0`, and in the
other two `Inverse(f, S, T)` is applied where `f` is not injective or misses
`T`, so every choice satisfies the predicate of the `CHOOSE` in its definition.

The two new findings were reduced by hand. `ExistsSurjection({1, 2, 3}, {})`
is `FALSE` in TLC and `TRUE` in Apalache, which rewires it as
`Cardinality(S) >= Cardinality(T)`. In `41ca4840`, `Init` contains
`var0 \in VariantFilter("Tag3", {Variant("Tag3", FALSE), Variant("Tag2", ...), ...})`
and `var1 = FALSE`, with `Inv == var1`. Apalache's filter encoding asserts both
that the shared `FALSE` cell is in the filtered set and that it is not, so
`Init` has no model and the `--no-deadlock` run passes.

Both Apalache crashes also run out of heap memory in a fresh JVM with
`-Xmx1g` (`041ad8a7` after 169 seconds, `ed052a2b` after 320 seconds in state
3), so the long-lived worker does not cause them. Neither applies
`LongestCommonPrefix`, so they are not the
[`apalache-performance-001`](../findings/apalache-performance/apalache-performance-001.md)
reduction, and they may belong to its 34 unreduced corpus43 entries. In
`041ad8a7`, `Init` alone runs out of memory at `--length=0`. Replacing
subterms of the IR places the cost in a nest of four folds (`ApaFoldSet` and
`ApaFoldSeqLeft`) over sets of functions with set and sequence domains, with a
`Tag18(<<Int -> Int>>)` accumulator. Without the enclosing `Inverse` and
outer fold, the nest checks in about 300 seconds. Without the nest, `Init`
checks in 9 seconds. Replacing the innermost `ReplaceSubSeqAt` call in `Init`
by its literal argument still runs out of memory. Hand-written constant versions of the nest check in seconds, so no
reproduction exists yet. `ed052a2b` was not reduced.

The triager classifies by the failing checker's diagnostic, and the
pass/counterexample pairs have none, so it still leaves the 77 aggregator
deviations as `NEW`, as well as the 10 TLC crashes (the `tlc-013` labels) and
the 2 Apalache out-of-memory exits. `all-defects.toml` now has the signatures
`community-exists-surjection` and `variant-filter`, which quarantine future
candidates that apply `ExistsSurjection` or `VariantFilter`.

## corpus46 residuals

corpus46 repeats the corpus44 configuration unchanged.
`00-known-defects` quarantined 3,671 candidates, and none of them records
`community-exists-surjection` or `variant-filter`. Since 12 of the `NEW`
aggregator entries apply `ExistsSurjection` and 10 apply `VariantFilter`, the run
most likely read a signature database older than `eda9f1a`. Triage left 8 of the
154 TLC crashes, 5 of the 131 Apalache crash outcomes and 87 of the 39,639
aggregator deviations as `NEW`. The parser report was empty. The other Apalache
crash outcomes matched the catalog or were worker timeouts (69). No entry needs
a new finding or conformance report.

Every `NEW` entry was rendered with FuzzTLA `f08b942` plus the working-tree
changes later committed as `e2fc51b`. The aggregator entries were rerun with TLC
commit `142d0ba` (tla2tools `1.8.0-20260917.033119-76`) and
`CommunityModules.jar` (commit `9aae8ea`) as for corpus44. Apalache 0.62.2 (build
`f0dec98`) was rerun on the stack overflow and on the `CHOOSE` entry
`a6f3fea4`.

| Count | Stage | Cause | Class |
|---:|---|---|---|
| 8 | TLC crash | a label directly on `[][A]_v`: `must be of forms` | [`tlc-013`](../findings/TLC/tlc-013.md) |
| 1 | Apalache crash | `StackOverflowError` in `BoundedChecker` under `ConstSimplifierForSmt`, `fd8ffc38` | [`apalache-builder-001`](../findings/apalache-builder/apalache-builder-001.md) |
| 4 | Apalache crash | `OutOfMemoryError: Java heap space` at 1 GB, no stack trace, `4342c4b0`, `4bf3be79`, `8cf6140f` and `ad7f8560` | not reduced, see below |
| 24 | aggregator | TLC pass and Apalache counterexample (18), or the reverse (6, each a `~>` property): order-sensitive `ApaFoldSet` | [order-sensitive-set-fold](order-sensitive-set-fold.md) |
| 46 | aggregator | TLC pass and Apalache counterexample (36), or the reverse (10): the order of `SetToSeq` | [order-sensitive-set-fold](order-sensitive-set-fold.md) |
| 4 | aggregator | the other order reaches an undefined expression: `Head(<<>>)` through `ApaFoldSet` in `c9f4326c`, a `CHOOSE` without a witness through `SetToSeq` in `76094df9`, a function applied outside its domain through `SetToSeq` in `aa6beb02` and `fe0bac6c` | [order-sensitive-set-fold](order-sensitive-set-fold.md) |
| 1 | aggregator | TLC counterexample, Apalache pass: `SetToSeq({step, 1, step})` fails at `step = 2` in TLC's order and at `step = 0` in the reverse order, `128e5fba` | [order-sensitive-set-fold](order-sensitive-set-fold.md) |
| 4 | aggregator | TLC pass, Apalache counterexample: a `CHOOSE` with several witnesses, `0e9a5fe1`, `310444ca`, `a6f3fea4` and `edd23c19` | [choose-multiple-witnesses](choose-multiple-witnesses.md) |
| 3 | aggregator | TLC pass, Apalache counterexample: `Inverse` of the constant function `[x \in {TRUE, FALSE} \|-> FALSE]`, `3dba77b2`, `83040138` and `f3f1facc` | [choose-multiple-witnesses](choose-multiple-witnesses.md) |
| 5 | aggregator | TLC counterexample, Apalache pass: `ExistsSurjection(S, var0)` with `var0 = {}` in the TLC trace, `2a825460`, `60218500`, `7433d6e0`, `aabfade6` and `b5470c03` | [`apalache-rewiring-002`](../findings/apalache-rewiring/apalache-rewiring-002.md) |

All 87 aggregator deviations are pass/counterexample pairs: 63 TLC passes with
an Apalache counterexample and 24 the other way round. Each was rerun three
times, as for corpus44: as recorded, with `ApaFoldSet` applying the combinator
in reverse order, and with `Reverse(SetToSeq(...))`. The recorded TLC verdict
reproduced for all 87. A reversal moves 70 entries to Apalache's verdict. TLC's
`The invariant of Inv is equal to FALSE`, `The property of Prop is equal to
FALSE` and `Temporal property Prop was violated` count as counterexamples. For 4
more entries, a reversal reaches the evaluation error named in the table.

In `128e5fba`, neither order agrees with Apalache, which keeps the written
element order: `<<0, 1>>` at `step = 0` and `<<2, 1>>` at `step = 2`. Both
orders satisfy `IsPrefix(RemoveAt(SetToSeq(...), 1), <<1, 2, 3>>)`. The seven
`CHOOSE` rows were classified by inspection. In `0e9a5fe1` and `310444ca`, the
predicate of the `CHOOSE` is the state variable `var0`. In `a6f3fea4`, the
initial-state `Prop` is
`(IF CHOOSE b \in {FALSE, var0, FALSE}: var0 THEN step ELSE 1) > step`, and
Apalache's rerun violates it in its liveness encoding, not in `Inv`. In
`edd23c19`, both `<<TRUE>>` and `<<FALSE>>` satisfy `IsStrictPrefix(<<>>, b)`.
For the three `Inverse` rows, every element of the domain satisfies the
`CHOOSE` in the definition of `Inverse`.

In a fresh JVM, `fd8ffc38` overflows the default 1 MB thread stack and exits
255, while with `-Xss8m` Apalache reports an invariant violation in state 0. The
trace is cut at 1,024 frames. Below `ConstSimplifierForSmt.simplifyShallow`,
which is mapped over the builder `State`, it contains only the
`IndexedStateT.apply` / `Id.bind` chain of `apalache-builder-001`, with no
`typecomp` frame. The triager's `apalache-builder-001` signature now also
accepts a trace that reaches `scalaz.IndexedStateT` frames. The only earlier
crash traces with such frames, corpus40's `16cdb28d` and `2f30e2a6`, are
already `apalache-builder-001`.

The four out-of-memory crashes are dominated by `SequencesExt` slicing, like the
unreduced entries of
[`apalache-performance-001`](../findings/apalache-performance/apalache-performance-001.md).
`4342c4b0` computes `CommonPrefixes(SubSeqs(ReplaceSubSeqAt(...)))` in `Init`, and
`8cf6140f` folds `CommonPrefixes(CommonPrefixes(_))` over a set with `ApaFoldSet`.
`4bf3be79` applies `Inverse`, `Restrict` and `BagRemove`. `ad7f8560` applies
`ReplaceSubSeqAt`, `ReplaceAt`, `Remove` and `FlattenSeq`. None was rerun or
reduced.

## corpus47 residuals

corpus47 repeats the corpus44 configuration and was generated on another
machine. `00-known-defects` quarantined 3,669 candidates, and none of them
records `community-exists-surjection` or `variant-filter`. Since 9 of the `NEW`
aggregator entries apply `ExistsSurjection` and 5 apply `VariantFilter`, the run,
like corpus46, most likely read a signature database older than `eda9f1a`.
Triage left 16 of the 205 TLC crashes, 4 of the 126 Apalache crash outcomes and
110 of the 40,213 aggregator deviations as `NEW`. The parser report was empty.
The other Apalache crash outcomes matched the catalog or were worker timeouts
(64). One entry needs a new conformance report,
[set-map guard over duplicate elements](set-map-product-guard.md).

Every `NEW` entry was rendered with FuzzTLA `d943ec2` plus working-tree changes.
The aggregator entries were rerun with TLC commit `142d0ba` (tla2tools
`1.8.0-20260917.033119-76`) and `CommunityModules.jar` (commit `9aae8ea`), as
for corpus44. Apalache 0.62.2 (build `f0dec98`) was rerun on the reductions of
`f4eead9b`.

| Count | Stage | Cause | Class |
|---:|---|---|---|
| 16 | TLC crash | a label directly on `[][A]_v`: `must be of forms` | [`tlc-013`](../findings/TLC/tlc-013.md) |
| 1 | Apalache crash | `Too many elements to enumerate: 2519424` for a set map that a fold applies to its accumulator, `f4eead9b` | [set-map-product-guard](set-map-product-guard.md) |
| 3 | Apalache crash | `OutOfMemoryError: Java heap space` at 1 GB, no stack trace, `696bf86c`, `717170d2` and `b31946ad` | not reduced |
| 75 | aggregator | TLC pass and Apalache counterexample (68), or the reverse (7): the order of `SetToSeq` | [order-sensitive-set-fold](order-sensitive-set-fold.md) |
| 20 | aggregator | TLC pass and Apalache counterexample (18), or the reverse (2): order-sensitive `ApaFoldSet` | [order-sensitive-set-fold](order-sensitive-set-fold.md) |
| 4 | aggregator | the other order reaches an undefined expression: a `CHOOSE` without a witness through `SetToSeq` in `31e2d52b` and `8048ff34` and through `ApaFoldSet` in `144ece6d`, a function applied outside its domain through `SetToSeq` in `a79961e4` | [order-sensitive-set-fold](order-sensitive-set-fold.md) |
| 4 | aggregator | TLC pass, Apalache counterexample: a `CHOOSE` with several witnesses, `4319c0a6` and `9cef3979`, and through `Inverse` in `bf743091` and `f5ba4213` | [choose-multiple-witnesses](choose-multiple-witnesses.md) |
| 4 | aggregator | `ExistsSurjection(S, {})` with `S # {}`: TLC counterexample and Apalache pass in `3ec3dda3`, `4b2f478b` and `770821e1`; the reverse under `=> var1` with `var1 = FALSE` in `1cdbe12c` | [`apalache-rewiring-002`](../findings/apalache-rewiring/apalache-rewiring-002.md) |
| 3 | aggregator | TLC pass, Apalache counterexample: an evaluation error in the initial state exits 0, under `[](P) => FALSE` in `0f2f227d` and `21ce5187` and in `P ~> FALSE` in `a4f0a4e1` | [`tlc-008`](../findings/TLC/tlc-008.md) |

All 110 aggregator deviations are pass/counterexample pairs: 97 TLC passes
with an Apalache counterexample and 13 the other way round. Each was rerun
three times, as for corpus44: as recorded, with `ApaFoldSet` applying the
combinator in reverse order, and with `Reverse(SetToSeq(...))`. The recorded
TLC verdict reproduced for 107 entries. The exceptions are the three `tlc-008`
entries, whose reruns print the evaluation error that the recorded pass hides;
`TLC.process()` returns 0 for each of them. TLC `142d0ba` still has the defect,
which [tlaplus/tlaplus#1440](https://github.com/tlaplus/tlaplus/pull/1440)
fixes. A reversal moves 95 entries to
Apalache's verdict. For 4 more entries, a reversal reaches the evaluation error
named in the table. Three of the TLC counterexamples, `30eb94e1`, `4b2f478b`
and `f9c6167b`, violate `Prop`, not `Inv`, and reproduce only with
`PROPERTY Prop` in the configuration.

The `CHOOSE` rows were classified by rerunning or by inspection. In `4319c0a6`,
a conjunct of the next-state action is a `CHOOSE` over `{TRUE, FALSE}` whose
predicate reduces to `Front(<<TRUE, FALSE>>)[1]`, which holds for both values. In `9cef3979`, the invariant is
`(CHOOSE b \in ToSet(<<TRUE, FALSE>>): Head(<<TRUE, FALSE>>)) <=> FALSE`.
TLC chooses `FALSE` in both. Replacing the conjunct by `TRUE` in `4319c0a6`, and
restricting the bound set to `{TRUE}` in `9cef3979`, moves TLC to Apalache's
counterexample. `bf743091` and `f5ba4213` apply `Inverse` to a constant
function, so every element of the domain satisfies the `CHOOSE` in its
definition.

`f4eead9b` is the first Apalache crash with `Too many elements to enumerate`.
Its next-state action quantifies over an `ApaFoldSet` whose combinator maps the
accumulator with five more bound variables, 108 combinations per step. Apalache
counts the mapped accumulator by its tuples, 2, 216 and 23,328, and refuses the
third step at 23,328 × 108 = 2,519,424. The accumulator stays equal to its
two-element initial value, and TLC, rerun on the entry, reports no error. The
reduction and the guard are described in
[set-map-product-guard](set-map-product-guard.md). The new `all-defects.toml`
signature `set-map-duplicate-product` matches this shape. It also matches
`68056c1e`, an Apalache worker timeout.

The three out-of-memory crashes were not rerun or reduced. The triager still
leaves the pass/counterexample pairs, the `tlc-013` crashes and the
out-of-memory exits as `NEW`.

## Auditing the classified entries

corpus14's 62,478 classified deviations were audited two ways. Every row
re-derives from its stored envelope, so the CSV matches the catalog it claims.
89 entries sampled across all 24 TLC-failing classes were re-run, and each
reproduced its stored diagnostic exactly, so the recorded failures are real and
the stored detail is a faithful root-cause line rather than a wrapper.

The classification itself needed one fix. An aggregator entry stores one line,
TLC line-wraps its messages, and several distinct messages share a first line,
so a first-line signature can merge classes silently. Extracting TLC's message
constants from `tla2tools` shows two such prefixes among the signatures:

| Stored first line | TLC messages sharing it |
|---|---|
| `Attempted to compute the value of an expression of[ form]` | no-witness `CHOOSE`, non-enumerable `CHOOSE` bound, N-tuples `CHOOSE`, `SUBSET` of a non-enumerable set |
| `In applying the function` | argument not in the domain, argument does not match the formal parameter (three arities) |

The first was realized. TLC wraps the non-enumerable-bound variant one word
earlier, so `...of an expression of` without `form` is always
[`CHOOSE` over `Int` or `Nat`](choose-over-infinite-set.md) and never a missing
witness. 69 corpus14 entries had been filed as
[`CHOOSE` without a witness](choose-without-witness.md); the signature now
separates them, and 20 re-runs confirmed the split. The `...of form` spelling
stays ambiguous in principle, but 40 sampled entries were all the no-witness
`CHOOSE`.

The second is latent: 40 sampled `In applying the function` entries were all the
domain variant.

The two crash reports were audited the same way and needed no change. Every row
of `02apa-crash-triage.csv` (1,524) and `02tlc-crash-triage.csv` (163)
re-derives from its stored stacktrace, no entry matches more than one finding
signature, and neither report contains a `NEW`. 19 Apalache crashes sampled
across all seven classes were re-run and reproduced their diagnosis and their
classification. Crash signatures do not suffer the aggregator's one-line limit:
they match the whole stored diagnostic, so a shared message prefix cannot merge
classes there.

Two crash classes are deliberate groupings rather than single causes --
[`apalache-cli-001`](../findings/apalache-cli/apalache-cli-001.md) and
[`tlc-002`](../findings/TLC/tlc-002.md) each collect the diagnoses that exit
under a generic status, which is the defect they record. corpus14's instances
are added to both tables.

956 of the 1,524 Apalache crash outcomes (62.7%) are worker timeouts at the
30-second stage limit rather than tool diagnoses. They carry no Apalache output
beyond JVM warnings, none matches a signature, and the `TIMEOUT` bucket keeps
them out of `NEW`. They establish no conformance difference, but they are the
majority of this stage's budget.

## Reproducing a corpus entry

`fuzztla print` renders an entry with the generator settings it is given, so
`--corpus <dir>` is required to reproduce what the workflow checked. Without it
the defaults apply and an entry that declares action operators prints a module
missing them, and since integer literals decode around the corpus's
`integer_base`, an entry can print different constants. Either parses and
checks but is not the input the corpus recorded.
Three corpus14 entries were first mis-triaged that way: the truncated modules
made two deviations disappear and changed the diagnostic of a third.
