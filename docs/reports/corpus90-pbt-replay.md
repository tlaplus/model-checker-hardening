# corpus90: the first campaign with counterexample replay

This report records what corpus90 found. corpus90 is a `pbt` campaign of `module` entries
that checks TLC `bc1b506` against Apalache 0.63.0. It is the first campaign that replays
the counterexamples it records ([ADR 0034](../decisions/0034-counterexample-replay.md)):
every trace that TLC or Apalache reports becomes an input of the next generation, and both
checkers must confirm it. The report covers:
1. the replays that a checker refuted, new causes and known;
2. the defects of FuzzTLA's replay that the campaign exposed, fixed with this report;
3. the other failures, and whether any of them is new.

"Known" means recorded in [`findings/`](../../findings/) or
[`conformance/`](../../conformance/) at FuzzTLA `218ef666`. Corpus entries are cited by
the first 12 hex digits of their digest.

## Summary

- **Replay works.** Of 49,890 replays, 33,204 were confirmed by both checkers. Another
  14,124 failed only because TLC rejects a partial term on Apalache's trace, which is the
  parent's disagreement again, as ADR 0034 §4 expects.
- **TLC, new.** [`tlc-024`](../../findings/TLC/tlc-024.md), 6 replays: an evaluation
  error in another branch of the violating action cuts TLC's trace of an invariant
  violation short. TLC prints a trace whose last state satisfies the invariant, and still
  exits 12. This is [tlaplus/tlaplus#810](https://github.com/tlaplus/tlaplus/issues/810);
  the finding adds the cause.
- **Apalache, new.** [`apalache-bmc-025`](../../findings/apalache-bmc/apalache-bmc-025.md):
  a sequence applied past its end is free when the index was computed, and the fixed
  default value when the index is a literal or a cached constant. Apalache refutes its own
  counterexamples once the replay assigns the index a literal. Apalache refuted 179 of its
  own replays; making the index a fresh cell made it confirm 56 of them. This is the case
  that ADR 0034's probes left open.
- **FuzzTLA, fixed.** Two defects of the replay itself, 26 replays:
  - `TlcTraceRecorder` joined an invariant trace with the trace of a property that TLC
    reports after it (7 replays);
  - the replay module duplicated the labels of an `UNCHANGED` expression, which SANY
    rejects (19 replays).
- **Known.** The other refuted replays have known causes: order-sensitive folds,
  [`choose-multiple-witnesses`](../../conformance/choose-multiple-witnesses.md),
  [`apalache-bmc-019`](../../findings/apalache-bmc/apalache-bmc-019.md),
  [`apalache-bmc-021`](../../findings/apalache-bmc/apalache-bmc-021.md),
  [`apalache-bmc-023`](../../findings/apalache-bmc/apalache-bmc-023.md) and
  [`apalache-bmc-024`](../../findings/apalache-bmc/apalache-bmc-024.md). Some are
  attributed by shape only; see [§1](#1-refuted-replays).
- **No other new defect.** The 41 verdict disagreements are order-sensitive folds (39) and
  `apalache-bmc-019` (2). The TLC crashes and failures that the triager left `NEW` are
  TLC's temporal-formula limits, `tlc-015`, `tlc-performance-001` and `tlc-004`, except
  one entry that this report leaves open ([§4.3](#43-tlc-failures)).

## Setup

| Item | Value |
|---|---|
| Technique, kind | `pbt`, `module`; `ignore = ["unbound", "exotic"]`, temporal forms weighted ×16 |
| Generator | as corpus87: `max_steps = 5`, `max_variables = 3`, `integer_literals = "boundary"`, `max_collection_size = 8`, the recursion library |
| Checkers | `tlc` (1 worker) and `apalache` (16 workers); no `tlcn`, no `generic` |
| Replay | `[workflow] replay = true` |
| Known-defect quarantine | none (`known_defects = []`) |
| TLC | `tools/tla2tools.jar`, tlaplus/tlaplus `bc1b506` (`bc1b506bdcad80bb518bf3776a9161c34ff0fc4c`, tla2tools 2026.11.19) |
| Apalache | `tools/apalache.jar`, 0.63.0 (`67b8ec6`); the root causes cite the source at `a2910d423` (`v0.63.0-2-ga2910d423`) |
| FuzzTLA | a build with replay (PRs #409 to #415); the corpus does not record its commit. The entries were rendered, rerun and triaged at `218ef666` |
| Run | 2026-11-25 20:27 to 2026-11-26 08:12 UTC, 91 generations; stopped at 90,500 of `max_entries = 100000`; timeouts 30 s per run |

The run stopped before Apalache drained its queue: 1,323 entries, 313 of them replays,
are still in `02apa-inputs`.

| Directory | Entries | Of them replays | Classified by the triager | `NEW` after triage |
|---|---:|---:|---:|---:|
| `01parser-fail` | 36 | 19 | not read | — |
| `02tlc-crash` | 4,359 | 74 | 2,232 | 2,127 |
| `02tlc-fail` | 12,373 | 474 | 12,368 | 5 |
| `02apa-crash` | 15,272 | 1,389 | 15,272 (12,135 timeouts) | 0 |
| `02apa-fail` | 955 | — | 955 | 0 |
| `03aggregator-fail` | 46,879 | 14,891 | 31,947 | 41, and 14,891 `REPLAY` |
| `03aggregator-pass`, `04quality-*` | 488, 73,158 | 225, 32,979 | — | — |

A replay that a checker crashed on is not aggregated and stays in a `02*` directory. The
aggregator failures that are not replays split by outcome pair:

| TLC | Apalache | Entries |
|---|---|---:|
| fail | counterexample | 21,410 |
| fail | pass | 8,475 |
| counterexample | fail | 1,478 |
| pass | fail | 584 |
| counterexample | pass | 27 |
| pass | counterexample | 14 |

## 1. Refuted replays

A replay predicts a counterexample of exactly the recorded length (ADR 0034 §4). The
triager files every replay that fails this prediction under `REPLAY`. The table pairs
each of the 14,891 with its parent and with the checker that recorded its trace. "Pass"
means that the checker refuted the trace.

| Recorded by | TLC on the replay | Apalache on the replay | Replays | Cause |
|---|---|---|---:|---|
| Apalache | fail | counterexample | 14,124 | TLC's partial terms on Apalache's trace, as on the parent |
| TLC | counterexample | fail | 459 | Apalache's `ENABLED`, `Seq` and `STRING` limits, as on the parent |
| Apalache | fail (172), counterexample (5) or pass (2) | pass | 179 | [§1.2](#12-apalache-refutes-its-own-trace) |
| TLC | counterexample | pass | 74 | [§1.3](#13-apalache-refutes-tlcs-trace) |
| Apalache | pass | counterexample | 42 | [§1.4](#14-tlc-refutes-apalaches-trace) |
| TLC | pass | pass | 13 | [§1.1](#11-tlc-refutes-its-own-trace) |

No replay holds a trace that both checkers recorded and a checker refuted.

In the first two rows, the replay fails on the checker that failed on the parent, at the
same constructs. Of the 459 Apalache failures, 451 read
`unsupported expression: ENABLED ...`, 5 reject `Seq(_)` and 3 reject `STRING`, which
[enabled-apalache-unsupported](../../conformance/enabled-apalache-unsupported.md),
[sequence-set-unsupported](../../conformance/sequence-set-unsupported.md) and
[string-set-unsupported](../../conformance/string-set-unsupported.md) document.

### 1.1. TLC refutes its own trace

All 13 parents end with TLC reporting a counterexample of 0 or 2 transitions, and
Apalache one of 1 transition.

| Replays | Recorded trace | Cause |
|---:|---|---|
| 7 | `[s0, s1, s0]`, ending "violates Inv" | FuzzTLA: two counterexamples joined ([§2](#2-fuzztla-defects-fixed-with-this-report)) |
| 6 | `[s0]`, ending "violates Inv", `s0` satisfies `Inv` | [`tlc-024`](../../findings/TLC/tlc-024.md) |

In the first group, TLC reports the invariant violation in state 2 and then
`Temporal property Prop was violated` with a counterexample of one state, for properties
such as `[][][]FALSE`. In the second, rerunning TLC on the parents (`7c39174f1f55`,
`722721b18185`, `ccc01e90cd0a`, `2935bffe89e1`, `92491e6da590`, `a55a23099c70`) prints
"Invariant Inv is violated", State 1 only, and an unexpected exception from another
branch of `Next`.

### 1.2. Apalache refutes its own trace

167 of the 179 parents have TLC failing on a partial term, 11 have both checkers
reporting a counterexample, and 1 has TLC passing. Each replay passes through an unspecified value: a sequence
indexed past its end, a `CHOOSE` without a witness, a `CASE` without a true guard, or a
function applied outside its domain.

| Replays | Shape | Cause | Evidence |
|---:|---|---|---|
| 56 | sequence application with a computed index | [`apalache-bmc-025`](../../findings/apalache-bmc/apalache-bmc-025.md) | confirmed: rewriting each index `i` of the replay as `(i + idx) - idx` makes Apalache confirm the trace |
| 114 | a `CHOOSE` among other partial terms | [`apalache-bmc-024`](../../findings/apalache-bmc/apalache-bmc-024.md) | by shape; 73 share one shape, likely mutants of one ancestor |
| 5 | `CASE` without `OTHER` whose guard uses `ENABLED` | [`apalache-bmc-019`](../../findings/apalache-bmc/apalache-bmc-019.md) | by shape |
| 1 | `\E` over the empty interval `6..1`, beside the stuttering disjunct | [`apalache-bmc-023`](../../findings/apalache-bmc/apalache-bmc-023.md) | traced: `d9c1230ad1ea` |
| 1 | `ApaFoldSet(LAMBDA a, e: e, FALSE, var0)` | [order-sensitive-set-fold](../../conformance/order-sensitive-set-fold.md) | traced: `5471d293aee7` |
| 2 | a function applied outside its domain, a `CASE` without `OTHER` | not analysed | `0e7eb71fac53`, `a81ec1c29618` |

`apalache-bmc-023` shows up although the interval is constant. In the parent, the `\E`
and the stuttering disjunct are two transitions, and the unsatisfiable bounds disable
only the first. The replay puts `BaseNext` into every one of its disjuncts, so the
bounds disable the stuttering step as well.

### 1.3. Apalache refutes TLC's trace

TLC confirmed its own trace in all 74. The first step that Apalache cannot take was
found by replacing `Inv` with `idx /= k` for growing `k`.

| Replays | Shape | Cause |
|---:|---|---|
| 11 | a lasso through a fold whose combinator returns `[S -> T]` | [`apalache-bmc-021`](../../findings/apalache-bmc/apalache-bmc-021.md); `5520b88c6e40` reduces to `x' \in (IF c THEN [{TRUE, FALSE} -> {1, 2, 3}] ELSE {...})` |
| 1 | a lasso through `\E` over the empty interval `SeqSum(<<1, 2, 3>>) .. 1` | [`apalache-bmc-023`](../../findings/apalache-bmc/apalache-bmc-023.md); `918ce35cd3d3` |
| 32 | an `ApaFoldSet`, and no `CASE` guarded by `ENABLED` | [order-sensitive-set-fold](../../conformance/order-sensitive-set-fold.md); traced for `228c274ca15e` and `4b1c7580c845`, the rest by shape |
| 26 | an `ApaFoldSet` and a `CASE` without `OTHER` whose guard uses `ENABLED` | order-sensitive-set-fold or [`apalache-bmc-019`](../../findings/apalache-bmc/apalache-bmc-019.md), by shape |
| 4 | a `CASE` without `OTHER` whose guard uses `ENABLED`, and no set fold | [`apalache-bmc-019`](../../findings/apalache-bmc/apalache-bmc-019.md), by shape |

27 of these parents are among the verdict disagreements of
[§3](#3-verdict-disagreements): TLC reported a violation in the initial state and
Apalache passed.

### 1.4. TLC refutes Apalache's trace

41 of the 42 replays have an `ApaFoldSet`, and TLC's fold order rejects a state of
Apalache's trace ([order-sensitive-set-fold](../../conformance/order-sensitive-set-fold.md)).
The other one, `5b57e50dfb2b`, draws its initial state from a `CHOOSE` whose predicate
holds for all three elements, and Apalache picked a different one than TLC
([choose-multiple-witnesses](../../conformance/choose-multiple-witnesses.md)).

## 2. FuzzTLA defects, fixed with this report

| Defect | Replays | Fix |
|---|---:|---|
| `TlcTraceRecorder` appended the states of every counterexample TLC printed. After an invariant violation, TLC still checks the properties on the explored states and may print a second counterexample. | 7 | Only the first counterexample is recorded. A counterexample followed by an unexpected exception (1000) is recorded without a trace, which also keeps `tlc-024`'s truncated traces out of the corpus. |
| `FuzzInputModule` writes `UNCHANGED e` as `e' = e` and copied `e` verbatim. A label in `e` appeared twice, and SANY rejected the replay with "Duplicate label". All 19 replays in `01parser-fail` failed so. | 19 | The second copy of `e` loses its labels, through a new `IrNames.eraseLabels`, which `ApalacheIrJson` uses too. |

ADR 0034 §3 and §5 record both rules.

## 3. Verdict disagreements

41 entries have one checker pass and the other report a counterexample. The triager
labels them `NEW`.

| Entries | TLC | Apalache | Cause |
|---:|---|---|---|
| 39 | counterexample (25) or pass (14) | pass (25) or counterexample (14) | [order-sensitive-set-fold](../../conformance/order-sensitive-set-fold.md) |
| 2 | counterexample | pass | [`apalache-bmc-019`](../../findings/apalache-bmc/apalache-bmc-019.md) |

All 39 have an `ApaFoldSet` whose combinator depends on the order: it returns its
element, as in `LAMBDA a, e: e`, or is a chain of implications such as
`LAMBDA a, e: (c => a) => e`. `04e41a45392e` was traced: its invariant folds
`(TRUE => a) => e` over `{TRUE, FALSE}` from `FALSE`, which gives `FALSE` in one order and
`TRUE` in the other. The other 38 are attributed by their combinators.

In `1ce37a185013` the invariant is `SeqEvenLength(SeqReverse(CASE ENABLED (...) -> ...))`.
In `62f7fd6d93a6` it is the constant `FALSE`, with an unused `LET` definition holding
`CASE ENABLED FALSE -> ...`. Apalache passes both, as `apalache-bmc-019` describes.

## 4. Other failures

### 4.1. Apalache

The triager labels every `02apa-crash` and `02apa-fail` entry. 12,135 crashes are
timeouts; corpus88 had 49,669. 1,345 of them are replays: a replay checks one path, but
it keeps the base's folds and recursion, so it is not always short. The other crashes
and the failures are known findings and conformance documents.

### 4.2. TLC crashes

Of the 2,127 crashes that the triager left `NEW`:
- 2,125 exit with "TLC cannot handle the temporal formula" (2213) or "Temporal formulas
  containing actions must be of forms <>[]A or []<>A" (2214). Every one matches a
  signature of `all-defects.toml` that references
  [tlc-temporal-formula-limits](../../conformance/tlc-temporal-formula-limits.md),
  [`tlc-013`](../../findings/TLC/tlc-013.md) or [`tlc-023`](../../findings/TLC/tlc-023.md).
- 2 are workers that ran out of their 512 MB heap. Both match the `powerset` signature,
  which references [`tlc-004`](../../findings/TLC/tlc-004.md).

The 74 TLC crashes on replays are [`tlc-002`](../../findings/TLC/tlc-002.md): an
evaluation error exits with status 255.

### 4.3. TLC failures

Of the 5 failures that the triager left `NEW`:
- 2, `0fac50c227d8` and `f6c62721f760`, are [`tlc-015`](../../findings/TLC/tlc-015.md):
  `step` under `*` in a lambda passed to `ApaFoldSet` in a temporal property, which TLC
  evaluates as a constant (error 2280).
- 2 end with a Java `StackOverflowError` at exit 75. They have the symptom of
  [`tlc-performance-001`](../../findings/tlc-performance/tlc-performance-001.md), whose
  crashes the triager labels; it does not label failures.
- 1, `36b69a571d11`, is not analysed. Its property is
  `<>[]...<>(ENABLED (CHOOSE b \in CASE step' ... : ...))`, and TLC stops at startup with
  error 2280, "the identifier step is either undefined or not an operator". It reduces to

  ```tla
  Prop == <>(ENABLED (CHOOSE b \in (CASE x' = 0 -> {TRUE} [] OTHER -> {FALSE}) : TRUE))
  ```

  for which TLC exits 151, while the same `ENABLED` as an invariant fails at run time with
  exit 75. Its operand reads `x'` before any conjunct assigns it, which TLC does not
  support in `ENABLED` (compare the last row of [`tlc-012`](../../findings/TLC/tlc-012.md)).
  Whether the startup error is a defect of its own is open.

## Method

- **Triage.** `script/triager.py` labels the crashes, failures and aggregator failures.
  It files the failing replays under `REPLAY` without reading them.
- **Replays.** A script read each `trace` entry's payload, decoded its parent's
  `stages.<checker>.trace` and compared the two, which names the checker that recorded
  the trace. It also tabulated each replay's verdicts beside its parent's.
- **Reruns.** Entries were rendered with `fuzztla print --spec` and `--apalache-ir` and
  rerun on the corpus's `tools/tla2tools.jar` and `tools/apalache.jar`, with the
  pipeline's options: for TLC `-workers 1 -deadlock -fp 88 -seed -1982044251410224109`,
  and for Apalache `check --init=Init --next=Next --inv=Inv --length=n --no-deadlock`.
  Parents with a property were rerun with `PROPERTY Prop`.
- **Bisection.** For a refuted replay, Apalache was run on the replay's JSON IR with `Inv`
  replaced by `idx /= k`, for growing `k`, to find the first transition it cannot take.
- **apalache-bmc-025.** Every sequence application `s[i]` with a non-literal `i` in the
  replay IR was rewritten to `s[(i + idx) - idx]`. Apalache then confirmed 56 of the 179
  replays it had refuted itself.
- **Shapes.** Where an entry was not traced, the operators of its IR decide the
  attribution: `ApaFoldSet`, `CHOOSE`, `CASE` without `OTHER`, `ENABLED`, `FUN_SET`.
  Such rows say "by shape".
- **Signatures.** The `NEW` TLC crashes were matched with
  `fuzztla print --known-defects signatures/all-defects.toml`.
- **Upstream.** The tlaplus/tlaplus issue tracker was searched for `tlc-024`; #810
  describes its symptom.
