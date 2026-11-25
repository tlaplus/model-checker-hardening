# corpus88: a metamorphic campaign against the TLC of February 2026

This report records what corpus88 found. corpus88 is an `mt` campaign with temporal
rules ([ADR 0016](../decisions/0016-metamorphic-testing.md),
[ADR 0027](../decisions/0027-temporal-metamorphic-testing.md),
[ADR 0029](../decisions/0029-apalache-temporal-metamorphic-testing.md)). It
adopts the elite entries of [corpus87](corpus87-pbt-clean-tlc-feb2026.md) and
runs TLC `7e64fd0` (2026-02-23) against Apalache 0.63.0, the latest Apalache
release. The report groups the TLC issues into three categories:

1. previously known issues that the campaign triggered on the old TLC;
2. previously unknown issues triggered on the old TLC;
3. previously unknown issues that reproduce on the latest TLC, `bc1b506`
   (2026-11-14), still the head of tlaplus/tlaplus `master` on 2026-11-25.

"Known" means recorded in [`findings/`](../../findings/) or
[`conformance/`](../../conformance/) at FuzzTLA `fa5b6c03`: 92 findings (68 open,
24 closed) and 53 conformance reports. The issues of the corpus87 report, which
upstream had fixed and which therefore have no finding, also count as known.

## Summary

- **Known issues (category 1).** The campaign reproduced 10 TLC findings, two of
  them closed, and 28 conformance reports on the TLC side. The two closed
  findings dominate.
  - [`tlc-014`](../../findings/TLC/tlc-014.md) (4,976 entries) is a spurious
    violation of a property with a double negation under a temporal operator.
  - [`tlc-008`](../../findings/TLC/tlc-008.md) (613 entries) is an evaluation error
    in an eventuality that exits with status 0.

  Together they explain every TLC metamorphic violation: all 5,589 are
  "property verdicts differ" between `SpecE |= PropE` and `SpecC |= PropC`.
  The three issues of the corpus87 report recur as well.
  - A Boolean state variable as `PROPERTY` (1,258 entries).
  - An operator argument as `PROPERTY` (3 entries).
  - `ENABLED` over a primed variable that no assignment binds (126 entries).
- **Previously unknown issues on the old TLC (category 2).** Two issues.
  - TLC rejects a negated temporal formula that contains an action or a
    fairness condition (191 entries), with "Temporal formulas containing
    actions must be of forms <>[]A or []<>A". An example is
    `[](~~[]<><<x' >= x>>_x)`. `bc1b506` checks these properties. The fix is
    upstream commit `4260e4712`, which also fixes `tlc-014`. `tlc-014` records
    only the spurious counterexample.
  - TLC builds the tableau of a temporal property too slowly (229 entries, all
    of corpus88's TLC timeouts that stored thread stacks). It deduplicates
    particles by linear search with a quadratic set equality. Filed as
    [`tlc-performance-003`](../../findings/tlc-performance/tlc-performance-003.md):
    a property of 13 temporal operators takes minutes on a 6-state
    specification, and seconds with hashed lookups.
- **The latest TLC (category 3).** No correctness issue reproduces on `bc1b506`.
  It reports no metamorphic violation for any of the 7,769 disagreements and
  counterexamples that were rerun. `tlc-performance-003` is unchanged on
  `bc1b506`, and normalizing negations exposes it more often: 38 more property
  runs exceed 60 s, and 15 of them exceed 600 s. `7e64fd0` answered 33 of the 38
  within a second, 27 of them with `tlc-014`'s spurious violation.
- **Known issues not triggered.** Together, corpus87 and corpus88 missed 19
  documented issues that their configuration could reach, and possibly 21:
  `tlc-011` and `tlc-023` may hide among the temporal-formula crashes. 44 other
  documents need Manyan, several TLC workers, a library, or a tool version
  that the campaigns did not run.
  See [Known issues that corpus87 and corpus88 did not trigger](#known-issues-that-corpus87-and-corpus88-did-not-trigger).

## Setup

| Item | Value |
|---|---|
| Technique, kind | `mt`, `module`; `temporal_rules` with `SFDef` and `WFDef` disabled |
| Base corpus | `../corpus87`, `adopt_ratio = 0.5`: half of each generation adopts a `04quality-pass` entry of corpus87 |
| Checkers | `tlc` (1 worker) and `apalache`; no `tlcn` |
| Known-defect quarantine | none (`known_defects = []`): neither `metamorphic-defects.toml` nor `temporal-apalache-defects.toml`, so `CHOOSE`, `ENABLED` and fairness reached both checkers |
| Generator | as corpus87: `max_steps = 5`, `max_variables = 3`, `integer_literals = "boundary"`, temporal forms weighted ×16 |
| Rewriting | `max_rewrites = 16`, `max_rewrite_depth = 4`, `max_rewrite_growth = 2`, `max_rewrite_temporal_operators = 16` |
| TLC in the corpus | `tools/tla2tools.jar`, tlaplus/tlaplus `7e64fd0`, the JAR of corpus87 |
| Reference TLC | tlaplus/tlaplus `bc1b506` (tla2tools `1.8.0-20261119.181232-127`), 296 commits later |
| Apalache | `tools/apalache.jar`, Apalache 0.63.0 (`67b8ec69`), the JAR of corpus87 and the latest release |
| FuzzTLA | `fa5b6c03`, with the triager change of [Method](#method), used to render, match and triage the entries; the corpus does not record the commit that ran it |
| Run | 2026-11-21 to 2026-11-24, stopped with 333 entries waiting for Apalache and 88 waiting for TLC |

| Directory | Entries | Classified by the triager | `NEW` after triage |
|---|---:|---:|---:|
| `01parser-fail` | 119 | not read | — |
| `02tlc-crash` | 7,207 | 2,636 (229 `tlc-performance-003`, 1 timeout) | 4,571 |
| `02tlc-fail` | 43,385 | 43,349 | 36 |
| `02apa-crash` | 54,559 | 54,552 (49,669 timeouts) | 7 |
| `02apa-fail` | 920 | 920 | 0 |
| `03aggregator-fail` | 52,550 | 45,372 | 7,178 |

The other directories hold 152,379 entries. These are passes, counterexamples
awaiting the other checker (`02tlc-counterexample`, `02apa-counterexample`) and
quality-gate results.

The metamorphic oracle fails an entry on any counterexample, so the
aggregator's 7,178 `NEW` entries split by verdict pair (TLC, Apalache):

| TLC | Apalache | Entries |
|---|---|---:|
| counterexample | pass | 5,196 |
| fail, exit 150 | pass | 1,231 |
| pass | counterexample | 674 |
| counterexample | counterexample | 60 |
| fail, exit 75 | pass | 17 |

## 1. Previously known issues triggered on the old TLC

### 1.1. TLC findings

| Finding | State | Entries | Stage | Attribution | On `bc1b506` |
|---|---|---:|---|---|---|
| [`tlc-014`](../../findings/TLC/tlc-014.md) | closed | 4,737 + 239 | aggregator, TLC counterexample | all rerun | fixed |
| [`tlc-008`](../../findings/TLC/tlc-008.md) | closed | 519 + 94, and 10 TLC passes | aggregator, TLC counterexample | all rerun | fixed; exits 255 as in `tlc-009` |
| [`tlc-002`](../../findings/TLC/tlc-002.md) | open | 847 | TLC crash | triager | not rerun |
| [`tlc-009`](../../findings/TLC/tlc-009.md) | open | 807 | TLC crash | triager | not rerun |
| [`tlc-010`](../../findings/TLC/tlc-010.md) | open | 748 | TLC crash | triager | not rerun |
| [`tlc-012`](../../findings/TLC/tlc-012.md) | open | 8 + 1 | TLC fail, aggregator | triager; all rerun | reproduces |
| [`tlc-003`](../../findings/TLC/tlc-003.md) | open | 2 | TLC crash | triager | not rerun |
| [`tlc-performance-001`](../../findings/tlc-performance/tlc-performance-001.md) | open | 1 | TLC crash | triager | not rerun |
| [`tlc-performance-002`](../../findings/tlc-performance/tlc-performance-002.md) | open | 1 | TLC crash | triager | not rerun |
| [`tlc-018`](../../findings/TLC/tlc-018.md) | open | 1 | TLC crash | rerun | reproduces |

In "4,737 + 239", the first number counts aggregator entries and the second
counts entries in `02tlc-counterexample` that wait for Apalache.

Every TLC counterexample of corpus88 comes from the two property runs: in none
does the relation run (`INVARIANT Inv`, `PROPERTY StepProperty`) fail. The base
rules of `Rewrites.tla` therefore exposed no TLC defect. All 5,589 are
"property verdicts differ": one side's property holds and the other's is
violated.

- **`tlc-014`.** In 4,975 of the 4,976 entries the violated side is the rewrite.
  Its property has a negated temporal subformula under another temporal operator.
  The rules `DoubleNeg`, `AlwaysAsNotEventually`, `EventuallyAsNotAlways` and
  `DeMorganAnd` write `~~F`, `~<>~F` or `~[]~F`. In `001d0110`, for example, the
  original property `<>((CASE FALSE \in {TRUE, FALSE} -> FALSE) ~> FALSE)`
  holds. Its rewrite `<>((~<>~(G /\ <>G) /\ TRUE) \/ FALSE \/ …)` holds as well,
  with `G` the `~>` formula, but `7e64fd0` reports a counterexample. In
  `6c64e130` the original side is the violated one: its property ends in
  `~> FALSE => FALSE`, which negates the `~>` formula. On `bc1b506` the entries
  give the following outcomes:
  - 4,739 pass;
  - 166 report an evaluation error in the relation run. The spurious verdict had
    outranked it ([ADR 0027](../decisions/0027-temporal-metamorphic-testing.md) §4);
  - 44 exit 255 with an evaluation error in a property run;
  - 27 time out after 60 s in tableau construction, see [§3](#3-the-latest-tlc).
- **`tlc-008`.** One side prints an evaluation error under `<>` in the initial
  state, such as "Attempted to evaluate a CASE with no conditions true", and
  exits 0, so FuzzTLA records that run as a pass. The other side, which
  evaluates the same partial term elsewhere, reports a violation. On `bc1b506`,
  504 entries exit 255 in the failing property run, as in corpus87. The other
  109 report the relation run's evaluation error. In 10 more aggregator entries
  TLC passes against an Apalache counterexample, and a property run exits 0 after
  an evaluation error. On `bc1b506` these exit 255.
- **`tlc-012`.** All 9 entries print the `WF_v(A)` message for `ENABLED UNCHANGED e`
  on both versions. In 2 of them, the command line wraps the message in "TLC
  threw an unexpected exception".
- **`tlc-018`.** The worker of `faf2c200` ran out of heap. On both versions, with
  `-Xmx512m`, the relation run prints a function-domain error in the initial
  state of a fold and then runs out of memory, as in the replay that collects
  the error's call stack. corpus87 has one such entry, `30753804`.

The open crash findings were attributed by the triager from the stored
diagnostic and were not rerun.

### 1.2. Conformance reports

The triager attributes TLC failures and aggregator entries to the conformance
reports below. Two classes outside the triager add
[tlc-temporal-formula-limits](../../conformance/tlc-temporal-formula-limits.md),
which brings the TLC side to 28 reports.

| Count | Stage | Cause | Class |
|---:|---|---|---|
| 4,553 + 17 | TLC crash, property run | "must be of forms" or "cannot handle". 4,553 match a TLC temporal signature of `all-defects.toml`. The other 17 are rows without a signature: 8 bounded quantifiers whose domain reads the state and 9 `WF` or `SF` shapes. On `bc1b506`, 4,362 fail with the same diagnostic in the same run and 191 pass, see [§2](#2-previously-unknown-issues-triggered-on-the-old-tlc) | [tlc-temporal-formula-limits](../../conformance/tlc-temporal-formula-limits.md) |
| 15 | aggregator, TLC fail | "Evaluating action property … failed", followed by a `CASE`, `CHOOSE`, tuple-index or function-application error in `[][A]_v` of a property run; the triager's signatures expect the error at the start of the detail | [case-without-matching-arm](../../conformance/case-without-matching-arm.md), [choose-without-witness](../../conformance/choose-without-witness.md), [function-application-outside-domain](../../conformance/function-application-outside-domain.md) |

The triager attribution, per document and stage:

| Document | TLC fail | Apalache fail | Aggregator |
|---|---:|---:|---:|
| [function-application-outside-domain](../../conformance/function-application-outside-domain.md) | 15,251 |  | 14,609 |
| [choose-without-witness](../../conformance/choose-without-witness.md) | 10,649 |  | 12,691 |
| [case-without-matching-arm](../../conformance/case-without-matching-arm.md) | 7,567 |  | 10,462 |
| [integer-outside-tlc-range](../../conformance/integer-outside-tlc-range.md) | 6,238 |  | 956 |
| [enabled-apalache-unsupported](../../conformance/enabled-apalache-unsupported.md) |  | 253 | 2,457 |
| [head-of-empty-sequence](../../conformance/head-of-empty-sequence.md) | 1,616 |  | 1,023 |
| [constant-property-tlc-rejects](../../conformance/constant-property-tlc-rejects.md) | 60 |  | 1,551 |
| [fairness-apalache-unsupported](../../conformance/fairness-apalache-unsupported.md) |  | 640 | 158 |
| [subseq-outside-domain](../../conformance/subseq-outside-domain.md) | 355 |  | 400 |
| [tail-of-empty-sequence](../../conformance/tail-of-empty-sequence.md) | 339 |  | 178 |
| [modulo-nonpositive-divisor](../../conformance/modulo-nonpositive-divisor.md) | 132 |  | 196 |
| [non-enumerable-initial-assignment](../../conformance/non-enumerable-initial-assignment.md) | 185 |  | 134 |
| [division-by-zero-tlc-fails](../../conformance/division-by-zero-tlc-fails.md) | 123 |  | 153 |
| [choose-over-infinite-set](../../conformance/choose-over-infinite-set.md) | 230 |  | 35 |
| [quantification-over-infinite-set](../../conformance/quantification-over-infinite-set.md) | 197 |  | 33 |
| [zero-power-zero-tlc-fails](../../conformance/zero-power-zero-tlc-fails.md) | 73 |  | 93 |
| [sequence-set-unsupported](../../conformance/sequence-set-unsupported.md) |  | 12 | 94 |
| [finite-set-containing-infinite-set](../../conformance/finite-set-containing-infinite-set.md) | 68 |  | 13 |
| [string-set-unsupported](../../conformance/string-set-unsupported.md) |  | 11 | 65 |
| [filter-over-infinite-set](../../conformance/filter-over-infinite-set.md) | 26 |  | 9 |
| [subset-test-over-infinite-set](../../conformance/subset-test-over-infinite-set.md) | 25 |  | 8 |
| [cardinality-of-infinite-set](../../conformance/cardinality-of-infinite-set.md) | 19 |  | 5 |
| [union-containing-infinite-set](../../conformance/union-containing-infinite-set.md) | 18 |  | 5 |
| [infinite-set-as-state-value](../../conformance/infinite-set-as-state-value.md) | 20 |  | 2 |
| [negative-exponent-tlc-fails](../../conformance/negative-exponent-tlc-fails.md) | 14 |  | 7 |
| [difference-with-infinite-set](../../conformance/difference-with-infinite-set.md) | 13 |  | 3 |
| [function-over-infinite-domain](../../conformance/function-over-infinite-domain.md) | 7 |  | 5 |
| [division-by-zero-apalache-fails](../../conformance/division-by-zero-apalache-fails.md) |  | 3 | 6 |
| [modulo-by-zero-apalache-fails](../../conformance/modulo-by-zero-apalache-fails.md) |  | 1 | 4 |
| [intersection-of-infinite-sets](../../conformance/intersection-of-infinite-sets.md) | 3 |  |  |
| [cartesian-product-with-infinite-set](../../conformance/cartesian-product-with-infinite-set.md) | 1 |  |  |
| [function-set-over-infinite-set](../../conformance/function-set-over-infinite-set.md) | 1 |  |  |
| [string-set-tlc-fails](../../conformance/string-set-tlc-fails.md) | 1 |  |  |

The triager also attributes 110 TLC fails and 15 aggregator entries to the closed
[`apalache-printer-008`](../../findings/apalache-printer/apalache-printer-008.md).
They are the `ENABLED` class of §1.3, not that printer defect.

### 1.3. Issues of the corpus87 report

| Count | Stage | Cause | `bc1b506` | Upstream |
|---:|---|---|---|---|
| 1,228 + 30 | aggregator, TLC fail | `PROPERTY` whose body is a Boolean state variable: "The operator var0, which equals …, cannot be used as a property", exit 150 | 1,225 pass; 17 report an evaluation error and 16 exit 255 in the now checked property | `72a0c291`, [#715](https://github.com/tlaplus/tlaplus/issues/715) |
| 3 | aggregator | an operator argument as the property: "The operator parameterN, which equals \<LAZY …\>, cannot be used as a property" | 1 pass, 1 evaluation error, 1 [constant-property-tlc-rejects](../../conformance/constant-property-tlc-rejects.md) | `9b5afe73`, [#1244](https://github.com/tlaplus/tlaplus/issues/1244) |
| 125 + 1 | TLC fail, aggregator | `ENABLED` over a primed variable that no conjunct has assigned yet: "the identifier step (or `varN`) is either undefined", exit 75. In 19 entries the command line wraps the message in "TLC threw an unexpected exception"; `c52e5960` prints "In computing ENABLED, TLC expected a boolean expression" for `ENABLED (<<(ENABLED …)', FALSE, var1>>[step])` | reproduces (all rerun) | none |
| 5 + 1 | TLC fail, aggregator | `StackOverflowError` in the worker | not reproduced: the command line reports an ordinary evaluation error | the corpus29 deep-expression class |

Each of the 1,258 Boolean-variable entries prints the corpus87 diagnostic on
`7e64fd0` in the rerun. The bodies come from the base modules, which corpus88
adopted from corpus87.

The `ENABLED` class reduces to `ENABLED (b')` and `ENABLED ((ENABLED b)')` for a
Boolean variable `b`. Both versions fail on them, and both check
`ENABLED (b' = TRUE /\ (ENABLED b)')`. It still has no conformance report.

## 2. Previously unknown issues triggered on the old TLC

### 2.1. Rejected negations of action formulas

| Count | Stage | Cause | `bc1b506` | Upstream fix |
|---:|---|---|---|---|
| 191 | TLC crash, property run | a temporal formula that contains an action or `WF`, under a negation that normalization would cancel or push inward: "Temporal formulas containing actions must be of forms <>[]A or []<>A", exit 255 | checks the property; all 191 pass | `4260e4712` "Normalize temporal negations before constructing the liveness tableau" (2026-09-23) |

The negation reaches TLC through the rewrite in every entry: the crashing run is
the rewritten side's. 152 of the 191 bodies contain `~~`, `~<>~` or `~[]~`
literally. 38 more negate a temporal subformula less directly, as in the
`<>~F` that `AlwaysOrBreak` writes or a negated conjunction with a temporal
operand. The last,
`1b571ad8`, has no negation in its rewritten property
`FALSE /\ (WF_FALSE(…) /\ …)`, and was not reduced. In `00ee7ada` the property
is `~~~~[]([][]<><<A>>_FALSE \/ …)`. Minimal properties with
`Spec == Init /\ [][Next]_x`, `Next` incrementing `x` up to 3:

| Property | `7e64fd0` | `bc1b506` |
|---|---|---|
| `[](~~[]<><<x' >= x>>_x)` | must be of forms | counterexample |
| `<>~~<>[][x' >= x]_x` | must be of forms | pass |
| `<>(~~[][x' >= x]_x)` | must be of forms | pass |
| `~<>~[]<><<x' > x>>_x` | must be of forms | counterexample |
| `~(TRUE /\ <>~[]<><<x' > x>>_x)` | must be of forms | counterexample |
| `[](~~WF_x(Next))` | must be of forms | counterexample |
| `[]<><<x' >= x>>_x`, `<>[][x' >= x]_x` | counterexample, pass | the same |

The counterexamples on `bc1b506` are correct: `x` stops at 3, and the
specification has no fairness. `4260e4712` lies between `7e64fd0` and `bc1b506`.
It calls `LNNeg.pushNeg` before simplification, so the tableau sees `[]<>A`
instead of `~~[]<>A`. Its message describes `tlc-014`'s spurious counterexamples. Its
`ActionNormalization` theorem, `~([]~~[]<>A) <=> <><>[]~A`, is this case.
[`tlc-014`](../../findings/TLC/tlc-014.md) describes only the spurious
counterexample, so the rejection is new to this repository, though not to
upstream. The documented shapes of
[tlc-temporal-formula-limits](../../conformance/tlc-temporal-formula-limits.md),
such as `<>WF_x(A)` and `[][A]_v \/ <>P`, still fail on `bc1b506`.

### 2.2. Tableau construction

| Count | Stage | Cause | `bc1b506` | Document |
|---:|---|---|---|---|
| 229 | TLC crash, property run: timeout after 30 s | TLC is still building the tableau of the property (`TBGraph.<init>`): 185 in `findOrCreateNode`, 44 in `particleClosure` | same code | [`tlc-performance-003`](../../findings/tlc-performance/tlc-performance-003.md), filed with this report |

The triager had labelled these 229 entries `TIMEOUT`, and 119 of them check
`SpecC |= PropC`. The 230th TLC timeout, `1a07c012`, stored no thread stacks;
its rerun reports "must be of forms" in 5 s.

TLC deduplicates the particles of the tableau by linear search, in
`TBGraph.findOrCreateNode` and `TBParVec.union`, and compares them with a
quadratic set equality. Patched copies of the two classes index the particles
by a hash instead. With them on the class path, `7e64fd0` finishes 223 of the
229 runs within 300 s:
- 182 finish within the corpus's 30 s limit; the median is 5.5 s;
- 108 pass, 111 report a violation, and 4 report an evaluation error;
- 6 still exceed 300 s.

The finding's minimal property has 13 temporal operators. Both versions build
its tableau of 3,400 nodes in minutes, and with the patch in seconds. corpus87,
which has no rewrites, has the same cause in 2 of its 3 TLC timeouts
(`0609942b`, `796fae54`). [ADR 0030](../decisions/0030-temporal-operator-budget.md)
reported this place for the corpus80 timeouts and bounded the rewrites to 16
temporal operators, but recorded no finding.

## 3. The latest TLC

No previously unknown correctness issue reproduces on `bc1b506`. The reruns
cover the following entries:

- every aggregator `NEW` entry;
- every stage counterexample;
- every TLC crash `NEW` entry;
- every TLC fail left `NEW` or attributed to `tlc-012` or `apalache-printer-008`.

On `bc1b506` none of them yields a metamorphic violation. The two property
runs agree, or a run fails with a diagnostic of §1, or a run exceeds the time
limit (§3.1).

### 3.1. Tableau construction after negation normalization

[`tlc-performance-003`](../../findings/tlc-performance/tlc-performance-003.md)
(§2.2) is unchanged on `bc1b506`. Normalization adds instances: the tableau
of a normalized property can be much larger than that of the operand which
`7e64fd0` left opaque.

| Count | Stage | Cause | `7e64fd0` | `bc1b506` |
|---:|---|---|---|---|
| 38 | property run of an aggregator, stage-counterexample or crash entry, always on the rewritten side | the rewritten property, whose negations `4260e4712` now normalizes | 33 report a violation within a second, 27 of them in `tlc-014` entries; 4 time out as well; 1 is rejected with "must be of forms" | over 60 s in `TBGraph.<init>`. With a 600 s limit, 23 finish in 57 to 585 s (median 173 s) and 15 do not |

The 23 runs that finish agree with the other side of their entry: 17 pass, 5
report a violation, and 1 exits 255 with an evaluation error.

A reduction of `055b77cd`'s rewritten property is `Q0 == <>Q2`, with
`Q2 == ~(~[]G /\ <>~[]G /\ ~(G /\ <>~G))` and `G == []P \/ (P /\ <>~P)` for the
invariant `P == x >= 0`. `bc1b506` builds its tableau of 3,400 nodes in 212 to
325 s, while `PROPERTY Q2` takes 1.5 s. `7e64fd0` builds a tableau of one
branch in 0.7 s and then reports the spurious violation of `tlc-014`. The
finding's `Prop` writes `Q0` without negated temporal subformulas. Its tableau
has the same size, and it is as slow on both versions.

## Outside the three categories

These are not TLC issues and were classified by shape only.

- **Apalache counterexamples.** There are 734 of them: 674 with a TLC pass and 60
  with a TLC counterexample. 88 more entries wait for TLC.
  - 477 match `apalache-eventually-in-conjunction`
    ([`apalache-temporal-006`](../../findings/apalache-temporal/apalache-temporal-006.md)),
    which `EventuallyWeaken` and `AlwaysOrBreak` create.
  - 240 contain `CHOOSE`
    ([choose-multiple-witnesses](../../conformance/choose-multiple-witnesses.md)),
    which `metamorphic-defects.toml` would have quarantined.
  - 17 were not classified by shape. Four of them, `84f5c1ea`, `abac4606`,
    `bd7f672e` and `45821555`, divide a constant negative dividend, as in
    `(-([field2 |-> 1]["field2"])) \div 2`, and two of them also apply `%`. Apalache
    0.63.0 rejects `Inv == (-1) \div 2 = -1` and `Inv == (-1) % 2 = 1`, which
    TLC checks, but decides both correctly when the dividend is a variable
    with value `-1`. `ConstSimplifierBase.scala:112` and `:124` fold constant
    `\div` and `%` with Scala's truncating `BigInt` operators, unchanged on
    Apalache `main` (`a2910d423`). This is not
    [`apalache-bmc-008`](../../findings/apalache-bmc/apalache-bmc-008.md),
    whose divisor is negative and whose dividend is a variable. Filed with this
    report as
    [`apalache-optimizer-002`](../../findings/apalache-optimizer/apalache-optimizer-002.md).

  On `bc1b506` TLC passes 653 of the 674 entries. The rest exit 255, as in
  `tlc-008`, or time out.
- **Apalache crashes.** The triager labels 49,669 timeouts and 4,883 crashes with
  15 Apalache findings. The 7 `NEW` crashes are from the rewriter's output:
  - two undeclared `_skolem` operators;
  - three assignment errors;
  - an expansion of `SUBSET` that is not supported;
  - an interval too long for a sequence.
- **Parser failures.** All 119 are SANY semantic errors, with the same
  diagnostics on both versions.
  - 106 are label errors, "must contain formal parameter" or "Illegal parameter
    … of label"; 100 of them match `printer-nested-case-arm`
    ([`apalache-printer-009`](../../findings/apalache-printer/apalache-printer-009.md)).
  - 8 are level errors of `ApaFoldSet` or `ApaFoldSeqLeft`, from the FuzzTLA
    generator, as in corpus85.
  - 5 are level errors of a rule's `LET` operator: `LetApply`, `LetHigher` or
    `FunApplyConst` passes an argument that contains `[A]_v` to an operator that
    primes or applies `ENABLED` to its parameter. SANY is right to reject these,
    so the rewriter has a level bug.

## Known issues that corpus87 and corpus88 did not trigger

Together, corpus87 and corpus88 triggered 34 of the 92 findings and 45 of the 53
conformance reports at `fa5b6c03`. Seven of these reports carry no label of
their own:
- three duplicate a finding that the triager labels instead:
  choose-symbolic-set-element (`apalache-bmc-006`), set-filter-symbolic-set
  (`apalache-bmc-001`) and symbolic-set-equality (`apalache-bmc-003`);
- four are diagnostics of `apalache-cli-001`: nonconstant-integer-range
  (976 crashes), function-set-expansion-guard (1,045), negative-power-apalache-fails
  (47) and set-map-product-guard (1).

Both campaigns share a configuration, apart from the technique:
- `module` inputs with `ignore = ["unbound", "exotic"]`;
- no library: empty `classpath` and `custom_operators`;
- the checkers TLC `7e64fd0` with one worker and Apalache 0.63.0.

Of the 66 documents they did not trigger, 44 cannot occur in that
configuration:

| Reason | Documents |
|---|---|
| Manyan is not run | `manyan-001` to `manyan-009` |
| TLC with several workers is not run | `tlc-020` |
| The Community Modules are not loaded | `tlc-016`, `tlc-017`, `apalache-rewiring-001`, `apalache-rewiring-002`, `apalache-performance-001`, `apalache-json-003`, bag-nonpositive-multiplicity |
| The recursion and custom-operator libraries are not loaded | `apalache-performance-002`, `apalache-performance-003`, `apalache-typechecker-001` |
| Fixed in Apalache 0.62.3 or earlier | `apalache-printer-001` to `apalache-printer-008`, `apalache-printer-010`, `apalache-json-001`, `apalache-json-002`, `apalache-bmc-002`, `apalache-bmc-011`, `apalache-bmc-013`, `apalache-bmc-017`, `apalache-bmc-018`, `apalache-builder-001`, empty-function-set, let-operand-grouping |
| A SANY regression newer than both tools | `sany-003`, from tlaplus `0a20a0b`; the SANY of `7e64fd0` and of Apalache 0.63.0 accept `-(IF \A a, b \in {1, 2} : a = b THEN 0 ELSE 1)` |
| The generator does not write the shape | `sany-002` and label-inside-except (no label inside an `EXCEPT` replacement), `apalache-printer-011` (definition names are unique) |
| Not a deviation | vacuous-initial-predicate |

The other 22 could have occurred: the configuration allows their shapes, and
the tools of the campaigns have the defect. The Apalache findings marked 0.63.0
were checked with their MWE on the JAR of the corpus. For the rest, whether
the generator reaches the shape is judged from the configuration, not
measured.

| Document | Trigger | Note |
|---|---|---|
| [`tlc-011`](../../findings/TLC/tlc-011.md), [`tlc-023`](../../findings/TLC/tlc-023.md) | a failing domain of `\E` around a temporal formula; `[](F /\ G)` for fairness `F`, `G` | possibly triggered: the diagnostics are those of the 22,958 tlc-temporal-formula-limits crashes, which were not separated. Two corpus88 crashes have the shape of `tlc-023` among other rejected shapes |
| [`tlc-004`](../../findings/TLC/tlc-004.md), [`tlc-005`](../../findings/TLC/tlc-005.md), [`tlc-006`](../../findings/TLC/tlc-006.md) | `CHOOSE` over about 10^5 functions; `\E` or `CHOOSE` over an interval of more than 10^7 integers | `max_collection_size = 8` and the boundary literals allow both. The only heap exhaustions of the two corpora are `tlc-018` |
| [`tlc-007`](../../findings/TLC/tlc-007.md) | an invariant error in a successor whose replay meets another error | the triager has a signature; no match |
| [`tlc-019`](../../findings/TLC/tlc-019.md) | a stack overflow while TLC reconstructs a liveness counterexample | deep fold recursion occurs (`tlc-performance-001`); no exit status 1 |
| [`tlc-021`](../../findings/TLC/tlc-021.md) | an evaluation error while fingerprinting a state two or more steps deep | infinite sets reach states (infinite-set-as-state-value, 22 corpus88 entries); no "Failed to recover the initial state" |
| [`tlc-022`](../../findings/TLC/tlc-022.md) | `=` on function sets with an empty domain or co-domain; fixed after `7e64fd0` | no TLC counterexample other than `tlc-008` and `tlc-014` |
| [`sany-001`](../../findings/SANY/sany-001.md) | a level error that names `%`; fixed after `7e64fd0` | no parser crash |
| [`apalache-bmc-008`](../../findings/apalache-bmc/apalache-bmc-008.md), [division-negative-divisor](../../conformance/division-negative-divisor.md) | `\div` by a negative divisor | reproduces on 0.63.0 |
| [`apalache-bmc-014`](../../findings/apalache-bmc/apalache-bmc-014.md) | a set map over `SUBSET` | reproduces on 0.63.0; the triager has a signature |
| [`apalache-bmc-015`](../../findings/apalache-bmc/apalache-bmc-015.md), [`apalache-bmc-016`](../../findings/apalache-bmc/apalache-bmc-016.md) | `[x \in Nat \|-> e]` under `DOMAIN`; `S \union Nat` | reproduce on 0.63.0. TLC fails on most such shapes, which hides Apalache's verdict |
| [`apalache-bmc-020`](../../findings/apalache-bmc/apalache-bmc-020.md) | a fold whose combinator returns `SUBSET` | reproduces on 0.63.0 |
| [`apalache-bmc-022`](../../findings/apalache-bmc/apalache-bmc-022.md) | `VariantFilter` with a payload `FALSE` or `0` | reproduces on 0.63.0 |
| [`apalache-bmc-023`](../../findings/apalache-bmc/apalache-bmc-023.md) | `\E c \in a..b` with a state-dependent empty range | reproduces on 0.63.0 |
| [`apalache-temporal-005`](../../findings/apalache-temporal/apalache-temporal-005.md) | `[][A]_v` under a Boolean connective, at the shortest length | reproduces on 0.63.0 |
| [`apalache-optimizer-001`](../../findings/apalache-optimizer/apalache-optimizer-001.md) | `ConstSimplifier` drops a `LET` definition that it still references | not checked on 0.63.0; the triager has a signature |
| [case-multiple-true-guards](../../conformance/case-multiple-true-guards.md), [infinite-set-as-membership-element](../../conformance/infinite-set-as-membership-element.md) | overlapping `CASE` guards; `Nat \in S` for a finite `S` | each was observed once or twice in earlier corpora |

The 22 documents record 21 issues, because `apalache-bmc-008` and
division-negative-divisor record the same defect. Excluding `tlc-011` and
`tlc-023`, which may hide among the temporal-formula crashes, the two campaigns
missed 19 issues that their configuration could reach:
- 8 TLC or SANY findings;
- 9 Apalache findings;
- 2 conformance differences.

## Method

- **Triage.** `script/triager.py` classified every crash, fail and aggregator
  entry. It refused corpus88: the metamorphic oracle fails an entry on any
  counterexample ([ADR 0016](../decisions/0016-metamorphic-testing.md) §3), so 60
  aggregator entries in which both checkers report one looked like agreeing
  results. The triager now accepts agreeing counterexamples and labels them
  `NEW`. It also labels a TLC timeout whose thread is in `TBGraph.<init>` as
  `tlc-performance-003`. Both changes have tests in `script/test_triager.py`. FuzzTLA `fa5b6c03` also needs
  the `02gen-*` directories of the generic stage
  ([ADR 0033](../decisions/0033-generic-checker.md)), which corpus88 predates.
  Empty ones were added to the corpus.
- **Rendering and matching.** 12,347 entries were decoded and rendered in one
  JVM per shard with FuzzTLA `fa5b6c03`, and matched against
  `signatures/all-defects.toml`. These are every `NEW` entry, every stage
  counterexample, and the entries attributed to `tlc-012` and
  `apalache-printer-008`.
- **Reruns.** Each rerun replays the TLC stage of
  [ADR 0027](../decisions/0027-temporal-metamorphic-testing.md) §4:
  - first the relation run (`SPECIFICATION Spec`, `INVARIANT Inv`,
    `PROPERTY StepProperty`);
  - then, for a base with a property, `Spec |= Prop` and `SpecC |= PropC`;
  - the outcomes are folded as `PropertyRunsVerdict` does.

  Every run used the command line with `-workers 1 -deadlock -fp 54
  -seed -4297436356756997584 -noGenerateSpecTE`, `-Xmx512m` and a 60 s limit
  per run. The following were rerun:
  - all 7,348 aggregator `NEW` and selected TLC-fail entries, on `7e64fd0` and
    `bc1b506`. `7e64fd0` reproduced the stored TLC verdict of every one;
  - all 421 stage counterexamples, on both versions;
  - all 4,571 TLC crashes, on `bc1b506`. The stored diagnostic gives the run and
    the message on `7e64fd0`;
  - the 38 runs that exceeded 60 s on `bc1b506`, again with a 600 s limit, and
    the checked side of the 12 entries whose explored side then finished;
  - the timed-out property run of the 230 TLC timeouts, on `7e64fd0` with the
    patched `TBGraph` and `TBParVec` of `tlc-performance-003` and a 300 s limit.
- **Known issues not triggered.** Each document that neither corpus triggered
  was compared with the configuration. The Apalache findings that the
  configuration allows were checked with their MWE on Apalache 0.63.0. The
  stored diagnostics of both corpora were searched for the messages of the TLC
  findings and of the `apalache-cli-001` variants.
- **Classification of the counterexamples.** An entry is `tlc-008` if one of its
  `7e64fd0` property runs exits 0 after printing an evaluation error, and
  `tlc-014` otherwise. In `tlc-014` entries, the violated side was compared with
  the explored side that the entry records (`explored: original` or `rewrite`).
- **Upstream fixes.** `4260e4712` and `35d40c91a` (the `tlc-008` fix) were found
  in the history of `tlc2.tool.liveness` between `7e64fd0` and `bc1b506`, and
  confirmed by the minimal properties above.
- **Parser failures.** The 119 `01parser-fail` entries were rendered and checked
  with the SANY of both JARs.
