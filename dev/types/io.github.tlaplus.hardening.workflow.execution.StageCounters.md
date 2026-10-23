# `StageCounters`

`io.github.tlaplus.hardening.workflow.execution` · class · other · [source](https://github.com/tlaplus/model-checker-hardening/blob/47a7fcfd71d20c6a404ebf5a8a3e7d9a0e250e9b/src/main/java/io/github/tlaplus/hardening/workflow/execution/StageCounters.java#L14) · module [workflow](../modules/workflow.md)

Verdict counters for one stage, seeded from the corpus and updated by its workers.

```mermaid
classDiagram
  class StageCounters
  class CorpusVerdict {
    <<model>>
  }
  class ElapsedTimeAccumulator
  class StageGraph {
    <<process>>
  }
  class AggregatorStage {
    <<process>>
  }
  class StageJobLoop
  class QualityGate {
    <<policy>>
  }
  class ToolStage {
    <<process>>
  }
  class StageVerdictSummary {
    <<record>>
  }
  StageCounters --> CorpusVerdict : has
  StageCounters --> ElapsedTimeAccumulator : has
  StageGraph --> StageCounters : has
  AggregatorStage --> StageCounters : has
  StageJobLoop --> StageCounters : has
  QualityGate --> StageCounters : has
  ToolStage --> StageCounters : has
  StageCounters ..> StageVerdictSummary : uses
```

The diagram draws 8 of 10 relations; the table lists them all.

| Direction | Relation | Type | Summary |
|---|---|---|---|
| → | has | [CorpusVerdict](io.github.tlaplus.hardening.corpus.CorpusVerdict.md) | A verdict supported by the implemented corpus stages. |
| → | has | [ElapsedTimeAccumulator](io.github.tlaplus.hardening.workflow.execution.ElapsedTimeAccumulator.md) | Accumulates active intervals across workers using a monotonic clock. |
| ← | has | [StageGraph](io.github.tlaplus.hardening.workflow.StageGraph.md) | The stages that run one workflow invocation, wired to their queues, result capacities, and shared collaborators, and the order in which they start and stop. |
| ← | has | [AggregatorStage](io.github.tlaplus.hardening.workflow.aggregator.AggregatorStage.md) | Joins completed TLC and Apalache results with one in-process worker. |
| ← | has | [StageJobLoop](io.github.tlaplus.hardening.workflow.execution.StageJobLoop.md) | Claims queued items on behalf of one stage worker under the shared CPU budget. |
| ← | has | [QualityGate](io.github.tlaplus.hardening.workflow.quality.QualityGate.md) | Selects the parents of the next generation from one settled generation (ADR 0010, ADR 0013). |
| ← | has | [ToolStage](io.github.tlaplus.hardening.workflow.tool.ToolStage.md) | One stage that runs an external tool over corpus entries: the parser, TLC, or Apalache. |
| → | uses | [StageVerdictSummary](io.github.tlaplus.hardening.workflow.execution.StageVerdictSummary.md) | Cumulative verdict counters and summed worker elapsed time for one stage. |
| → | depends | [StageEntryCounts](io.github.tlaplus.hardening.corpus.StageEntryCounts.md) | How many entries one stage has produced under each verdict. |
| ← | depends | [GenerationLoop](io.github.tlaplus.hardening.workflow.GenerationLoop.md) | Runs the generations of one workflow invocation over a single stage graph (ADR 0010). |
