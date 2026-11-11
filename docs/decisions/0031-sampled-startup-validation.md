# 0031: Sampled validation of retired entries at startup

**Authors:** Igor Konnov and Claude

**Status:** Implemented

**Date:** 2026-11-10

**Implementation.** `CorpusStage.PipelineRole` names the verdicts that retire an entry,
and `CorpusEnvelope.isRetired` applies them. `workflow.RetiredEntrySample` decides which retired
entries `WorkflowRunner` decodes at startup, and `fuzztla run --validate-retired=FRACTION` sets
the fraction.

## Context

Before a run starts its stages, `CorpusDirectory.recoverAndValidate` checks every entry in
two ways:

1. **Consistency.** The file name is the digest of the input, the envelope decodes, the
   recorded verdicts match the entry's directory, and checker copies agree. This reads the
   file and costs little.
2. **Decodability.** `WorkflowRunner`'s validator decodes the stored input into a
   specification with this build's decoders. It fails when the generator, the rewrite rules
   or the libraries no longer accept an input. For an `mt` corpus, the decode replays the
   rewrites.

No earlier ADR states why every entry must pass check 2. The scan was single-threaded until
commit `d0f40947`, which runs it on `--max-cpus` threads.

We measured `run --continue` on a copy of corpus78, an `mt` corpus with TLC and TLC-n that
had already reached `max_entries = 100000` (FuzzTLA `d0f40947`, TLC `d8fb231`, Apalache
0.63.0, not run). The run did nothing but startup and the final pass:

- **On 7 threads:** 50 min 19 s wall clock and 17,365 s of CPU time for 98,835 entries,
  about 175 ms of CPU per entry. Almost all of it is check 2.
- **On 1 thread (before `d0f40947`):** still in the scan after 61 min, when we stopped it.
  At the measured CPU time, the whole scan takes 4 to 5 hours.
- **Retired entries dominate.** 94,914 of the 98,835 entries are in `04quality-fail`,
  and 37 in `01parser-fail`; the checker crash directories hold 4,494 copies. 3,921 entries
  are in `04quality-pass`.

Most of check 2 protects nothing. An entry in `04quality-fail` is never decoded again by
`run`: no stage reads it, and the gate selects parents only from `04quality-pass`. Decoding
it at startup only tells us that the build has changed, and a sample tells us that too.

## Decision

### 1. Retired entries

An entry is *retired* when a stage recorded a verdict after which no stage of any run reads
the entry's input again:

| Stage | Retiring verdicts | Why the others are live |
|---|---|---|
| parser | fail, crash | A pass fans out to the checkers. |
| each checker | crash | A pass, counterexample or fail waits for the aggregator. A crash pair is never aggregated. |
| aggregator | fail (differ) | A pass waits for the quality gate. |
| quality | fail (drop) | A pass is a parent of later mutants. |

The retiring verdicts follow from a stage's role, so each `CorpusStage.PipelineRole`
constant states them, and `CorpusEnvelope.isRetired` reports whether any recorded verdict retires the entry. This depends only on the envelope,
which check 1 matches against the directory.

A checker copy whose partner crashed stays in its pass, counterexample or fail directory and
is never aggregated. It counts as live, which is conservative.

### 2. Startup decodes every live entry and a sample of the retired ones

`WorkflowRunner`'s validator still runs check 1 and the generation check on every entry. It
decodes every live entry, and a retired entry only when `RetiredEntrySample` selects it.

- **Fraction.** `fuzztla run --validate-retired=FRACTION` sets the expected share of retired
  entries that are decoded, in [0, 1]. The default is 0.01. With 1, every entry is decoded,
  as before this ADR; with 0, no retired entry is.
- **Selection.** An entry is selected when a uniform draw from its input digest and a salt
  falls below the fraction. Whether one entry is decoded doesn't depend on the order of the
  scan or on the thread count.
- **Salt.** Each invocation draws a fresh salt, so successive `run --continue` invocations
  check different entries. The salt is not one of the corpus `[seeds]`. Selection decides
  only whether startup fails; it never changes what the run writes. A fresh corpus has no
  entries to sample, so ADR 0026 is unaffected.

A run option rather than a configuration key: the fraction changes how much one invocation
checks, not what the campaign generates. This is the same reason `--max-cpus` is an option.

### 3. What still fails startup

A failure to decode a selected or live entry fails startup as before, and a generator crash
is still recorded under `.work/generator-crash`. Check 1 is still applied to every entry.

## Consequences

- **Startup cost.** On corpus78, startup decodes 3,921 live and about 990 retired entries
  instead of 98,835. The run of the Context takes 3 min 9 s wall clock and 653 s of CPU time
  on 7 threads, against 50 min 19 s and 17,365 s.
- **Detection.** A build change that breaks a share p of the retired entries is detected
  with probability 1 − (1 − p)^n, where n is the number of sampled entries. On corpus78
  (n ≈ 990), p = 1% is detected almost surely, and p = 0.1% about 63% of the time. A miss
  does not affect the run. It shows later, when `print`, `shrink` or `export-db` decodes such
  an entry.
- **Startup varies between invocations.** Two invocations on the same corpus may decode
  different retired entries, and only one of them may fail. Pass `--validate-retired=1` to
  check every entry, for example after upgrading the generator.
