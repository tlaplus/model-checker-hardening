# `WorkerGroup`

`io.github.tlaplus.hardening.workflow.execution` · class · process · key · [source](https://github.com/tlaplus/model-checker-hardening/blob/7b97336d8ec341ec48f89de4a368b6a488a145ac/src/main/java/io/github/tlaplus/hardening/workflow/execution/WorkerGroup.java#L16) · module [workflow](../modules/workflow.md)

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
