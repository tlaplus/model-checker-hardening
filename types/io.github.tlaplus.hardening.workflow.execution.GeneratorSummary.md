# `GeneratorSummary`

`io.github.tlaplus.hardening.workflow.execution` · record · other · [source](https://github.com/tlaplus/model-checker-hardening/blob/47dd62c3064b76d8140ecbc017a45de80d885015/src/main/java/io/github/tlaplus/hardening/workflow/execution/GeneratorSummary.java#L9) · module [workflow](../modules/workflow.md)

Cumulative generation statistics decorated with invocation-facing replay and timing fields.

```mermaid
classDiagram
  class GeneratorSummary {
    <<record>>
  }
  class GeneratorAggregate {
    <<record>>
  }
  class RunView {
    <<view>>
  }
  class WorkflowProgress {
    <<record>>
  }
  class WorkflowRunSummary {
    <<record>>
  }
  class WorkflowRunner {
    <<facade>>
  }
  class GeneratorStatistics
  GeneratorSummary --> GeneratorAggregate : has
  RunView --> GeneratorSummary : has
  WorkflowProgress --> GeneratorSummary : has
  WorkflowRunSummary --> GeneratorSummary : has
  WorkflowRunner ..> GeneratorSummary : uses
  GeneratorStatistics ..> GeneratorSummary : uses
```

The diagram draws 6 of 8 relations; the table lists them all.

| Direction | Relation | Type | Summary |
|---|---|---|---|
| → | has | [GeneratorAggregate](io.github.tlaplus.hardening.common.GeneratorAggregate.md) | Immutable generation counters shared by persisted statistics and invocation summaries. |
| ← | has | [RunView](io.github.tlaplus.hardening.cli.RunView.md) | The statistics shared by live and final reports, without layout or terminal state. |
| ← | has | [WorkflowProgress](io.github.tlaplus.hardening.workflow.WorkflowProgress.md) | Best-effort snapshot of cumulative corpus statistics and current pending work. |
| ← | has | [WorkflowRunSummary](io.github.tlaplus.hardening.workflow.WorkflowRunSummary.md) | Cumulative corpus statistics and stopping reason after one complete workflow invocation. |
| ← | uses | [WorkflowRunner](io.github.tlaplus.hardening.workflow.WorkflowRunner.md) | Runs generation, parsing, TLC, Apalache, conformance aggregation and the quality gate under one CPU budget. |
| ← | uses | [GeneratorStatistics](io.github.tlaplus.hardening.workflow.execution.GeneratorStatistics.md) | Cumulative input-generation counters shared by the generator workers. |
| → | depends | [Preconditions](io.github.tlaplus.hardening.common.Preconditions.md) | Shared validation of method and constructor arguments. |
| ← | depends | [RunMetric](io.github.tlaplus.hardening.cli.RunMetric.md) | Stable field identity, independent of screen coordinates or formatted text. |
