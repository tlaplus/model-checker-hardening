# `WorkflowRunSummary`

`io.github.tlaplus.hardening.workflow` · record · other · [source](https://github.com/tlaplus/model-checker-hardening/blob/3df8c8564b58e946b95079b671788c8315f98831/src/main/java/io/github/tlaplus/hardening/workflow/WorkflowRunSummary.java#L14) · module [workflow](../modules/workflow.md)

Cumulative corpus statistics and stopping reason after one complete workflow invocation.

```mermaid
classDiagram
  class WorkflowRunSummary {
    <<record>>
  }
  class CorpusInventory {
    <<record>>
  }
  class CorpusStage {
    <<model>>
  }
  class GeneratorSummary {
    <<record>>
  }
  class StageVerdictSummary {
    <<record>>
  }
  class RunOutput
  class RunTable {
    <<view>>
  }
  class RunView {
    <<view>>
  }
  class GenerationLoop {
    <<process>>
  }
  class WorkflowRunner {
    <<facade>>
  }
  WorkflowRunSummary --> CorpusInventory : has
  WorkflowRunSummary --> CorpusStage : has
  WorkflowRunSummary --> GeneratorSummary : has
  WorkflowRunSummary --> StageVerdictSummary : has
  RunOutput ..> WorkflowRunSummary : uses
  RunTable ..> WorkflowRunSummary : uses
  RunView ..> WorkflowRunSummary : uses
  GenerationLoop ..> WorkflowRunSummary : uses
  WorkflowRunner ..> WorkflowRunSummary : uses
```

The diagram draws 9 of 12 relations; the table lists them all.

| Direction | Relation | Type | Summary |
|---|---|---|---|
| → | has | [CorpusInventory](io.github.tlaplus.hardening.corpus.CorpusInventory.md) | Immutable startup inventory used to seed stage queues and capacities. |
| → | has | [CorpusStage](io.github.tlaplus.hardening.corpus.CorpusStage.md) | One implemented stage of the fuzzing pipeline, and the corpus directories it owns. |
| → | has | [GeneratorSummary](io.github.tlaplus.hardening.workflow.execution.GeneratorSummary.md) | Cumulative generation statistics decorated with invocation-facing replay and timing fields. |
| → | has | [StageVerdictSummary](io.github.tlaplus.hardening.workflow.execution.StageVerdictSummary.md) | Cumulative verdict counters and summed worker elapsed time for one stage. |
| ← | uses | [RunOutput](io.github.tlaplus.hardening.cli.RunOutput.md) | The output of one run: the command's configured writer and an optional live display in front of it. |
| ← | uses | [RunTable](io.github.tlaplus.hardening.cli.RunTable.md) | Complete, exact statistics grouped by stage; suitable for scrollback and redirected output. |
| ← | uses | [RunView](io.github.tlaplus.hardening.cli.RunView.md) | The statistics shared by live and final reports, without layout or terminal state. |
| ← | uses | [GenerationLoop](io.github.tlaplus.hardening.workflow.GenerationLoop.md) | Runs the generations of one workflow invocation over a single stage graph (ADR 0010). |
| ← | uses | [WorkflowRunner](io.github.tlaplus.hardening.workflow.WorkflowRunner.md) | Runs generation, parsing, TLC, Apalache, conformance aggregation and the quality gate under one CPU budget. |
| → | depends | [EnumMaps](io.github.tlaplus.hardening.common.EnumMaps.md) | Validated immutable copies of maps keyed by an enum, iterated in declaration order. |
| → | depends | [Preconditions](io.github.tlaplus.hardening.common.Preconditions.md) | Shared validation of method and constructor arguments. |
| ← | depends | [RunCommand](io.github.tlaplus.hardening.cli.RunCommand.md) | `fuzztla run`: reads a corpus configuration and runs `WorkflowRunner` with live progress output. |
