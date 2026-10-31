# 0026: Reproducible runs: seeds chosen at `init`, `run --continue`

**Authors:** Igor Konnov and Claude

**Status:** Implemented

**Date:** 2026-10-31

## Context

[ADR 0025][adr-0025] made a corpus hold everything that decides how its entries decode
and check. What a run *generates* still depended on the command line:

- **The seed.** `run --seed=SEED` set the base of every generator worker's stream
  ([ADR 0010][adr-0010]). Without it, every run drew a fresh seed, which only the run's
  output recorded.
- **TLC's own randomness.** Without `-fp` and `-seed`, TLC draws its fingerprint
  polynomial and the seed of its random-value operators on every check. The polynomial
  decides which distinct states collide, so it can change a distinct-state count and,
  with it, a `STATE_COUNT` triage ([ADR 0019][adr-0019]).
- **Continuation.** `run` on a corpus that holds entries continued it silently. The
  result of a continued campaign depends on where the earlier runs stopped, so it is
  not reproducible from the configuration, but nothing said so.

## Decision

### 1. `[seeds]` holds every seed, and `init` draws it

```toml
[seeds]
generator = 7921605275006395529
tlc_fp = 17
tlc_seed = -4431069563186392154
```

- `generator` is the base of the generator streams, as `--seed` was. `tlc_fp` (0–130)
  and `tlc_seed` are passed to every TLC stage as `-fp` and `-seed`. TLC and TLC-n share
  them, so both hash states with the same polynomial.
- `init` draws `generator` with `SecureRandom` and derives the TLC seeds from it
  (`config.SeedConfig.derive`). An `mt` corpus adopting a base gets its own seeds.
- The table and its keys are required. `run` has no `--seed`; to choose a seed, edit
  `[seeds]` before the first run. A configuration without the table is refused with
  `missing root keys: seeds`.

### 2. `run` runs a fresh corpus; `run --continue` extends one

- A corpus is *fresh* when no entry directory holds an entry and there is no
  `.workflow-stats.cbor`. A run that stored nothing leaves its corpus fresh.
- `run` refuses a corpus that is not fresh and names `--continue`.
- `run --continue` on such a corpus warns and appends the UTC start time to
  `.not-reproducible`. On a fresh corpus, `--continue` has no effect.
- `run` checks freshness before it starts, to report early; `WorkflowRunner` checks it
  again under the corpus lock (`workflow.RunMode`), which decides.

### 3. The report shows both

The campaign report's setup table lists `[seeds]` and the number of continued runs.

## Alternatives considered

- **Keep `run --seed` as an override.** A run with an override would not be reproducible
  from its configuration, which is the property this ADR establishes.
- **TLC seeds per checker table** (`[workflow.tlc] fp`). `CheckerStageConfig` is shared
  with Apalache, which has no such seeds, and TLC and TLC-n should use one polynomial.
- **A run counter in place of `.not-reproducible`.** Freshness already follows from the
  entries and the statistics; the mark only has to record continuations.

## Consequences

- **A fresh run is determined by its configuration** up to scheduling: the candidates of
  each target and every TLC check are fixed by `[seeds]`. Which candidates become entries
  still depends on timing between workers and on `--max-cpus`
  ([Running campaigns](../manual/running.md#reproducibility)).
- **Corpora initialized before this ADR** do not run until `[seeds]` is added, and
  since they hold entries, they also need `--continue`.
- **Growing a finished corpus** (raising `max_entries`) is a continuation and marks it.
- **Deviation.** This replaces `run --seed` of [Running campaigns](../manual/running.md)
  and extends [ADR 0025][adr-0025] §1 with the seeds.

[adr-0010]: 0010-mutation.md
[adr-0019]: 0019-state-count-agreement.md
[adr-0025]: 0025-self-contained-corpora.md
