# corpus89-manyan: a pbt campaign of TLC against Manyan

This report records what corpus89-manyan found. corpus89-manyan is a `pbt` campaign of
250,000 `module` entries that runs TLC `bc1b506` against Manyan `v0.17.0` as the generic
checker ([ADR 0033](../decisions/0033-generic-checker.md)). The report covers both tools:
1. Manyan's disagreements with TLC, new and known;
2. TLC's failures, and whether any of them is new.

"Known" means recorded in [`findings/`](../../findings/) or
[`conformance/`](../../conformance/) at FuzzTLA `ba2e1e4c`, which has `manyan-001` to
`manyan-009`.

## Summary

- **No verdict disagreement.** In none of the 15,787 failing entries did both tools
  complete with different verdicts, a pass against a counterexample. In every one, at
  least one tool failed or crashed.
- **Manyan, new.** Five defects, filed with this report as
  [`manyan-010`](../../findings/manyan/manyan-010.md) to
  [`manyan-014`](../../findings/manyan/manyan-014.md):
  - `manyan-010`, 32 entries: Manyan cannot handle a `LET` with a temporal body under
    `[]`, `<>`, `~>` or `~`, which TLC checks. An example is
    `[](LET c == 1 IN <>(x = 3))`.
  - `manyan-011`, 1 entry: Manyan loses the next state when an operator argument is
    applied to a primed expression. An example is `Next == Ap(Id, x' = 1 - x)` with
    `Ap(Op(_), v) == Op(v)`. `ApaFoldSet` and `ApaFoldSeqLeft` with an action as their
    initial value fail the same way under `ENABLED`. Few generated inputs reach this, but
    specifications that fold actions do.
  - `manyan-012`, 2 entries: Manyan omits an evaluation error in a selected tuple element
    of a constant definition, such as `[t \in {"A"} |-> <<1 \div 0>>[1]]`. TLC reports it
    when it fingerprints the value.
  - `manyan-013`, 5 entries: printing a function-domain error takes exponential time and
    memory in the nesting of failing lazy functions: 31 s and 19.6 GB at depth 12. TLC's
    [`tlc-performance-002`](../../findings/tlc-performance/tlc-performance-002.md) has
    the same time, but its memory stays small.
  - `manyan-014`, 1 entry: Manyan's tableau for nested `~>` grows exponentially:
    `(((x = 1) ~> (x = 2)) ~> FALSE) ...` with seven `~> FALSE` takes 27 s and 10 GB,
    and TLC 0.4 s.
- **Manyan, known.** `manyan-001` to `manyan-009` explain most of Manyan's failures and
  crashes on inputs TLC handles. `manyan-001`, `FALSE` as an action subscript, alone
  rejects 5,402 entries, because the corpus quarantined no known defect.
- **Manyan reproduces TLC's own defects.** On 5,289 entries both tools crashed, and on
  5,051 of them they printed the same diagnostic. Manyan exits 255 where TLC escapes an
  evaluation error as error 1000 or rejects a temporal formula of the documented limits.
  It follows TLC's conventions even where they are TLC defects
  ([`tlc-009`](../../findings/TLC/tlc-009.md), [`tlc-010`](../../findings/TLC/tlc-010.md),
  [`tlc-002`](../../findings/TLC/tlc-002.md)).
- **TLC.** No new TLC defect. TLC `bc1b506` was the head of tlaplus/tlaplus `master` on
  2026-11-25, so every TLC result below is on the latest TLC.
  - 5,574 crashes reject a temporal formula of a documented shape
    ([tlc-temporal-formula-limits](../../conformance/tlc-temporal-formula-limits.md)), and
    10 more are the labelled `[][A]_v` of [`tlc-013`](../../findings/TLC/tlc-013.md).
  - The other crashes are the known `tlc-009` (1,596), `tlc-010` (2,550) and `tlc-002`
    (604), and single entries of `tlc-001` and `tlc-performance-001`.
  - Three failures are [`tlc-015`](../../findings/TLC/tlc-015.md), and one is
    [`tlc-012`](../../findings/TLC/tlc-012.md).
  - The other TLC failures are documented conformance differences.

## Setup

| Item | Value |
|---|---|
| Technique, kind | `pbt`, `module`; `ignore = ["unbound", "exotic"]`, temporal forms weighted ×16 |
| Generator | as corpus87: `max_steps = 5`, `max_variables = 3`, `integer_literals = "boundary"`, `max_collection_size = 8` |
| Checkers | `tlc` (1 worker) and `generic`; no `apalache`, no `tlcn` |
| Known-defect quarantine | none (`known_defects = []`): neither `all-defects.toml` nor `manyan-defects.toml` |
| TLC | `tools/tla2tools.jar`, tlaplus/tlaplus `bc1b506` (`bc1b506bdcad80bb518bf3776a9161c34ff0fc4c`, tla2tools 2026-11-19) |
| Manyan | `tools/manyan-a9ca4f38028f`, soasme/manyan `a9ca4f38028f4e8b4cc3f016167d071b55e3abe0` (`v0.17.0`, 2026-11-24), an x86-64 build; arguments `-workers 2 -deadlock -config {config} {module}`, 16 concurrent invocations |
| Apalache | not run; the corpus holds the Apalache 0.63.0 JAR only because `init` copies it |
| FuzzTLA | `fa5b6c03`, the JAR that ran the corpus; its main code equals `ba2e1e4c`, at which the entries were rendered, matched and triaged |
| Run | 2026-11-24 21:45 to 2026-11-25 00:28 UTC, until `max_entries = 250000`; timeouts 30 s per run for both tools |

| Directory | Entries | Classified by the triager | `NEW` after triage |
|---|---:|---:|---:|
| `01parser-fail` | 82 | not read | — |
| `02tlc-crash` | 10,341 | 4,755 (3 timeouts) | 5,586 |
| `02tlc-fail` | 2,522 | 2,520 | 2 |
| `02gen-crash` | 7,910 | 2,714 (10 timeouts) | 5,196 |
| `02gen-fail` | 4,822 | 0 | 4,822 |
| `03aggregator-fail` | 2,825 | 160 | 2,665 |
| `02gen-counterexample`, `02gen-pass` | 176, 54 | — | — |
| `02tlc-counterexample`, `02tlc-pass` | 54, 45 | — | — |

An entry appears in a `02tlc-*` and a `02gen-*` directory when one tool crashed: the
aggregator collects only entries in which both tools completed or failed. The other
234,131 entries are quality-gate results. The 15,787 failing entries split by outcome pair:

| TLC | Manyan | Entries |
|---|---|---:|
| crash | crash | 5,289 |
| crash | fail | 4,822 |
| fail | crash | 2,522 |
| counterexample | fail | 1,534 |
| pass | fail | 1,130 |
| crash | counterexample | 176 |
| fail | counterexample | 148 |
| crash | pass | 54 |
| counterexample | crash | 54 |
| pass | crash | 45 |
| fail | pass | 13 |

The generic stage records a failure's exit status but not Manyan's output, so the triager
labels every `02gen-fail` entry `NEW`. Every failing entry was therefore rendered and
rerun on Manyan, see [Method](#method).

## 1. Manyan

### 1.1. New findings

| Finding | Entries | TLC on these entries | Manyan |
|---|---:|---|---|
| [`manyan-010`](../../findings/manyan/manyan-010.md) | 32 | 3 pass, 2 counterexample, 23 evaluation error, 4 other crash | exit 255, "TLC cannot handle the temporal formula" |
| [`manyan-011`](../../findings/manyan/manyan-011.md) | 1 | counterexample | exit 75, "the identifier var0 is either undefined" |
| [`manyan-012`](../../findings/manyan/manyan-012.md) | 2 | evaluation error, exit 75 | invariant violated |
| [`manyan-013`](../../findings/manyan/manyan-013.md) | 5 | evaluation error within 4 s | timeout at 30 s |
| [`manyan-014`](../../findings/manyan/manyan-014.md) | 1 | pass in 1.3 s | timeout at 30 s |

- **`manyan-010`.** Manyan printed "TLC cannot handle the temporal formula" on 183
  entries. On 151 TLC printed it too. The other 32 properties all contain a `LET` around
  a temporal operand, as in `(LET LocalOp16(p) == p IN []FALSE) ~> FALSE`.
- **`manyan-011`.** In `b7a06b95` the invariant is
  `ENABLED ApaFoldSet(Lambda6, (var0' = var0), {"default_OF_MODEL"})`. The reduction
  shows that any operator argument applied to a primed expression fails, in the
  next-state relation as well.
- **`manyan-012`.** In `249ac0cb` and `fa984c7e` a constant invariant builds
  `Variant("Tag6", (CHOOSE b \in S : FALSE)[k])`, a function constructor, and never
  applies it.
- **`manyan-013`.** Manyan timed out on 10 entries. Five are this defect: their stacks,
  taken with `gdb`, are in `pretty`, `print_value` and `realize_lambda`. Rerun without a
  limit, `d0d7abb1` reached 18.7 GB in 40 s. Four of the other five also time out or
  exhaust the heap in TLC: `\E p \in 1..var0` with `var0` near 2^30 or 46,341 enumerates
  that many successors. The tenth is `manyan-014`.

### 1.2. Known findings

| Finding | Entries | Outcome | How attributed |
|---|---:|---|---|
| [`manyan-001`](../../findings/manyan/manyan-001.md) | 5,402 | exit 150, `Encountered "FALSE": expected a subscript` (one `TRUE`); TLC: 3,726 crash, 1,080 counterexample, 596 pass | rerun message |
| [`manyan-005`](../../findings/manyan/manyan-005.md) | 2,360 | exit 255 for a domain error in the next-state relation; TLC: 2,181 exit 75 with the same message | triager |
| [`manyan-003`](../../findings/manyan/manyan-003.md) | 703 | exit 77, "Temporal formula is a tautology"; TLC: 409 pass, 272 counterexample, 22 crash | rerun message |
| [`manyan-002`](../../findings/manyan/manyan-002.md) | 352 | exit 150, "Label labelN must contain formal parameter `mappedN'" (17 `argN`); TLC: 131 counterexample, 111 pass, 110 crash | rerun message, label checked in samples |
| [`manyan-006`](../../findings/manyan/manyan-006.md) | 141 | SIGABRT, exit 134 | triager |
| [`manyan-008`](../../findings/manyan/manyan-008.md) | 113 + 2 | exit 255, "the identifier step is either undefined" | triager; 2 more print the `CHOOSE` error that the early evaluation meets first |
| [`manyan-007`](../../findings/manyan/manyan-007.md) | 90 | SIGSEGV, exit 139 | triager |
| [`manyan-004`](../../findings/manyan/manyan-004.md) | 74 | domain error that TLC does not report; TLC: 50 counterexample, 24 pass | rerun message, `EXCEPT` applied at once |
| [`manyan-009`](../../findings/manyan/manyan-009.md) | 39 | exit 255, "must be of forms" on `[]WF_v(A)` or `[]SF_v(A)` | rerun message and shape |

The SIGABRT and SIGSEGV crashes reproduce on the aarch64 build of the same revision.
`manyan-004` occurs in invariants (exit 75), in the
next-state relation (exit 255, as in `manyan-005`) and in properties. The tautology of
`manyan-003` hides the rest of the check: in the 272 entries where TLC reports a
counterexample, the counterexample is usually an invariant violation that Manyan never
reaches, as in `00e81bd4` with `Prop == FALSE ~> var0` and `Inv == var0`.

### 1.3. Differences not filed

These entries disagree with TLC, but Manyan's result is allowed by the semantics of
TLA+, or the tools only check in a different order.

| Count | Cause | Example |
|---:|---|---|
| 152 | TLC rejects a constant property ([constant-property-tlc-rejects](../../conformance/constant-property-tlc-rejects.md)); Manyan checks it. In the 10 Manyan passes, `Init` has no states | `43f87de8` |
| 18 | TLC crashes on an evaluation error while checking liveness ([`tlc-010`](../../findings/TLC/tlc-010.md)), and Manyan completes. In `fd2687b5`, reduced, TLC evaluates the state predicate of an eventuality in states after it has held, and Manyan does not: `<>((CASE x <= -x -> 1) >= 1)` holds at `x = 0` and has no arm at `x = 1`; TLC exits 255, Manyan 0. The other 17 were not reduced | `fd2687b5` |
| 4 | TLC crashes on `WF_v(A)` whose action leaves a variable of `v` unprimed (`tlc-010`); Manyan checks the fairness | `ccc867f0` |
| 9 | Check order. TLC reports a violation, and Manyan an evaluation error in a property or the next-state relation that the same behaviors reach. In `0a4ad486`, `19ca32fd` and `6c783690`, TLC reports that error too once the invariant is removed | `0a4ad486`, `1441ccaf` |
| 1 | `P ~> TRUE` with an erroneous `P`: Manyan simplifies the property and does not evaluate `P`; TLC fails | `2e4bff61` |
| 1 | A set map `{e : a \in {}, b \in S}` where `S` fails: Manyan does not evaluate `S` after an empty first bound; TLC does | `b3d6f4d3` |
| 1 | `(\E q \in {} : P) ~> [][A]_v`: TLC expands the empty quantifier to `FALSE` and drops the property; Manyan rejects `[][A]_v` under `~>`, as TLC does for a non-empty domain | `7893d224` |
| 1 | Manyan evaluates a domain error in a temporal subformula when it builds the tableau; TLC never needs that subformula | `79a41f31` |

In 36 more entries both tools report an evaluation error. In 15 it is the same error,
which Manyan reports on an initial state with exit 255 and TLC with 75, the exit status of
`manyan-005` outside the next-state relation. In 21 the errors differ, because the tools
evaluate the conjuncts or the states in a different order.

## 2. TLC

### 2.1. Crashes

| Count | Cause | Document |
|---:|---|---|
| 5,574 | "must be of forms" (4,158) or "cannot handle" (1,416), every one matched by a signature of `all-defects.toml` that references the report | [tlc-temporal-formula-limits](../../conformance/tlc-temporal-formula-limits.md) |
| 2,550 | evaluation error while checking liveness, escaped as error 1000 | [`tlc-010`](../../findings/TLC/tlc-010.md) |
| 1,596 | evaluation error in a constant eventuality, escaped as error 1000 | [`tlc-009`](../../findings/TLC/tlc-009.md) |
| 604 | a standard-module error that exits 255 | [`tlc-002`](../../findings/TLC/tlc-002.md) |
| 10 | "must be of forms" on `label :: [][A]_v`, no limits signature | [`tlc-013`](../../findings/TLC/tlc-013.md) |
| 3 | timeout at 30 s in next-state generation; `\E p \in 1..var0` with `var0 = 46341` | none, a large state space |
| 2 | stack overflow in a fold | [`tlc-performance-001`](../../findings/tlc-performance/tlc-performance-001.md) |
| 1 | `a2a1cd94`: a `CASE` without a matching arm in a lazy initial-state value, after a warning, escaped as error 1000 | [`tlc-001`](../../findings/TLC/tlc-001.md) |
| 1 | `d5482583`: heap exhausted by `\E p \in 1..var0` with `var0 = 2^30`; Manyan times out | none, a large state space |

Manyan's outcomes on these entries:
- 5,289 crashes, 5,051 of them with TLC's diagnostic;
- 3,836 parse errors of `manyan-001` and `manyan-002`;
- 986 failures with exit 75, 76 or 77;
- 230 completed checks.

Of the 986 failures:
- in 589, TLC rejects the temporal formula, and Manyan evaluates it and reports an
  evaluation error (557), an unexpected exception (17) or a tautology (15);
- in 354, Manyan reports with exit 75 the evaluation error that TLC escapes with exit
  255: 299 of `tlc-010` and 55 of `tlc-009`;
- in 40, Manyan reports a `tlc-002` error with exit 76, or a tautology;
- 3 are `tlc-013` or `tlc-performance-001`.

The 230 completed checks:
- 201 are equivalences of temporal formulas, which TLC cannot handle. Manyan's verdicts
  agree with TLC's verdict on the expansion `(A => B) /\ (B => A)` in the eight shapes
  that were tried;
- 27 are entries of `tlc-010` (22), `tlc-002` (3) and `tlc-009` (2);
- 1 is the `tlc-013` label, which Manyan passes correctly;
- 1, `bf218147`, has a temporal formula under a bounded quantifier.

### 2.2. Failures

| Count | Cause | Document |
|---:|---|---|
| 2,304 | function application outside its domain | [function-application-outside-domain](../../conformance/function-application-outside-domain.md) |
| 162 | constant property | [constant-property-tlc-rejects](../../conformance/constant-property-tlc-rejects.md) |
| 112 | `CHOOSE` without a witness | [choose-without-witness](../../conformance/choose-without-witness.md) |
| 45 | `CASE` without a matching arm | [case-without-matching-arm](../../conformance/case-without-matching-arm.md) |
| 21 | integer outside TLC's range | [integer-outside-tlc-range](../../conformance/integer-outside-tlc-range.md) |
| 18, 6, 4, 2, 2, 1, 1 | `Head` of `<<>>`, `SubSeq`, division by 0, `0^0`, constant false invariant, non-enumerable `Init`, `Tail` of `<<>>` | the corresponding conformance reports |
| 3 | "the identifier step is either undefined", exit 150: `*` of `step` in an operator argument of `ApaFoldSet` under a temporal operator | [`tlc-015`](../../findings/TLC/tlc-015.md) |
| 1 | `8ab93e07`: the `WF_v(A)` message of `ENABLED UNCHANGED e` | [`tlc-012`](../../findings/TLC/tlc-012.md) |
| 1 | `c3d7c763`: `ENABLED (CHOOSE b \in (... {var1' = ...} ...) : FALSE)` in a property, exit 151; Manyan fails the same way when the invariant holds | the `ENABLED` class of the corpus88 report, §1.3 |

The triager labels the `tlc-015` entries `NEW` or `apalache-printer-008`, and `c3d7c763`
`NEW`: it has no aggregator signature for "identifier is either undefined".

### 2.3. Parser failures

All 82 are SANY semantic errors of the FuzzTLA generator, not of either checker. 79 are
label errors ("must contain formal parameter") that match `printer-nested-case-arm`
([`apalache-printer-009`](../../findings/apalache-printer/apalache-printer-009.md)).
The other 3 are level errors of `ApaFoldSet` or `ApaFoldSeqLeft`, as in corpus85 and
corpus88.

## Follow-ups

- **Store the generic checker's output on failure.** The `02gen-fail` entries and the
  aggregator's `generic` results carry an exit status but no detail. Triage had to rerun
  7,877 entries to read Manyan's messages, and the triager cannot classify them.
- **Triager rules.** The triager has no rule for these:
  - `manyan-001`, `manyan-002`, `manyan-003`, `manyan-004` and `manyan-009` (once the
    detail is stored);
  - `tlc-015` in a TLC failure;
  - the `CASE` message of `tlc-001`.
- **Quarantine.** A Manyan campaign should list `manyan-defects.toml` and
  `all-defects.toml`, as the generic-checker manual says. Without them, 34% of the
  failing entries of this corpus are `manyan-001`.
- **Signatures for `manyan-010` and `manyan-011`.** Both are shapes. A signature for
  `manyan-010` would match a `LET` with a temporal body under a temporal operator. Its
  precision needs measuring, because TLC rejects many such properties for other reasons.

## Method

- **Triage.** `script/triager.py` classified every crash, failure and aggregator entry.
- **Rendering.** All 15,787 failing entries were decoded and rendered in one JVM per shard
  with the FuzzTLA JAR that ran the corpus. Each was written as `FuzzInput.tla` and
  `FuzzInput.cfg`, with `Apalache.tla` and `Variants.tla`, as the generic stage writes
  them.
- **Matching.** The same JVMs matched every entry against `signatures/all-defects.toml`
  and `signatures/manyan-defects.toml`.
- **Manyan reruns.** All 15,787 entries were rerun with Manyan built from the same
  revision with GCC 13.3.0 on aarch64 Linux (`make manyan-build`). The command line was
  the corpus's, `-workers 2 -deadlock`, with a 30 s limit. Every stored verdict and exit
  status reproduced: the generic stage stores 76 and 77 as failure code 75. Of the 10
  stored timeouts, 5 timed out again and 5 were killed for exhausting memory.
- **TLC reruns.** Entries that a classification depended on were rerun with the corpus's
  `tla2tools.jar` on OpenJDK 25.0.5.1, with `-workers 1 -deadlock -fp 13 -seed
  -4990515120303276481 -noGenerateSpecTE`, `-Xmx512m` and a 60 s limit.
- **Reduction.** The new findings were reduced by hand to the modules in their documents,
  and every row of their tables was run on both tools. Stacks of slow Manyan runs were
  taken by interrupting Manyan under `gdb`.
- **Attribution by shape.** `manyan-004` was attributed to entries whose module contains an
  `EXCEPT` and whose Manyan error is a domain error that TLC does not report; the three
  smallest were checked to apply an `EXCEPT` at once. `manyan-009` was attributed by a
  `[]` around `WF_` or `SF_` in the property.
