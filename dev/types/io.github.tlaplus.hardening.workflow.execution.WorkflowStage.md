# `WorkflowStage`

`io.github.tlaplus.hardening.workflow.execution` · interface · contract · key · [source](https://github.com/tlaplus/model-checker-hardening/blob/fd29bdb142d5d87538f91aeddca09d5ac3d7d0a7/src/main/java/io/github/tlaplus/hardening/workflow/execution/WorkflowStage.java#L8) · module [workflow](../modules/workflow.md)

An independently scheduled workflow stage that owns its worker threads.

```mermaid
classDiagram
  class WorkflowStage {
    <<contract>>
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
  WorkflowStage <|.. AggregatorStage
  WorkflowStage <|.. InputStage
  WorkflowStage <|.. ToolStage
```

The diagram draws 3 of 4 relations; the table lists them all.

| Direction | Relation | Type | Summary |
|---|---|---|---|
| ← | implements | [AggregatorStage](io.github.tlaplus.hardening.workflow.aggregator.AggregatorStage.md) | Joins completed TLC and Apalache results with one in-process worker. |
| ← | implements | [InputStage](io.github.tlaplus.hardening.workflow.input.InputStage.md) | Concurrent input stage: admits reserved target ranges from adjacent generations. |
| ← | implements | [ToolStage](io.github.tlaplus.hardening.workflow.tool.ToolStage.md) | One stage that runs an external tool over corpus entries: the parser, TLC, or Apalache. |
| ← | depends | [StageGraph](io.github.tlaplus.hardening.workflow.StageGraph.md) | The stages that run one workflow invocation, wired to their queues, result capacities, and shared collaborators, and the order in which they start and stop. |
