# corpus87: a clean PBT campaign against the TLC of February 2026

This report records what corpus87 found. corpus87 is a long `pbt` campaign that ran
TLC `7e64fd0` (2026-02-23) against Apalache 0.63.0. It answers three questions:

1. Which known issues did the campaign reproduce?
2. Which issues are previously unknown?
3. Do those issues still reproduce on the current TLC, `bc1b506` (2026-11-15)?

"Known" means recorded in [`findings/`](../../findings/) or
[`conformance/`](../../conformance/) at FuzzTLA `a410726a`: 82 findings (59 open,
23 closed) and 53 conformance reports.

## Summary

- **Known issues.** The campaign reproduced 30 of the 82 findings and 38 of the
  53 conformance reports. On the TLC side (TLC, TLC performance and SANY findings),
  it reproduced 13 of 28 findings, two of them closed: `tlc-008` and `tlc-014`.
- **Previously unknown issues.** Three TLC issues are not recorded in this
  repository. They account for 3,004 entries, all rejections of a valid
  `PROPERTY`:
  - a Boolean state variable as the property;
  - an operator argument as the property;
  - a `LET` around `[][A]_v`.
- **Current TLC.** None of the three reproduces on `bc1b506`. Upstream fixed them
  in commits `72a0c291`, `9b5afe73` and `0727a074`, all of which descend
  from `7e64fd0`. The first two resolve upstream issues
  [#715](https://github.com/tlaplus/tlaplus/issues/715) and
  [#1244](https://github.com/tlaplus/tlaplus/issues/1244).
- **Still to resolve.** One SANY parse error reproduces on both SANY versions
  and is not reduced; see [§2.3](#23-unresolved).
- **Undocumented TLC behaviour that persists.** The following still reproduces
  on `bc1b506` but has no document of its own:
  - TLC fails on `ENABLED` over a primed expression that reads a variable no
    conjunct assigns (440 entries). The triager attributes these to the closed
    `apalache-printer-008`.
  - Stack overflows on deep expressions (18 entries) do not reproduce in a rerun.

## Setup

| Item | Value |
|---|---|
| Technique, kind | `pbt`, `module` |
| Checkers | `tlc` (1 worker) and `apalache`; no `tlcn` |
| Known-defect quarantine | none (`known_defects = []`): every shape reached the checkers |
| Generator | `max_steps = 5`, `max_variables = 3`, `integer_literals = "boundary"`, temporal forms weighted ×16 |
| TLC in the corpus | `tools/tla2tools.jar`, tlaplus/tlaplus `7e64fd0` (2026-02-23), built 2026-11-14 |
| Reference TLC | tlaplus/tlaplus `bc1b506` (tla2tools `1.8.0-20261115.003216-126`), 296 commits later |
| Apalache | `tools/apalache.jar`, Apalache 0.63.0 release |
| FuzzTLA | `a410726a`, used to render, match and triage the entries; the corpus does not record the commit that ran it |

`7e64fd0` is the earliest TLC that FuzzTLA's workers accept
([Earlier checker versions](../manual/checker-versions.md)).

| Directory | Entries | Classified by the triager | `NEW` after triage |
|---|---:|---:|---:|
| `01parser-fail` | 211 | not read | — |
| `02tlc-crash` | 24,580 | 6,131 (3 timeouts) | 18,449 |
| `02tlc-fail` | 119,452 | 118,614 | 838 |
| `02apa-crash` | 140,966 | 140,966 (119,675 timeouts) | 0 |
| `02apa-fail` | 7,000 | 7,000 | 0 |
| `03aggregator-fail` | 216,372 | 212,232 | 4,140 |

The counts use the triager after this change, which also matches a `STRING`
state value and a truncated set-equality diagnostic; see [Method](#method). The
other directories hold 147,916 entries, which are passes, counterexamples
awaiting the other checker, and quality-gate results.

## 1. Known issues reproduced

### 1.1. TLC findings

| Finding | State | Entries | Stage | Attribution | On `bc1b506` |
|---|---|---:|---|---|---|
| [`tlc-009`](../../findings/TLC/tlc-009.md) | open | 4,689 | TLC crash | triager | not rerun |
| [`tlc-010`](../../findings/TLC/tlc-010.md) | open | 781 | TLC crash | triager | not rerun |
| [`tlc-002`](../../findings/TLC/tlc-002.md) | open | 646 | TLC crash | triager | not rerun |
| [`tlc-012`](../../findings/TLC/tlc-012.md) | open | 21 + 3 | TLC fail, aggregator | triager | not rerun |
| [`tlc-003`](../../findings/TLC/tlc-003.md) | open | 4 | TLC crash | triager | not rerun |
| [`tlc-001`](../../findings/TLC/tlc-001.md) | open | 1 | TLC crash | triager | not rerun |
| [`tlc-performance-002`](../../findings/tlc-performance/tlc-performance-002.md) | open | 4 | TLC crash | triager | not rerun |
| [`tlc-performance-001`](../../findings/tlc-performance/tlc-performance-001.md) | open | 3 | TLC crash | triager | not rerun |
| [`tlc-008`](../../findings/TLC/tlc-008.md) | closed | 1,734 | aggregator | all rerun | fixed |
| [`tlc-013`](../../findings/TLC/tlc-013.md) | open | 57 | TLC crash | `Prop` shape; 20 rerun | reproduces |
| [`tlc-015`](../../findings/TLC/tlc-015.md) | open | 10 + 4 | TLC fail, aggregator | all rerun | reproduces |
| [`tlc-014`](../../findings/TLC/tlc-014.md) | closed | 10 | aggregator | all rerun | fixed |
| [`tlc-018`](../../findings/TLC/tlc-018.md) | open | 1 | TLC crash | plausible, not reproduced | — |

- **`tlc-008`.** It is the largest class that the triager cannot see. TLC
  records a pass, while Apalache reports a counterexample. Old TLC prints an
  evaluation error raised under `<>` in the initial state and then exits 0, so
  FuzzTLA stores a pass. On `bc1b506`, 1,729 of these entries exit 255, as in
  [`tlc-009`](../../findings/TLC/tlc-009.md), and 4 exit 151. One entry,
  `879c317e`, reports the initial-state invariant violation that the exit status
  of `7e64fd0` hid.
- **`tlc-014`.** Old TLC reports a temporal violation that neither `bc1b506` nor
  Apalache reports. Each of the 10 properties negates a temporal subformula under
  another temporal operator or `=>`. Examples are
  `<>(<>(~(<>([]P)))) ~> Q` (`ac51bcde`) and `[](<>FALSE) => var1`
  (`71e61e0c`). Expanding `~>` or `=>` yields the `~~F` or `~<>~` shapes of the
  finding. The finding's MWE also fails on `7e64fd0` and passes on `bc1b506`.
- **`tlc-013`.** In these 57 crashes, `Prop` puts a label directly on `[][A]_v`.
  All 20 entries rerun from this class report "must be of forms" on both
  versions.
- **`tlc-015`.** In all 14 entries, a fold whose combinator multiplies `step`
  occurs under `<>` or `~>`. Each reports `step` as undefined and exits 151 on
  both versions.
- **`tlc-018`.** The worker of `30753804` ran out of heap. A rerun with
  `-Xmx512m` reports a function-domain error instead.

### 1.2. Other known documents

The triager attributes entries to 58 documents. Shapes and reruns add 11 more:
- the 6 TLC findings of §1.1 that the triager does not label;
- `apalache-bmc-019`, `apalache-bmc-021` and `apalache-printer-009`;
- the conformance reports tlc-temporal-formula-limits, order-sensitive-set-fold
  and choose-multiple-witnesses.

The triager table also lists `apalache-printer-008`, which is closed. Its 440
entries are not that printer defect; see [§2.2](#22-known-but-undocumented-tlc-behaviour).
Excluding it gives 30 findings and 38 conformance reports.

The classes outside the triager:

| Count | Stage | Cause | Class |
|---:|---|---|---|
| 18,388 | TLC crash | "must be of forms" or "cannot handle"; every one matches a TLC temporal signature of `all-defects.toml`. 48 sampled across the signatures, and the 15 `LET`-wrapped ones among them, fail the same way on both versions | [tlc-temporal-formula-limits](../../conformance/tlc-temporal-formula-limits.md); [`tlc-011`](../../findings/TLC/tlc-011.md) and [`tlc-023`](../../findings/TLC/tlc-023.md) print the same diagnostics and are not separated |
| 112 | aggregator | TLC counterexample on both versions, Apalache pass, `ENABLED` in `Inv` | [`apalache-bmc-019`](../../findings/apalache-bmc/apalache-bmc-019.md) |
| 18 | aggregator | TLC counterexample on both versions, Apalache pass, a fold in the module. `e17f8b71` reduces to `x \in ApaFoldSet(L, {}, S)` with `L` returning `[S -> BOOLEAN]`, and Apalache 0.63.0 passes `Inv == FALSE` | [`apalache-bmc-021`](../../findings/apalache-bmc/apalache-bmc-021.md) (`e17f8b71`); [order-sensitive-set-fold](../../conformance/order-sensitive-set-fold.md) (`a937c808`, `ff8e807d`, `f79ab833`); the rest by shape |
| 70 + 1 | aggregator | TLC pass on both versions, Apalache counterexample; an `ApaFoldSet` whose combinator returns its element, or a `CHOOSE` with several witnesses (`f06acadb`) | [order-sensitive-set-fold](../../conformance/order-sensitive-set-fold.md), [choose-multiple-witnesses](../../conformance/choose-multiple-witnesses.md) |
| 196 + 4 | parser fail | `Label ... must contain formal parameter`; 196 match the `printer-nested-case-arm` signature | [`apalache-printer-009`](../../findings/apalache-printer/apalache-printer-009.md) |
| 10 | parser fail | `Level error in applying operator ApaFoldSet` or `ApaFoldSeqLeft` | FuzzTLA generator, as in the corpus85 triage |

The Apalache rows are classified by the shape of the specification and were not
rerun on Apalache, except `e17f8b71`. The Apalache version is the one current at
`a410726a`, so the TLC version question does not apply to them.

The full triager attribution, per document and stage:

| Document | Kind | State | TLC crash | TLC fail | Apalache crash | Apalache fail | Aggregator |
|---|---|---|---:|---:|---:|---:|---:|
| [`apalache-temporal-001`](../../findings/apalache-temporal/apalache-temporal-001.md) | finding | open |  |  | 17,643 |  |  |
| [`tlc-009`](../../findings/TLC/tlc-009.md) | finding | open | 4,689 |  |  |  |  |
| [`apalache-cli-001`](../../findings/apalache-cli/apalache-cli-001.md) | finding | open |  |  | 1,504 |  |  |
| [`apalache-bmc-001`](../../findings/apalache-bmc/apalache-bmc-001.md) | finding | open |  |  | 952 |  |  |
| [`tlc-010`](../../findings/TLC/tlc-010.md) | finding | open | 781 |  |  |  |  |
| [`tlc-002`](../../findings/TLC/tlc-002.md) | finding | open | 646 |  |  |  |  |
| [`apalache-printer-008`](../../findings/apalache-printer/apalache-printer-008.md) | finding | closed |  | 366 |  |  | 74 |
| [`apalache-bmc-004`](../../findings/apalache-bmc/apalache-bmc-004.md) | finding | open |  |  | 305 |  |  |
| [`apalache-assignments-001`](../../findings/apalache-assignments/apalache-assignments-001.md) | finding | open |  |  | 194 |  |  |
| [`apalache-bmc-003`](../../findings/apalache-bmc/apalache-bmc-003.md) | finding | open |  |  | 189 |  |  |
| [`apalache-bmc-005`](../../findings/apalache-bmc/apalache-bmc-005.md) | finding | open |  |  | 144 |  |  |
| [`apalache-bmc-006`](../../findings/apalache-bmc/apalache-bmc-006.md) | finding | open |  |  | 131 |  |  |
| [`apalache-temporal-002`](../../findings/apalache-temporal/apalache-temporal-002.md) | finding | open |  |  | 119 |  |  |
| [`apalache-temporal-007`](../../findings/apalache-temporal/apalache-temporal-007.md) | finding | open |  |  | 43 |  |  |
| [`apalache-temporal-004`](../../findings/apalache-temporal/apalache-temporal-004.md) | finding | open |  |  | 35 |  |  |
| [`tlc-012`](../../findings/TLC/tlc-012.md) | finding | open |  | 21 |  |  | 3 |
| [`apalache-temporal-003`](../../findings/apalache-temporal/apalache-temporal-003.md) | finding | open |  |  | 19 |  |  |
| [`apalache-bmc-012`](../../findings/apalache-bmc/apalache-bmc-012.md) | finding | open |  |  | 13 |  |  |
| [`tlc-003`](../../findings/TLC/tlc-003.md) | finding | open | 4 |  |  |  |  |
| [`tlc-performance-002`](../../findings/tlc-performance/tlc-performance-002.md) | finding | open | 4 |  |  |  |  |
| [`tlc-performance-001`](../../findings/tlc-performance/tlc-performance-001.md) | finding | open | 3 |  |  |  |  |
| [`apalache-bmc-007`](../../findings/apalache-bmc/apalache-bmc-007.md) | finding | open |  | 1 |  |  | 2 |
| [`tlc-001`](../../findings/TLC/tlc-001.md) | finding | open | 1 |  |  |  |  |
| [function-application-outside-domain](../../conformance/function-application-outside-domain.md) | conformance | — |  | 39,960 |  |  | 55,247 |
| [choose-without-witness](../../conformance/choose-without-witness.md) | conformance | — |  | 27,193 |  |  | 39,884 |
| [constant-false-invariant](../../conformance/constant-false-invariant.md) | conformance | — |  | 7,443 |  |  | 51,589 |
| [case-without-matching-arm](../../conformance/case-without-matching-arm.md) | conformance | — |  | 16,490 |  |  | 33,262 |
| [integer-outside-tlc-range](../../conformance/integer-outside-tlc-range.md) | conformance | — |  | 16,248 |  |  | 3,685 |
| [enabled-apalache-unsupported](../../conformance/enabled-apalache-unsupported.md) | conformance | — |  |  |  | 1,555 | 8,663 |
| [head-of-empty-sequence](../../conformance/head-of-empty-sequence.md) | conformance | — |  | 4,738 |  |  | 4,697 |
| [constant-property-tlc-rejects](../../conformance/constant-property-tlc-rejects.md) | conformance | — |  | 1,267 |  |  | 6,682 |
| [fairness-apalache-unsupported](../../conformance/fairness-apalache-unsupported.md) | conformance | — |  |  |  | 5,161 | 2,266 |
| [subseq-outside-domain](../../conformance/subseq-outside-domain.md) | conformance | — |  | 1,007 |  |  | 1,668 |
| [tail-of-empty-sequence](../../conformance/tail-of-empty-sequence.md) | conformance | — |  | 921 |  |  | 909 |
| [modulo-nonpositive-divisor](../../conformance/modulo-nonpositive-divisor.md) | conformance | — |  | 435 |  |  | 779 |
| [division-by-zero-tlc-fails](../../conformance/division-by-zero-tlc-fails.md) | conformance | — |  | 348 |  |  | 724 |
| [non-enumerable-initial-assignment](../../conformance/non-enumerable-initial-assignment.md) | conformance | — |  | 475 |  |  | 465 |
| [choose-over-infinite-set](../../conformance/choose-over-infinite-set.md) | conformance | — |  | 625 |  |  | 193 |
| [zero-power-zero-tlc-fails](../../conformance/zero-power-zero-tlc-fails.md) | conformance | — |  | 235 |  |  | 385 |
| [sequence-set-unsupported](../../conformance/sequence-set-unsupported.md) | conformance | — |  |  |  | 163 | 348 |
| [quantification-over-infinite-set](../../conformance/quantification-over-infinite-set.md) | conformance | — |  | 274 |  |  | 213 |
| [string-set-unsupported](../../conformance/string-set-unsupported.md) | conformance | — |  |  |  | 108 | 199 |
| [finite-set-containing-infinite-set](../../conformance/finite-set-containing-infinite-set.md) | conformance | — |  | 213 |  |  | 64 |
| [negative-exponent-tlc-fails](../../conformance/negative-exponent-tlc-fails.md) | conformance | — |  | 53 |  |  | 39 |
| [subset-test-over-infinite-set](../../conformance/subset-test-over-infinite-set.md) | conformance | — |  | 57 |  |  | 27 |
| [infinite-set-as-state-value](../../conformance/infinite-set-as-state-value.md) | conformance | — |  | 47 |  |  | 30 |
| [cardinality-of-infinite-set](../../conformance/cardinality-of-infinite-set.md) | conformance | — |  | 57 |  |  | 16 |
| [filter-over-infinite-set](../../conformance/filter-over-infinite-set.md) | conformance | — |  | 56 |  |  | 15 |
| [union-containing-infinite-set](../../conformance/union-containing-infinite-set.md) | conformance | — |  | 33 |  |  | 31 |
| [difference-with-infinite-set](../../conformance/difference-with-infinite-set.md) | conformance | — |  | 32 |  |  | 11 |
| [modulo-by-zero-apalache-fails](../../conformance/modulo-by-zero-apalache-fails.md) | conformance | — |  |  |  | 6 | 23 |
| [division-by-zero-apalache-fails](../../conformance/division-by-zero-apalache-fails.md) | conformance | — |  |  |  | 6 | 21 |
| [function-over-infinite-domain](../../conformance/function-over-infinite-domain.md) | conformance | — |  | 8 |  |  | 15 |
| [string-set-tlc-fails](../../conformance/string-set-tlc-fails.md) | conformance | — |  | 5 |  |  | 2 |
| [cartesian-product-with-infinite-set](../../conformance/cartesian-product-with-infinite-set.md) | conformance | — |  | 4 |  |  |  |
| [function-set-over-infinite-set](../../conformance/function-set-over-infinite-set.md) | conformance | — |  | 2 |  |  |  |
| [zero-power-zero-apalache-fails](../../conformance/zero-power-zero-apalache-fails.md) | conformance | — |  |  |  | 1 |  |
| [intersection-of-infinite-sets](../../conformance/intersection-of-infinite-sets.md) | conformance | — |  |  |  |  | 1 |

### 1.3. Known TLC findings not reproduced

| Finding | Reason, where evident |
|---|---|
| [`tlc-020`](../../findings/TLC/tlc-020.md) (closed) | Needs several workers; the corpus has no `tlcn` stage |
| [`tlc-016`](../../findings/TLC/tlc-016.md), [`tlc-017`](../../findings/TLC/tlc-017.md) (closed) | Community Modules overrides; `custom_operators = []` |
| [`sany-003`](../../findings/SANY/sany-003.md) | A SANY regression of tlaplus `0a20a0b` (2026-10-02), newer than `7e64fd0` |
| [`tlc-011`](../../findings/TLC/tlc-011.md), [`tlc-023`](../../findings/TLC/tlc-023.md) | Not separated from the temporal-formula crashes, which print the same diagnostics |
| [`tlc-004`](../../findings/TLC/tlc-004.md), [`tlc-005`](../../findings/TLC/tlc-005.md), [`tlc-006`](../../findings/TLC/tlc-006.md), [`tlc-007`](../../findings/TLC/tlc-007.md), [`tlc-019`](../../findings/TLC/tlc-019.md), [`tlc-021`](../../findings/TLC/tlc-021.md), [`tlc-022`](../../findings/TLC/tlc-022.md) (closed), [`sany-001`](../../findings/SANY/sany-001.md), [`sany-002`](../../findings/SANY/sany-002.md) | No entry with their diagnostic or verdict pattern |

Every count is a lower bound for findings without a triager signature: the
triager leaves an entry `NEW` rather than guess. The verdict-pair rows,
`tlc-008` and `tlc-014`, are exact for this corpus, because every disagreement
was rerun.

## 2. Previously unknown issues

### 2.1. TLC issues fixed after `7e64fd0`

| Count | Stage | Cause | `bc1b506` | Upstream fix |
|---:|---|---|---|---|
| 817 + 2,166 | TLC fail, aggregator | `PROPERTY Prop` with `Prop == var0` for a Boolean variable: "The operator var0, which equals …, cannot be used as a property", exit 151 | checks the property | `72a0c291` "Allow variables as `PROPERTY` in Config", [#715](https://github.com/tlaplus/tlaplus/issues/715) |
| 18 | aggregator | `Prop` applies an operator whose body is its parameter: "The operator parameter3, which equals \<LAZY …\>, cannot be used as a property" | gets past `Prop` | `9b5afe73` "Handle LazyValue in SpecProcessor#processConfigSpec and processConfigProps", [#1244](https://github.com/tlaplus/tlaplus/issues/1244), [PR #1321](https://github.com/tlaplus/tlaplus/pull/1321) |
| 3 | TLC crash | `Prop == LET d == e IN [][A]_v`: "Temporal formulas containing actions must be of forms \<\>[]A or []\<\>A", exit 255 (`43a341d7`, `4eb5ab46`, `d839fe46`) | checks the property | `0727a074` "Handle LetInNode in SpecProcessor#processConfigSpec and processConfigProps", a follow-up to PR #1321 |

All three are TLC rejecting a valid property in `SpecProcessor.processConfigProps`.
They are new to this repository, not to TLC's maintainers. A minimal module for
the third:

```tla
---- MODULE FuzzInput ----
EXTENDS Integers
VARIABLE x
Init == x = 0
Next == (x < 3 /\ x' = x + 1) \/ UNCHANGED x
Spec == Init /\ [][Next]_x
Prop == LET y == 1 IN [][x' >= x]_x
====
```

With `SPECIFICATION Spec` and `PROPERTY Prop`, `7e64fd0` prints the "must be of
forms" error and exits 255. `bc1b506` reports no error. Without the `LET`, both
pass. The first class:
- was confirmed on 5 TLC fails, with `Prop == var0` or `Prop == var1`;
- was identified in the rest by the diagnostic, which only TLC's
  `TLC_CONFIG_OP_IS_EQUAL` error prints;
- reproduces on `7e64fd0` in each rerun, while `bc1b506` gets past the property.

The second class was confirmed on 4 of its 18 entries.

### 2.2. Known but undocumented TLC behaviour

| Count | Stage | Cause | `bc1b506` | Note |
|---:|---|---|---|---|
| 366 + 74 | TLC fail, aggregator | "In evaluation, the identifier step (or `varN`) is either undefined", exit 75: `ENABLED` over a primed expression that reads a variable before a conjunct assigns it, such as `ENABLED (… IF step' …)` | reproduces (20 of 20) | The corpus84 class "TLC's evaluation of `ENABLED`". The triager files it under the closed [`apalache-printer-008`](../../findings/apalache-printer/apalache-printer-008.md), whose "identifier … is either undefined" alternative matches any undefined identifier |
| 11 + 7 | TLC fail, aggregator | `StackOverflowError` in the worker | not reproduced in a rerun (2 tried) | The corpus29 deep-expression class |

The `ENABLED` behaviour is a TLC capability limit rather than a version
difference. It needs a conformance report of its own, so that the triager stops
attributing it to a closed printer defect.

### 2.3. Unresolved

| Count | Stage | Cause | `bc1b506` |
|---:|---|---|---|
| 1 | parser fail | SANY `***Parse Error*** Was expecting "Identifier or >>"` at the `<<` of `[ << [ << … >>[1] EXCEPT ![1] = FALSE ] >> EXCEPT ![1] = … ]`, under a nested `CASE` (`d701237a`) | reproduces |

The bracket does not fail on its own. Extracted into a fresh module, it parses,
including inside the nested `CASE`, the `LET` and the labels that enclose it. It
also parses when only its top-level shape is kept, as in
`[<<[<<e>>[1] EXCEPT ![1] = FALSE]>> EXCEPT ![1] = …]`. The whole one-line `Inv`
of the entry fails. Automatic reduction was not finished, so it is open whether
this is a SANY defect or a printer output that SANY rightly rejects. Since SANY
`bc1b506` rejects it too, the version question does not separate it.

## 3. Reproduction on the current TLC

None of the previously unknown TLC issues reproduces on `bc1b506`. All three were
fixed upstream within days of `7e64fd0`. The closed findings that the campaign
reproduced, `tlc-008` and `tlc-014`, are fixed on `bc1b506` as well. The open
findings that were rerun, `tlc-013` and `tlc-015`, reproduce on both versions. A
campaign against TLC `7e64fd0` therefore adds no open TLC defect.

## Method

- **Triage.** `script/triager.py` classified every crash, fail and aggregator
  entry. Two signatures were widened for this corpus, with tests in
  `script/test_triager.py`:
  - `infinite-set-as-state-value` now matches `STRING` (9 TLC fails);
  - `finite-set-containing-infinite-set` now matches a set-equality diagnostic
    that the stored detail truncates (4 TLC fails and 1 aggregator entry).
- **Rendering and matching.** All 23,441 `NEW` entries were decoded and rendered
  in one JVM per shard with FuzzTLA `a410726a`, and matched against
  `signatures/all-defects.toml`. For each crash, the head of `Prop` was extracted
  to count the label and `LET` shapes.
- **Reruns.** Each rerun used `SPECIFICATION Spec`, `INVARIANT Inv` and
  `PROPERTY Prop`, with `-workers 1 -deadlock -fp 28 -seed 2106970606645755393`
  and `-Xmx512m`, the settings of the FuzzTLA TLC worker. Each ran on
  `7e64fd0` and on `bc1b506`. The following were rerun:
  - all 1,945 verdict disagreements;
  - all 14 `tlc-015` entries;
  - all small TLC-fail groups;
  - 71 crashes sampled across the temporal signatures, plus all 18 `LET`-wrapped
    ones;
  - samples of the other classes, as stated in their rows.
- **Parser failures.** The 211 `01parser-fail` entries were checked with the
  SANY of both JARs.
- **Upstream fixes.** The fixing commits were found in the history of
  `SpecProcessor.java` between `7e64fd0` and `bc1b506`, and confirmed by the
  MWEs above.
