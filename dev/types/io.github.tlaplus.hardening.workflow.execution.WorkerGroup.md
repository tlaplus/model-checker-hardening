# `WorkerGroup`

`io.github.tlaplus.hardening.workflow.execution` · class · process · key · [source](https://github.com/tlaplus/model-checker-hardening/blob/21262d9d0cc07b5ae4c47c4ee511217c9b88b561/src/main/java/io/github/tlaplus/hardening/workflow/execution/WorkerGroup.java#L16) · module [workflow](../modules/workflow.md)

Owns the common lifecycle of a fixed group of stage worker threads.

```mermaid
classDiagram
  class WorkerGroup {
    <<process>>
  }
  class AggregatorStage {
    <<process>>
  }
  class InputStage {
    <<process>>
  }
  class ToolStage {
    <<process>>
  }
  AggregatorStage --> WorkerGroup : has
  InputStage --> WorkerGroup : has
  ToolStage --> WorkerGroup : has
```

The diagram draws 3 of 4 relations; the table lists them all.

| Direction | Relation | Type | Summary |
|---|---|---|---|
| ← | has | [AggregatorStage](io.github.tlaplus.hardening.workflow.aggregator.AggregatorStage.md) | Joins completed TLC and Apalache results with one in-process worker. |
| ← | has | [InputStage](io.github.tlaplus.hardening.workflow.input.InputStage.md) | Concurrent input stage: admits reserved target ranges from adjacent generations. |
| ← | has | [ToolStage](io.github.tlaplus.hardening.workflow.tool.ToolStage.md) | One stage that runs an external tool over corpus entries: the parser, TLC, or Apalache. |
| → | depends | [Preconditions](io.github.tlaplus.hardening.common.Preconditions.md) | Shared validation of method and constructor arguments. |
