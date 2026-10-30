# `StageVerdictSummary`

`io.github.tlaplus.hardening.workflow.execution` · record · other · [source](https://github.com/tlaplus/model-checker-hardening/blob/b96eff75a5bbf8a60b15ad020d6e84735c75a47c/src/main/java/io/github/tlaplus/hardening/workflow/execution/StageVerdictSummary.java#L10) · module [workflow](../modules/workflow.md)

Cumulative verdict counters and summed worker elapsed time for one stage.

```mermaid
classDiagram
  class StageVerdictSummary {
    <<record>>
  }
  class StageEntryCounts {
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
  class CorpusVerdict {
    <<model>>
  }
  class StageGraph {
    <<process>>
  }
  class WorkflowRunner {
    <<facade>>
  }
  class StageCounters
  class ToolStage {
    <<process>>
  }
  StageVerdictSummary --> StageEntryCounts : has
  RunView --> StageVerdictSummary : has
  WorkflowProgress --> StageVerdictSummary : has
  WorkflowRunSummary --> StageVerdictSummary : has
  StageVerdictSummary ..> CorpusVerdict : uses
  StageGraph ..> StageVerdictSummary : uses
  WorkflowRunner ..> StageVerdictSummary : uses
  StageCounters ..> StageVerdictSummary : uses
  ToolStage ..> StageVerdictSummary : uses
```

The diagram draws 9 of 10 relations; the table lists them all.

| Direction | Relation | Type | Summary |
|---|---|---|---|
| → | has | [StageEntryCounts](io.github.tlaplus.hardening.corpus.StageEntryCounts.md) | How many entries one stage has produced under each verdict. |
| ← | has | [RunView](io.github.tlaplus.hardening.cli.RunView.md) | The statistics shared by live and final reports, without layout or terminal state. |
| ← | has | [WorkflowProgress](io.github.tlaplus.hardening.workflow.WorkflowProgress.md) | Best-effort snapshot of cumulative corpus statistics and current pending work. |
| ← | has | [WorkflowRunSummary](io.github.tlaplus.hardening.workflow.WorkflowRunSummary.md) | Cumulative corpus statistics and stopping reason after one complete workflow invocation. |
| → | uses | [CorpusVerdict](io.github.tlaplus.hardening.corpus.CorpusVerdict.md) | A verdict supported by the implemented corpus stages. |
| ← | uses | [StageGraph](io.github.tlaplus.hardening.workflow.StageGraph.md) | The stages that run one workflow invocation, wired to their queues, result capacities, and shared collaborators, and the order in which they start and stop. |
| ← | uses | [WorkflowRunner](io.github.tlaplus.hardening.workflow.WorkflowRunner.md) | Runs generation, parsing, TLC, Apalache, conformance aggregation and the quality gate under one CPU budget. |
| ← | uses | [StageCounters](io.github.tlaplus.hardening.workflow.execution.StageCounters.md) | Verdict counters for one stage, seeded from the corpus and updated by its workers. |
| ← | uses | [ToolStage](io.github.tlaplus.hardening.workflow.tool.ToolStage.md) | One stage that runs an external tool over corpus entries: the parser, TLC, or Apalache. |
| → | depends | [Preconditions](io.github.tlaplus.hardening.common.Preconditions.md) | Shared validation of method and constructor arguments. |
