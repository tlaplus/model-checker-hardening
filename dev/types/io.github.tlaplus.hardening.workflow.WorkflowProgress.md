# `WorkflowProgress`

`io.github.tlaplus.hardening.workflow` · record · other · [source](https://github.com/tlaplus/model-checker-hardening/blob/86f4053a56eb75f027c9ac8d2b7ff84a4acf6d75/src/main/java/io/github/tlaplus/hardening/workflow/WorkflowProgress.java#L19) · module [workflow](../modules/workflow.md)

Best-effort snapshot of cumulative corpus statistics and current pending work.

```mermaid
classDiagram
  class WorkflowProgress {
    <<record>>
  }
  class CorpusStage {
    <<model>>
  }
  class CorpusEntryCounts {
    <<record>>
  }
  class GeneratorSummary {
    <<record>>
  }
  class StageVerdictSummary {
    <<record>>
  }
  class WorkflowProgressMonitor
  class RunView {
    <<view>>
  }
  class TerminalProgressDisplay {
    <<view>>
  }
  class WorkflowRunner {
    <<facade>>
  }
  WorkflowProgress --> CorpusStage : has
  WorkflowProgress --> CorpusEntryCounts : has
  WorkflowProgress --> GeneratorSummary : has
  WorkflowProgress --> StageVerdictSummary : has
  WorkflowProgressMonitor --> WorkflowProgress : has
  RunView ..> WorkflowProgress : uses
  TerminalProgressDisplay ..> WorkflowProgress : uses
  WorkflowRunner ..> WorkflowProgress : uses
```

The diagram draws 8 of 11 relations; the table lists them all.

| Direction | Relation | Type | Summary |
|---|---|---|---|
| → | has | [CorpusStage](io.github.tlaplus.hardening.corpus.CorpusStage.md) | One implemented stage of the fuzzing pipeline, and the corpus directories it owns. |
| → | has | [CorpusEntryCounts](io.github.tlaplus.hardening.workflow.CorpusEntryCounts.md) | How many entries a corpus holds, split the way `workflow.max_entries` counts them (ADR 0034 §6): `sized` entries share the generations' sizes and are bounded by `maximum`, while `replays` come on top of them and are not. |
| → | has | [GeneratorSummary](io.github.tlaplus.hardening.workflow.execution.GeneratorSummary.md) | Cumulative generation statistics decorated with invocation-facing replay and timing fields. |
| → | has | [StageVerdictSummary](io.github.tlaplus.hardening.workflow.execution.StageVerdictSummary.md) | Cumulative verdict counters and summed worker elapsed time for one stage. |
| ← | has | [WorkflowProgressMonitor](io.github.tlaplus.hardening.workflow.execution.WorkflowProgressMonitor.md) | Periodically publishes serialized workflow snapshots without delaying stage workers. |
| ← | uses | [RunView](io.github.tlaplus.hardening.cli.RunView.md) | The statistics shared by live and final reports, without layout or terminal state. |
| ← | uses | [TerminalProgressDisplay](io.github.tlaplus.hardening.cli.TerminalProgressDisplay.md) | Terminal mechanics of live progress: snapshots, change highlighting, resizing, and screen restoration. |
| ← | uses | [WorkflowRunner](io.github.tlaplus.hardening.workflow.WorkflowRunner.md) | Runs generation, parsing, TLC, Apalache, conformance aggregation and the quality gate under one CPU budget. |
| → | depends | [EnumMaps](io.github.tlaplus.hardening.common.EnumMaps.md) | Validated immutable copies of maps keyed by an enum, iterated in declaration order. |
| → | depends | [Preconditions](io.github.tlaplus.hardening.common.Preconditions.md) | Shared validation of method and constructor arguments. |
| ← | depends | [RunOutput](io.github.tlaplus.hardening.cli.RunOutput.md) | The output of one run: the command's configured writer and an optional live display in front of it. |
