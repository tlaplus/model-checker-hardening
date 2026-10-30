# `StageEnvironment`

`io.github.tlaplus.hardening.workflow.execution` · record · model · key · [source](https://github.com/tlaplus/model-checker-hardening/blob/918f7db91890f302587d25c76dc7ac189db3d023/src/main/java/io/github/tlaplus/hardening/workflow/execution/StageEnvironment.java#L15) · module [workflow](../modules/workflow.md)

The collaborators every stage of one workflow invocation shares: the corpus it reads and writes, the decoder each stored input kind is regenerated through, the CPU budget it competes for, and the stop state it observes and reports to.

```mermaid
classDiagram
  class StageEnvironment {
    <<model>>
  }
  class CorpusDirectory {
    <<facade>>
  }
  class CpuBudget {
    <<policy>>
  }
  class WorkflowControl {
    <<model>>
  }
  class WorkflowEvents {
    <<contract>>
  }
  class SpecDecoders {
    <<facade>>
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
  StageEnvironment --> CorpusDirectory : has
  StageEnvironment --> CpuBudget : has
  StageEnvironment --> WorkflowControl : has
  StageEnvironment --> WorkflowEvents : has
  StageEnvironment --> SpecDecoders : has
  AggregatorStage --> StageEnvironment : has
  InputStage --> StageEnvironment : has
  ToolStage --> StageEnvironment : has
```

The diagram draws 8 of 9 relations; the table lists them all.

| Direction | Relation | Type | Summary |
|---|---|---|---|
| → | has | [CorpusDirectory](io.github.tlaplus.hardening.corpus.CorpusDirectory.md) | Owns the on-disk layout and integrity checks for one FuzzTLA corpus. |
| → | has | [CpuBudget](io.github.tlaplus.hardening.workflow.execution.CpuBudget.md) | Priority-aware logical CPU budget shared by all stage workers. |
| → | has | [WorkflowControl](io.github.tlaplus.hardening.workflow.execution.WorkflowControl.md) | Shared stop state for independently running stages. |
| → | has | [WorkflowEvents](io.github.tlaplus.hardening.workflow.execution.WorkflowEvents.md) | Durable admission and stage transitions, reported to whatever schedules generations (ADR 0010). |
| → | has | [SpecDecoders](io.github.tlaplus.hardening.workflow.spec.SpecDecoders.md) | Immutable registry of the decoders that turn stored payloads into checkable modules. |
| ← | has | [AggregatorStage](io.github.tlaplus.hardening.workflow.aggregator.AggregatorStage.md) | Joins completed TLC and Apalache results with one in-process worker. |
| ← | has | [InputStage](io.github.tlaplus.hardening.workflow.input.InputStage.md) | Concurrent input stage: admits reserved target ranges from adjacent generations. |
| ← | has | [ToolStage](io.github.tlaplus.hardening.workflow.tool.ToolStage.md) | One stage that runs an external tool over corpus entries: the parser, TLC, or Apalache. |
| ← | depends | [StageGraph](io.github.tlaplus.hardening.workflow.StageGraph.md) | The stages that run one workflow invocation, wired to their queues, result capacities, and shared collaborators, and the order in which they start and stop. |
