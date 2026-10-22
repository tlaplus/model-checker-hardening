# `WorkQueue`

`io.github.tlaplus.hardening.workflow.execution` · class · utility · key · [source](https://github.com/tlaplus/model-checker-hardening/blob/4f182776ab66c4f93ff45cb83dfcd11e34fa32d1/src/main/java/io/github/tlaplus/hardening/workflow/execution/WorkQueue.java#L15) · module [workflow](../modules/workflow.md)

A close-aware multi-producer, multi-consumer queue; optionally ordered by priority.

```mermaid
classDiagram
  class WorkQueue {
    <<utility>>
  }
  class StageGraph {
    <<process>>
  }
  class AggregatorStage {
    <<process>>
  }
  class CheckerRouting {
    <<policy>>
  }
  class StageJobLoop
  class WorkflowControl {
    <<model>>
  }
  class InputHandoff {
    <<record>>
  }
  class InputStage {
    <<process>>
  }
  class ParserRouting {
    <<policy>>
  }
  class ToolStage {
    <<process>>
  }
  StageGraph --> WorkQueue : has
  AggregatorStage --> WorkQueue : has
  CheckerRouting --> WorkQueue : has
  StageJobLoop --> WorkQueue : has
  WorkflowControl --> WorkQueue : has
  InputHandoff --> WorkQueue : has
  InputStage --> WorkQueue : has
  ParserRouting --> WorkQueue : has
  ToolStage --> WorkQueue : has
```

| Direction | Relation | Type | Summary |
|---|---|---|---|
| ← | has | [StageGraph](io.github.tlaplus.hardening.workflow.StageGraph.md) | The stages that run one workflow invocation, wired to their queues, result capacities, and shared collaborators, and the order in which they start and stop. |
| ← | has | [AggregatorStage](io.github.tlaplus.hardening.workflow.aggregator.AggregatorStage.md) | Joins completed TLC and Apalache results with one in-process worker. |
| ← | has | [CheckerRouting](io.github.tlaplus.hardening.workflow.checker.CheckerRouting.md) | Routes a model checker's results. |
| ← | has | [StageJobLoop](io.github.tlaplus.hardening.workflow.execution.StageJobLoop.md) | Claims queued items on behalf of one stage worker under the shared CPU budget. |
| ← | has | [WorkflowControl](io.github.tlaplus.hardening.workflow.execution.WorkflowControl.md) | Shared stop state for independently running stages. |
| ← | has | [InputHandoff](io.github.tlaplus.hardening.workflow.input.InputHandoff.md) | How the input stage hands entries to the parser: the parser's queue, and the `00-inputs` slots that bound how many entries wait in it. |
| ← | has | [InputStage](io.github.tlaplus.hardening.workflow.input.InputStage.md) | Concurrent input stage: admits reserved target ranges from adjacent generations. |
| ← | has | [ParserRouting](io.github.tlaplus.hardening.workflow.parser.ParserRouting.md) | Routes parser results. |
| ← | has | [ToolStage](io.github.tlaplus.hardening.workflow.tool.ToolStage.md) | One stage that runs an external tool over corpus entries: the parser, TLC, or Apalache. |
