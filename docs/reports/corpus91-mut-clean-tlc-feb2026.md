# corpus91: a mutation campaign against the TLC of February 2026

This report records what corpus91 found. corpus91 is a `pbt` campaign with the
mutator ([ADR 0010](../decisions/0010-mutation.md)) and the recursion library
([ADR 0015](../decisions/0015-diff-linked-libraries.md),
[Recursive operators](../manual/recursive-operators.md)). It ran TLC `7e64fd0`
(2026-02-23) against Apalache 0.63.0, the latest Apalache release. The report
groups the TLC issues into three categories:

1. previously known issues that the campaign triggered on the old TLC;
2. previously unknown issues triggered on the old TLC;
3. previously unknown issues that reproduce on the latest TLC, `1138302`
   (tla2tools `1.8.0-20261126.022345-128`).

For every issue it gives the entries found by pure PBT and those found by
mutants. "Known" means recorded in [`findings/`](../../findings/) or
[`conformance/`](../../conformance/) at FuzzTLA `f185e6a1`: 102 findings (64
open, 38 closed) and 53 conformance reports. The issues of the
[corpus87](corpus87-pbt-clean-tlc-feb2026.md) and
[corpus88](corpus88-pbt-clean-tlc-feb2026.md) reports also count as known.

## Summary

- **Known issues (category 1).** The campaign reproduced 12 TLC findings, two
  of them closed, and 36 conformance reports.
  - [`tlc-008`](../../findings/TLC/tlc-008.md) (closed, 613 entries) is an
    evaluation error in an eventuality that exits with status 0. It explains
    every TLC pass against an Apalache counterexample that is not an
    order-sensitive fold or a `CHOOSE`.
  - [`tlc-014`](../../findings/TLC/tlc-014.md) (closed, 48 entries) is a
    spurious violation of a property that negates a temporal subformula. Only
    mutants triggered it.
  - The issues of the corpus87 report recur: a Boolean state variable as
    `PROPERTY` (420 entries), an operator argument as `PROPERTY` (6), and
    `ENABLED` over a primed variable that no conjunct has assigned (26). So
    does the rejected negation of an action formula of the corpus88 report
    (5).
- **Previously unknown issues on the old TLC (category 2).** One issue, and
  the recursion library is what exposes it. `7e64fd0` inlines the body of a
  `RECURSIVE` operator while it translates a liveness property, and never stops
  when the operator's recursion is guarded by `IF`. A property such as
  `<>IntIsEven(1)` therefore overflows the Java stack (191 entries, 43 PBT and
  148 mutants). The triager files these under
  [`tlc-performance-001`](../../findings/tlc-performance/tlc-performance-001.md).
  Upstream fixed it in `4daa15584` (2026-03-16).
- **The latest TLC (category 3).** No previously unknown issue reproduces on
  `1138302`. On the rerun entries, its verdicts disagree with Apalache's only
  through known conformance reports and Apalache findings. Of the 3,520
  temporal-formula crashes, 3,515 fail on `1138302` with the same diagnostic.
- **PBT and mutants.** Mutants are 74.6% of the 200,000 inputs. They dominate
  the verdict defects: `tlc-014` (48 of 48), `tlc-008` (445 of 613) and the
  recursion overflow (148 of 191). PBT inputs dominate the TLC failures: 78% of
  `02tlc-fail`, and 312 of the 420 Boolean-variable properties. See
  [PBT and mutants](#4-pbt-and-mutants).

## Setup

| Item | Value |
|---|---|
| Technique, kind | `pbt`, `module`, with the mutator: `feedback_ratio = 0.75`, `max_edits = 5`, `generation_size = 1000` |
| Library | the recursion library: 39 operators of `RecursionTLC` for TLC and `RecursionApalache` for Apalache, `link = "diff"`; the corpus copy predates `a9da7f8e`, which added the `…Fun` operators |
| Checkers | `tlc` (1 worker) and `apalache`; no `tlcn` |
| Known-defect quarantine | none (`known_defects = []`): every shape reached the checkers |
| Generator | `max_steps = 5`, `max_variables = 3`, `integer_literals = "boundary"`; `always`, `eventually`, `leads_to` and `enum_set` weighted ×16, `name` ×8 |
| TLC in the corpus | `tools/tla2tools.jar`, tlaplus/tlaplus `7e64fd0`, the JAR of corpus87 |
| Reference TLC | tlaplus/tlaplus `1138302` (tla2tools `1.8.0-20261126.022345-128`), the build's TLC at `f185e6a1` |
| Apalache | `tools/apalache.jar`, Apalache 0.63.0 (`67b8ec69`), the JAR of corpus87 and the latest release |
| FuzzTLA | `f185e6a1`, used to render, match and triage the entries. Its main code equals that of `b1d642d7`; the corpus does not record the commit that ran it |
| Run | 2026-11-26 13:49 to 2026-11-27 06:12 UTC, 200 generations, stopped at `max_entries = 200000` |

| Directory | Entries | Mutants | Classified by the triager | `NEW` after triage |
|---|---:|---:|---:|---:|
| `01parser-fail` | 18 | 0 | not read | — |
| `02tlc-crash` | 6,558 | 4,211 | 3,030 | 3,528 |
| `02tlc-fail` | 15,273 | 3,333 | 15,188 | 85 |
| `02apa-crash` | 21,446 | 7,374 | 21,445 (14,355 timeouts) | 1 |
| `02apa-fail` | 1,597 | 967 | 1,597 | 0 |
| `03aggregator-fail` | 68,021 | 46,660 | 66,941 | 1,080 |

The other directories hold 113,764 entries: passes, counterexamples awaiting
the other checker, and quality-gate results. One input can sit in two
directories, for example `02tlc-crash` and `02apa-pass`, so the rows count
226,677 entries of 200,000 inputs.

The aggregator's 1,080 `NEW` entries split by verdict pair (TLC, Apalache):

| TLC | Apalache | Entries | Mutants |
|---|---|---:|---:|
| pass | counterexample | 656 | 483 |
| fail, exit 150 | counterexample | 330 | 100 |
| counterexample | pass | 76 | 63 |
| fail, exit 150 | pass | 17 | 10 |
| fail, exit 75 | pass | 1 | 0 |

The two rows of exit 150 are the two `PROPERTY` classes of
[§1.3](#13-issues-of-the-corpus87-and-corpus88-reports) (346 entries) and one
`tlc-015` entry. The row of exit 75 is a stack overflow of §1.3.

## 1. Previously known issues triggered on the old TLC

### 1.1. TLC findings

| Finding | State | Entries | PBT | Mutants | Stage | Attribution | On `1138302` |
|---|---|---:|---:|---:|---|---|---|
| [`tlc-009`](../../findings/TLC/tlc-009.md) | open | 1,623 | 478 | 1,145 | TLC crash | triager | not rerun |
| [`tlc-002`](../../findings/TLC/tlc-002.md) | open | 706 | 91 | 615 | TLC crash | triager | not rerun |
| [`tlc-008`](../../findings/TLC/tlc-008.md) | closed | 613 | 168 | 445 | aggregator | all rerun | fixed: 612 exit 255 as in `tlc-009`, 1 reports an invariant violation |
| [`tlc-010`](../../findings/TLC/tlc-010.md) | open | 472 | 72 | 400 | TLC crash | triager | not rerun |
| [`tlc-014`](../../findings/TLC/tlc-014.md) | closed | 48 | 0 | 48 | aggregator | all rerun | fixed: 44 pass, 4 report an evaluation error |
| [`tlc-003`](../../findings/TLC/tlc-003.md) | open | 33 | 1 | 32 | TLC crash | triager | not rerun |
| [`tlc-013`](../../findings/TLC/tlc-013.md) | open | 7 | 4 | 3 | TLC crash | `Prop` shape; all rerun | reproduces |
| [`tlc-015`](../../findings/TLC/tlc-015.md) | open | 1 | 0 | 1 | aggregator | rerun | reproduces |
| [`tlc-018`](../../findings/TLC/tlc-018.md) | open | 3 | 1 | 2 | TLC crash | triager (2); rerun (1) | reproduces (`cd79aa33`) |
| [`tlc-performance-001`](../../findings/tlc-performance/tlc-performance-001.md) | open | 2 | 0 | 2 | TLC crash | rerun | reproduces with the default stack (`bb69a9e4`) |
| [`tlc-performance-002`](../../findings/tlc-performance/tlc-performance-002.md) | open | 1 | 1 | 0 | TLC crash | triager | not rerun |
| [`tlc-012`](../../findings/TLC/tlc-012.md) | open | 1 | 1 | 0 | TLC fail | triager; rerun | reproduces |

- **`tlc-008`.** TLC prints an evaluation error raised under `<>` in the
  initial state and exits 0, so FuzzTLA records a pass against Apalache's
  counterexample. All 613 entries do so on `7e64fd0`; 583 of them match the
  `tlc-partial-operator-under-eventuality` signature. In 251 of them TLC also
  prints an invariant violation, which the exit status hides. The partial operators are the usual ones: a tuple index out of range
  (175 entries), `CASE` without a true guard (141), `CHOOSE` without a witness
  (121), and function application outside the domain (87). In `00396810`, the
  eventuality is `<>(CHOOSE b \in SetUnionAll({{TRUE}, {FALSE}} \ {{TRUE}, {FALSE}}) : var0)`,
  a `CHOOSE` over the empty set that the recursion library computes.
- **`tlc-014`.** Old TLC reports a temporal violation that neither `1138302`
  nor Apalache reports. Each of the 48 properties puts a temporal formula under
  `=>` or `~>`, whose expansion yields the `~~F` or `~<>~` shapes of the
  finding, as in corpus87. Examples are `<>([]([]FALSE)) => var0` (`d3428d8e`)
  and `[]IsFiniteSet({…}) ~> FALSE => FALSE` (`6f48d804`). The 4 entries that
  report an evaluation error on `1138302` divide by zero, index a tuple out of
  range, or apply a function outside its domain in the property. The spurious
  verdict had hidden that error, as in 166 entries of corpus88.
- **`tlc-013`.** In these 7 crashes, `Prop` puts a label directly on
  `[][A]_v`, as in `Prop == label8 :: ([]([(<<FALSE>>')[1]]_FALSE))` (`80af5ab6`).
  All of them report "must be of forms" on both versions.
- **`tlc-015`.** In `56e2209a`, `Prop` is
  `<>(ApaFoldSet(LAMBDA a, x : <<step, …>>[1] * step <= 1, FALSE, …))`. Both
  versions report "the identifier step is either undefined" with exit 151; the
  worker stored exit 150. The triager's undefined-identifier pattern, filed
  under `apalache-printer-008`, requires exit 75, so it leaves the entry `NEW`.
- **`tlc-018`.** The worker of `cd79aa33` ran out of heap after a
  function-domain error. A rerun with `-Xmx1024m` does the same on both
  versions.
- **`tlc-performance-001`.** The triager labels 193 stack overflows with this
  finding. Two are the finding: in `bb69a9e4`, `Next` sets `var0` to three
  copies of itself and four pairs, and `Inv` folds `ApaFoldSeqLeft` over up to
  968 elements.
  With the default stack, `bb69a9e4` overflows on both versions; with a 16 MB
  stack it passes. `97347b1a` passes on this machine. The other 191 are the
  issue of [§2](#2-previously-unknown-issues-triggered-on-the-old-tlc).

The open crash findings were attributed by the triager from the stored
diagnostic and were not rerun.

### 1.2. Conformance reports

The triager attributes TLC failures and aggregator entries to the conformance
reports below. Three classes outside the triager add
[tlc-temporal-formula-limits](../../conformance/tlc-temporal-formula-limits.md),
[order-sensitive-set-fold](../../conformance/order-sensitive-set-fold.md) and
[choose-multiple-witnesses](../../conformance/choose-multiple-witnesses.md).

| Count | PBT | Mutants | Stage | Cause | Class |
|---:|---:|---:|---|---|---|
| 3,515 | 1,653 | 1,862 | TLC crash | "must be of forms" (2,642) or "cannot handle" (873), with the same diagnostic on `1138302`; every one matches a TLC temporal signature of `all-defects.toml`. 5 more entries pass the check on `1138302` ([§1.3](#13-issues-of-the-corpus87-and-corpus88-reports)) | [tlc-temporal-formula-limits](../../conformance/tlc-temporal-formula-limits.md); [`tlc-011`](../../findings/TLC/tlc-011.md) and [`tlc-023`](../../findings/TLC/tlc-023.md) print the same diagnostics and are not separated |
| 41 + 7 | 7 | 41 | aggregator | TLC pass on both versions and Apalache counterexample (41), or a TLC violation on both versions and Apalache pass (7), in an `ApaFoldSet` whose combinator returns its element or does not commute, such as `LAMBDA acc, x : acc => x` (`d54900fb`) | [order-sensitive-set-fold](../../conformance/order-sensitive-set-fold.md), by shape |
| 2 + 1 | 1 | 2 | aggregator | a `CHOOSE` with several witnesses as the only cause, as in `Prop == (CHOOSE b \in {TRUE, FALSE} : …) ~> FALSE` (`5f48a770`) | [choose-multiple-witnesses](../../conformance/choose-multiple-witnesses.md), by shape |

The triager attribution, per document and stage:

| Document | TLC fail | Apalache fail | Aggregator | PBT | Mutants |
|---|---:|---:|---:|---:|---:|
| [function-application-outside-domain](../../conformance/function-application-outside-domain.md) | 4,990 |  | 16,105 | 9,635 | 11,460 |
| [constant-false-invariant](../../conformance/constant-false-invariant.md) | 1,306 |  | 17,551 | 5,748 | 13,109 |
| [choose-without-witness](../../conformance/choose-without-witness.md) | 3,619 |  | 12,386 | 6,805 | 9,200 |
| [case-without-matching-arm](../../conformance/case-without-matching-arm.md) | 2,106 |  | 10,944 | 4,763 | 8,287 |
| [constant-property-tlc-rejects](../../conformance/constant-property-tlc-rejects.md) | 146 |  | 2,714 | 881 | 1,979 |
| [enabled-apalache-unsupported](../../conformance/enabled-apalache-unsupported.md) |  | 222 | 2,554 | 934 | 1,842 |
| [fairness-apalache-unsupported](../../conformance/fairness-apalache-unsupported.md) |  | 1,311 | 973 | 713 | 1,571 |
| [integer-outside-tlc-range](../../conformance/integer-outside-tlc-range.md) | 1,088 |  | 451 | 1,303 | 236 |
| [head-of-empty-sequence](../../conformance/head-of-empty-sequence.md) | 554 |  | 940 | 990 | 504 |
| [subseq-outside-domain](../../conformance/subseq-outside-domain.md) | 185 |  | 767 | 386 | 566 |
| [finite-set-containing-infinite-set](../../conformance/finite-set-containing-infinite-set.md) | 528 |  | 284 | 365 | 447 |
| [tail-of-empty-sequence](../../conformance/tail-of-empty-sequence.md) | 119 |  | 222 | 231 | 110 |
| [modulo-nonpositive-divisor](../../conformance/modulo-nonpositive-divisor.md) | 63 |  | 262 | 91 | 234 |
| [division-by-zero-tlc-fails](../../conformance/division-by-zero-tlc-fails.md) | 48 |  | 226 | 84 | 190 |
| [sequence-set-unsupported](../../conformance/sequence-set-unsupported.md) |  | 62 | 105 | 75 | 92 |
| [non-enumerable-initial-assignment](../../conformance/non-enumerable-initial-assignment.md) | 50 |  | 104 | 83 | 71 |
| [quantification-over-infinite-set](../../conformance/quantification-over-infinite-set.md) | 47 |  | 86 | 48 | 85 |
| [choose-over-infinite-set](../../conformance/choose-over-infinite-set.md) | 90 |  | 35 | 73 | 52 |
| [infinite-set-as-state-value](../../conformance/infinite-set-as-state-value.md) | 55 |  | 37 | 43 | 49 |
| [zero-power-zero-tlc-fails](../../conformance/zero-power-zero-tlc-fails.md) | 26 |  | 65 | 32 | 59 |
| [infinite-set-as-membership-element](../../conformance/infinite-set-as-membership-element.md) | 63 |  | 22 | 35 | 50 |
| [union-containing-infinite-set](../../conformance/union-containing-infinite-set.md) | 6 |  | 36 | 11 | 31 |
| [difference-with-infinite-set](../../conformance/difference-with-infinite-set.md) | 32 |  | 2 | 8 | 26 |
| [function-over-infinite-domain](../../conformance/function-over-infinite-domain.md) | 11 |  | 20 | 14 | 17 |
| [filter-over-infinite-set](../../conformance/filter-over-infinite-set.md) | 14 |  | 16 | 19 | 11 |
| [string-set-unsupported](../../conformance/string-set-unsupported.md) |  | 2 | 19 | 13 | 8 |
| [subset-test-over-infinite-set](../../conformance/subset-test-over-infinite-set.md) | 12 |  | 3 | 8 | 7 |
| [cardinality-of-infinite-set](../../conformance/cardinality-of-infinite-set.md) | 6 |  | 1 | 6 | 1 |
| [negative-exponent-tlc-fails](../../conformance/negative-exponent-tlc-fails.md) | 1 |  | 1 | 2 | 0 |
| [modulo-by-zero-apalache-fails](../../conformance/modulo-by-zero-apalache-fails.md) |  |  | 2 | 1 | 1 |
| [cartesian-product-with-infinite-set](../../conformance/cartesian-product-with-infinite-set.md) | 2 |  |  | 1 | 1 |
| [intersection-of-infinite-sets](../../conformance/intersection-of-infinite-sets.md) | 1 |  |  | 1 | 0 |
| [division-by-zero-apalache-fails](../../conformance/division-by-zero-apalache-fails.md) |  |  | 1 | 0 | 1 |

The triager also attributes 19 TLC fails and 7 aggregator entries to the closed
[`apalache-printer-008`](../../findings/apalache-printer/apalache-printer-008.md).
They are the `ENABLED` class of §1.3, not that printer defect.

### 1.3. Issues of the corpus87 and corpus88 reports

| Count | PBT | Mutants | Stage | Cause | `1138302` | Upstream |
|---:|---:|---:|---|---|---|---|
| 340 + 80 | 312 | 108 | aggregator, TLC fail | `PROPERTY` whose body is a Boolean state variable: "The operator var0, which equals …, cannot be used as a property", exit 150 | gets past the property in all 420: 14 pass, 189 report an invariant violation, 71 a property violation, 146 an evaluation error | `72a0c291`, [#715](https://github.com/tlaplus/tlaplus/issues/715) |
| 6 | 2 | 4 | aggregator | an operator argument as the property: "The operator parameterN, which equals \<LAZY …\>, cannot be used as a property" | 2 pass, 1 invariant violation, 1 evaluation error, 2 [constant-property-tlc-rejects](../../conformance/constant-property-tlc-rejects.md) | `9b5afe73`, [#1244](https://github.com/tlaplus/tlaplus/issues/1244) |
| 19 + 7 | 22 | 4 | TLC fail, aggregator | `ENABLED` over a primed variable that no conjunct has assigned yet: "the identifier step (or `varN`) is either undefined", exit 75 | reproduces in 18 of the 19 TLC fails; `5da1aab1` reports a tuple-index error first. The 7 aggregator entries were not rerun | none |
| 5 + 1 | 6 | 0 | TLC fail, aggregator | `StackOverflowError` in the worker | not reproduced: both versions report an ordinary evaluation error, with the default stack and with 16 MB | the corpus29 deep-expression class |
| 5 | 3 | 2 | TLC crash | a negated fairness or action formula, as in `<>(~(WF_FALSE(var0)))` (`45e0cb5e`): "must be of forms" | checks the property: 1 violation, correct; 1 pass; 3 evaluation errors, one of them without a message, as in [`tlc-010`](../../findings/TLC/tlc-010.md) | `4260e4712`, the corpus88 class |

All 420 Boolean-variable entries print the corpus87 diagnostic on `7e64fd0` in
the rerun. All 26 `ENABLED` entries contain `ENABLED`. The class still has no
conformance report. The corpus ran on an amd64 host, whose default Java thread
stack is 1 MB. The reruns ran on aarch64, where it is 2 MB. This may explain
why the 6 stack overflows do not reproduce.

The property `<>~WF_FALSE(x >= 0)` reproduces the negation class: `7e64fd0`
rejects it, and `1138302` reports the correct violation. `<<A>>_FALSE` is never enabled, so `WF_FALSE(A)` holds in every
behavior.

## 2. Previously unknown issues triggered on the old TLC

| Count | PBT | Mutants | Stage | Cause | `1138302` | Upstream fix |
|---:|---:|---:|---|---|---|---|
| 191 | 43 | 148 | TLC crash, exit 255 | "This was a Java StackOverflowError. It was probably the result of an incorrect recursive function definition": a `RECURSIVE` Boolean operator applied to constant arguments in a liveness property. Every entry applies the library's `IntIsEven` in `Prop` or `Fairness` | checks the property; no verdict disagrees with Apalache | `4daa15584` "Implement lazy evaluation for operator arguments in Liveness.java" (2026-03-16) |

`Liveness.astToLive` of `7e64fd0` translates an application of a user-defined
operator by evaluating its arguments and translating the operator's body in
their context. It does so for a `RECURSIVE` operator too. It translates both
branches of an `IF`, so the recursion never reaches its base case. The library's
`IntIsEven` is `IsEven(n % 64)`, with `IsEven` and `IsOdd` mutually recursive
through `IF k = 0 THEN … ELSE IsOdd(k - 1)`. A minimal module:

```tla
---- MODULE M ----
EXTENDS Integers
VARIABLE x
RECURSIVE IsEven(_), IsOdd(_)
IsEven(k) == IF k = 0 THEN TRUE ELSE IsOdd(k - 1)
IsOdd(k) == IF k = 0 THEN FALSE ELSE IsEven(k - 1)
Init == x = 0
Next == x < 2 /\ x' = x + 1
Spec == Init /\ [][Next]_x
Prop == <>IsEven(1)
====
```

With `SPECIFICATION Spec` and `PROPERTY Prop`:

| Property | `7e64fd0` | `1138302` |
|---|---|---|
| `<>IsEven(1)` | stack overflow | violation, correct |
| `<>IsEven(2)`, `<>IsOdd(1)`, `[]<>IsEven(2)` | stack overflow | pass, correct |
| `IsEven(2) ~> (x = 2)`, `<>(IF IsEven(2) THEN x = 2 ELSE FALSE)` | stack overflow | violation, correct: there is no fairness |
| `<>Even(1)` with `Even(k) == IF k = 0 THEN TRUE ELSE ~Even(k - 1)` | stack overflow | violation, correct |
| `[]IsEven(1)`, `[]IsEven(2)` | violation, pass | the same |
| `<>IsEven(x)` | pass | pass |
| `<>(Sum({1, 2}) = 3)`, `Sum` recursive and integer-valued | pass | pass |

`[]IsEven(c)` is checked as an invariant. In `<>IsEven(x)` the argument cannot
be evaluated without a state, and in `Sum({1, 2}) = 3` the operator is an
operand of `=`. In these three cases TLC wraps the call opaquely. A 16 MB stack
does not help: all 191 entries still overflow on `7e64fd0`.

`4daa15584` skips the syntactic expansion of an operator for which
`opDef.getInRecursive()` holds. Its comment states the cause: "astToLive would
endlessly inline the self-referential body". `4ba7d8811` (2026-05-26) later
expands a `RECURSIVE` operator again when its body is temporal-level, and
evaluates a constant `IF` guard first, so that the expansion reaches its base
case.

The issue is new to this repository, not to TLC's maintainers. In this corpus
only `IntIsEven` triggers it. `SetAllPositive` is a Boolean recursion as well,
and it occurs under `<>` without an overflow, as in
`<>(<>(SetAllPositive({1, 2, 3})) ~> FALSE) => FALSE` (`0006b4e9`). Its
recursive argument `S \ {Any(S)}` cannot be evaluated once `S` is empty, and
`astToLive` catches the exception and wraps the call opaquely. `IntIsEven`
recurses on an integer that can always be computed.

## 3. The latest TLC

No previously unknown issue reproduces on `1138302`. The reruns cover the
following entries:

- every aggregator `NEW` entry (1,080);
- every TLC fail left `NEW` (85), or attributed to `tlc-012` or
  `apalache-printer-008` (20);
- every TLC crash `NEW` entry (3,528);
- every crash that the triager attributes to `tlc-performance-001` (193).

On `1138302`, the corrected verdicts of `tlc-008`, `tlc-014`, the `PROPERTY`
classes and the recursion overflow agree with Apalache, or fail with a
diagnostic of §1. Of the 3,528 TLC crashes, 3,515 temporal-formula crashes and
the 7 `tlc-013` crashes fail with the same diagnostic. `cd79aa33` runs out of
heap again (`tlc-018`), and the 5 negated formulas of §1.3 get past the check.
One of them, `4b48b61e`, then hits `tlc-010`. Its property is
`<>(WF_FALSE({<<…>>[step], …} \in …)) => FALSE`, and with `step = 0`, TLC prints
"The behavior up to this point is:" without a diagnostic and exits 255. The
smaller `[]<>(<<1>>[x] = 1)`, with `x = 0` initially, does the same on both
versions.

Apalache had not run on the 193 crashes of the last item. A rerun of Apalache
0.63.0 on each and a comparison with `1138302` gives:

| `1138302` | Apalache | Entries |
|---|---|---:|
| pass | pass | 53 |
| invariant or property violation | counterexample | 58 |
| evaluation error | counterexample | 38 |
| evaluation error | pass | 17 |
| any | Apalache error or timeout | 27 |

In no entry does one checker pass while the other reports a violation. The
evaluation errors are the partial operators of §1.2. `530ba9bd` is rejected as a
tautology, exit 77
([constant-property-tlc-rejects](../../conformance/constant-property-tlc-rejects.md)):
its property is `IntIsEven(IntPow2(1)) \/ …`, which `4ba7d8811` now evaluates.
In 14 entries, the command-line TLC wraps an evaluation error in `Next` in "TLC
threw an unexpected exception … java.lang.RuntimeException". corpus88 saw the
same wrapping; the FuzzTLA worker reports the evaluation error directly.

## 4. PBT and mutants

A PBT entry has no `gen.parent`: the generator decoded it from fresh random
bytes. A mutant has a `gen.parent`: up to 5 byte edits of an entry of
`04quality-pass`, an entry on which both checkers agreed. Of the 200,000
inputs, 50,750 are PBT and 149,250 are mutants (74.6%).

| Issue | Category | Entries | PBT | Mutants | Mutant share |
|---|---|---:|---:|---:|---:|
| all inputs | — | 200,000 | 50,750 | 149,250 | 75% |
| [`tlc-009`](../../findings/TLC/tlc-009.md) | 1 | 1,623 | 478 | 1,145 | 71% |
| [`tlc-002`](../../findings/TLC/tlc-002.md) | 1 | 706 | 91 | 615 | 87% |
| [`tlc-008`](../../findings/TLC/tlc-008.md) | 1 | 613 | 168 | 445 | 73% |
| [`tlc-010`](../../findings/TLC/tlc-010.md) | 1 | 472 | 72 | 400 | 85% |
| [`tlc-014`](../../findings/TLC/tlc-014.md) | 1 | 48 | 0 | 48 | 100% |
| [`tlc-003`](../../findings/TLC/tlc-003.md) | 1 | 33 | 1 | 32 | 97% |
| [`tlc-013`](../../findings/TLC/tlc-013.md) | 1 | 7 | 4 | 3 | 43% |
| [`tlc-018`](../../findings/TLC/tlc-018.md), [`tlc-performance-001`](../../findings/tlc-performance/tlc-performance-001.md), [`tlc-performance-002`](../../findings/tlc-performance/tlc-performance-002.md), [`tlc-012`](../../findings/TLC/tlc-012.md), [`tlc-015`](../../findings/TLC/tlc-015.md) | 1 | 8 | 3 | 5 | — |
| [tlc-temporal-formula-limits](../../conformance/tlc-temporal-formula-limits.md) | 1 | 3,515 | 1,653 | 1,862 | 53% |
| [order-sensitive-set-fold](../../conformance/order-sensitive-set-fold.md), [choose-multiple-witnesses](../../conformance/choose-multiple-witnesses.md) | 1 | 51 | 8 | 43 | 84% |
| Boolean variable as `PROPERTY` (corpus87) | 1 | 420 | 312 | 108 | 26% |
| operator argument as `PROPERTY` (corpus87) | 1 | 6 | 2 | 4 | — |
| `ENABLED` over an unassigned primed variable (corpus87) | 1 | 26 | 22 | 4 | 15% |
| negated action formula (corpus88) | 1 | 5 | 3 | 2 | — |
| recursive operator in a liveness property | 2 | 191 | 43 | 148 | 77% |

Observations:

- **Only mutants triggered `tlc-014`.** The 48 entries have 43 distinct parents,
  which descend from 11 PBT inputs. No parent shows the defect itself: every
  parent is an agreeing entry of `04quality-pass`, so the edits produced the
  shape.
- **PBT found every class of more than two entries, except `tlc-014`.** The
  first entries of `tlc-008` and of the recursion overflow are PBT entries of
  generations 0 and 1. The 148 recursion mutants have 84 distinct parents and
  22 PBT ancestors, so mutation spread the shape from many inputs rather than
  copying one.
- **PBT dominates the rejections of a property.** 312 of the 420
  Boolean-variable properties and 22 of the 26 `ENABLED` failures are PBT
  entries, as are 78% of `02tlc-fail`. A plausible reason is that a mutant
  inherits a parent on which both checkers agreed, and a few byte edits rarely
  turn its property into a bare variable.
- **Mutants are over-represented in some crashes and disagreements.**
  `tlc-002`, `tlc-003`, `tlc-010` and the order-sensitive folds are 84% to 97%
  mutants, above the 75% baseline.

## Outside the three categories

These are not TLC issues and were classified by shape only, except where a
rerun is stated.

- **TLC counterexamples against Apalache passes.** TLC reports an invariant or
  property violation on both versions, and Apalache passes, in 28 entries.
  TLC's verdict is correct in each entry checked by hand. By the first
  matching shape:
  - 12 apply a fold whose combinator returns `SUBSET` or the library's
    `SetPowerset`
    ([`apalache-bmc-020`](../../findings/apalache-bmc/apalache-bmc-020.md)).
    Five of them were rerun on Apalache. In `8d4fb086`, `550adf9d`, `4fa0db2f`
    and `8740a6ba`, Apalache reports "Transition #0 is disabled" for an `Init`
    that has a solution, such as
    `var0 \in ApaFoldSet(LAMBDA a, x : SUBSET (SetUnionAll(a) \ {{1}, {2}, {3}}), …)`.
    In `43e010a0`, Apalache explores the states and finds the violated
    invariant to hold.
  - 7 apply `ENABLED` in a `CASE` guard
    ([`apalache-bmc-019`](../../findings/apalache-bmc/apalache-bmc-019.md)),
    as in `1149828c`, whose `Inv` is `IF (CASE ENABLED (var0 >= step) -> …) … THEN FALSE ELSE FALSE`.
  - 7 fold with an order-sensitive combinator, and 1 is a `CHOOSE` with
    several witnesses (§1.2).
  - 1 applies `VariantFilter`
    ([`apalache-bmc-022`](../../findings/apalache-bmc/apalache-bmc-022.md)).
- **Apalache crashes.** The triager labels 14,355 timeouts and 7,090 crashes
  with 15 Apalache findings. The one `NEW` crash, `e154a288` (PBT), is a
  rewriter error, "No rewriting rule applies to expression: q79$1 ∈ {⟨1⟩, ⟨2⟩, ⟨3⟩}",
  and was not reduced.
- **Parser failures.** All 18 are PBT entries with the SANY label error "Label
  … must contain formal parameter", on both versions; 17 match
  `printer-nested-case-arm`
  ([`apalache-printer-009`](../../findings/apalache-printer/apalache-printer-009.md)).

## Known TLC issues that corpus91 did not trigger

| Finding | Reason, where evident |
|---|---|
| [`tlc-020`](../../findings/TLC/tlc-020.md) (closed) | Needs several workers; the corpus has no `tlcn` stage |
| [`tlc-016`](../../findings/TLC/tlc-016.md), [`tlc-017`](../../findings/TLC/tlc-017.md) (closed) | Community Modules overrides; the corpus links only the recursion library |
| [`tlc-024`](../../findings/TLC/tlc-024.md) | Found by counterexample replay; `replay = false` |
| [`sany-003`](../../findings/SANY/sany-003.md) | A SANY regression of tlaplus `0a20a0b` (2026-10-02), newer than `7e64fd0` |
| [`tlc-011`](../../findings/TLC/tlc-011.md), [`tlc-023`](../../findings/TLC/tlc-023.md) | Not separated from the temporal-formula crashes, which print the same diagnostics |
| [`tlc-performance-003`](../../findings/tlc-performance/tlc-performance-003.md) | No TLC timeout: no `TIMEOUT` label in `02tlc-crash` |
| [`tlc-001`](../../findings/TLC/tlc-001.md), [`tlc-004`](../../findings/TLC/tlc-004.md), [`tlc-005`](../../findings/TLC/tlc-005.md), [`tlc-006`](../../findings/TLC/tlc-006.md), [`tlc-007`](../../findings/TLC/tlc-007.md), [`tlc-019`](../../findings/TLC/tlc-019.md), [`tlc-021`](../../findings/TLC/tlc-021.md), [`tlc-022`](../../findings/TLC/tlc-022.md) (closed), [`sany-001`](../../findings/SANY/sany-001.md) (closed), [`sany-002`](../../findings/SANY/sany-002.md), [`sany-004`](../../findings/SANY/sany-004.md) | No entry with their diagnostic or verdict pattern |

## Method

- **Triage.** `script/triager.py` classified every crash, fail and aggregator
  entry. No signature was changed.
- **Origin.** `fuzztla export-db` exported the corpus; the `parent` column
  separates PBT entries from mutants. Ancestors were followed through
  `parent` within the corpus.
- **Rendering and matching.** All 4,712 `NEW` and parser-failure entries were
  decoded and rendered in eight JVMs with FuzzTLA `f185e6a1`, and matched against
  `signatures/all-defects.toml`. Each was also rendered as the TLC module,
  its configuration, and the Apalache JSON IR with the Apalache arguments of
  the worker.
- **Reruns.** Each TLC rerun used the rendered configuration
  (`SPECIFICATION Spec`, `INVARIANT Inv`, `PROPERTY Prop`) with
  `-workers 1 -deadlock -fp 6 -seed 2259799937058764445`, the settings of the
  corpus. It ran with `-Xmx1024m`, the corpus's heap, and `-Xss16m`. The
  stack overflows were also rerun with the default stack. The recursion library
  was on `TLA-Library`. Each rerun ran on `7e64fd0` and on `1138302`, except
  the 3,528 crashes, which ran on `1138302` only. Apalache 0.63.0 reran the 193
  stack-overflow crashes and five aggregator entries with
  `check --init=Init --next=Next --inv=Inv --temporal=Liveness --length=N --no-deadlock`.
- **Upstream fix.** The fixing commit was found in the history of
  `Liveness.java` between `7e64fd0` and `bc1b506`, the TLC snapshot before
  `1138302`, and the minimal module above was run on `7e64fd0` and `1138302`.
