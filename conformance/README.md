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
`Aggregator (corpus4)` use corpus4's 4,282 aggregator deviations. Percentages
are rounded to two decimal places, so table rows may not sum exactly to 100%.

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
| Aggregator | 0.06% | 🔴 Fail | 🟢 Pass | Infinite set as state value | [MWE](infinite-set-as-state-value.md#representative-mwe) | TLC representation limit |
| Aggregator | 0.03% | 🔴 Fail | 🟢 Pass | Union containing an infinite set | [MWE](union-containing-infinite-set.md#representative-mwe) | TLC enumeration limit |
| Aggregator | 0.03% | 🔴 Fail | 🟢 Pass | Filtering `Nat` | [MWE](filter-over-infinite-set.md#representative-mwe) | TLC enumeration limit |
| Aggregator (corpus4) | 0.02% | 🔴 Fail | 🟢 Pass | Function over an infinite domain | [MWE](function-over-infinite-domain.md#representative-mwe) | TLC representation limit |
| Aggregator (corpus4) | 0.02% | 🔴 Fail | 🟢 Pass | Finite set containing `Nat` | [MWE](finite-set-containing-infinite-set.md#representative-mwe) | TLC representation limit |
| Aggregator | 0.09% | 🔴 Fail | 🟢 Pass | `LET` operand grouping | [MWE](let-operand-grouping.md#representative-mwe) | [Printer defect](../findings/apalache-printer/apalache-printer-007.md) |
| Aggregator | 4.04% | 🟢 Pass | 🔴 Fail | Apalache reaches modulo by zero | [MWE](modulo-by-zero-apalache-fails.md#representative-mwe) | Evaluation order |
| Aggregator | 4.01% | 🟢 Pass | 🔴 Fail | Apalache reaches division by zero | [MWE](division-by-zero-apalache-fails.md#representative-mwe) | Evaluation order |
| Aggregator | 1.92% | 🟢 Pass | 🔴 Fail | Unsupported `Seq(S)` | [MWE](sequence-set-unsupported.md#representative-mwe) | Known Apalache limitation |
| Aggregator | 1.15% | 🟢 Pass | 🔴 Fail | Apalache reaches `0^0` | [MWE](zero-power-zero-apalache-fails.md#representative-mwe) | Evaluation order |
| Aggregator | 1.00% | 🟢 Pass | 🔴 Fail | Unsupported `STRING` | [MWE](string-set-unsupported.md#representative-mwe) | Apalache capability limit |
| Aggregator | 0.06% | 🟢 Pass | Counterexample | Empty-domain function set | [MWE](empty-function-set.md#representative-mwe) | [Soundness defect](../findings/apalache-bmc/apalache-bmc-002.md) |
| Apalache crash | 0.16% | Supported | Input error | Nonconstant integer range | [MWE](nonconstant-integer-range.md#representative-mwe) | Known Apalache limitation |
| Apalache crash | 1.05% | Varies | Crash | Symbolic-set filtering | [MWE](set-filter-symbolic-set.md#representative-mwe) | [Unhandled defect](../findings/apalache-bmc/apalache-bmc-001.md) |
| Apalache crash | 0.16% | Varies | Crash | Symbolic-set equality | [MWE](symbolic-set-equality.md#representative-mwe) | [Unhandled defect](../findings/apalache-bmc/apalache-bmc-003.md) |
| Apalache crash | 0.05% | Varies | Guard | Function-set expansion | [MWE](function-set-expansion-guard.md#representative-mwe) | Intentional resource guard |

Resource-only timeouts and heap exhaustion are excluded because they do not
establish a semantic conformance difference. Known checker issues not observed
in the analyzed PBT session are also excluded.
