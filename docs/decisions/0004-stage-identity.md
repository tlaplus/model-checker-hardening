# 0004: Stage identity

**Author:** Claude Opus 5

**Status:** Accepted

**Date:** 2026-08-18

## Context

[ADR 0001][] defines five concurrently running stages and gives each one its own
corpus directories. The code named those stages by repeating them: the parser,
TLC, and Apalache appeared as separate fields, accessors, or triplicated
expressions in the corpus inventory, the durable statistics aggregate and its
codec, the workflow metrics, the progress and result records, the run table, and
the configuration records.

[`fuzzing-workflows.md`][workflows] describes an aggregator, a quality gate, and
a mutator as future stages. Adding any of them meant editing roughly a dozen
types that had no reason to know how many stages exist.

## Decision

`CorpusStage` is the identity of a pipeline stage. It is a public enum in the
`corpus` package that owns each stage's metadata name, display name, optional
input and scratch directories, result directories and supported verdicts. It
also states whether a passed entry stays in the stage's result directory or fans
out downstream, whether configuration bounds its result occupancy, and which
failure metadata this build writes.

Everything that holds per-stage data is keyed by it:

- `CorpusInventory` maps each stage to its pending inputs and its verdict counts.
- `CorpusRunStatistics` maps each stage to its cumulative elapsed nanoseconds, and
  stores them under `elapsedNs.stages` keyed by metadata name. A stage the reader
  does not know is ignored; a stage the document omits contributes zero.
- `StageClocks` holds one elapsed-time accumulator per stage, and
  `GeneratorStatistics` holds the generator's counters and clock. `WorkflowMetrics`
  composes the two.
- `WorkflowProgress` and `WorkflowRunSummary` map each stage to what it produced
  and, for progress, to its current backlog. `RunTable` renders whatever the maps
  contain.
- `WorkflowConfig` maps each configured checker stage to a `CheckerStageConfig`,
  matching the `[workflow.<stage>]` tables of the configuration file. The
  fixed, single-worker aggregator uses the global corpus limit and has no
  configuration table.

The input stage has no `CorpusStage`. It produces corpus entries rather than
recording verdicts on them, so its statistics live with the generator.

Progress reports a stage's backlog from its queue's current size rather than by
subtracting counters that were sampled at different moments.

## Consequences

Adding a pipeline stage means adding an enum constant with its topology and the
stage implementation, plus configuration only when the stage has configurable
policy. The inventory, the statistics
aggregate and its codec, the clocks, the progress snapshot, and the run table
follow without modification.

The durable `.workflow-stats.cbor` layout changed. Per this repository's policy,
formats change without migration; a corpus written by an older build reports
`elapsedNs.stages` as missing and is rejected as invalid workflow statistics.

Keying by a corpus concept means `config` now depends on `corpus`. The dependency
is one-way and matches the configuration file, whose tables are named after the
stages.

[ADR 0001]: 0001-stages-and-workers.md
[workflows]: ../architecture/fuzzing-workflows.md
