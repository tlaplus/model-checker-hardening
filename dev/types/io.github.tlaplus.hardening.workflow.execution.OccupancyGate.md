# `OccupancyGate`

`io.github.tlaplus.hardening.workflow.execution` · class · other · [source](https://github.com/tlaplus/model-checker-hardening/blob/ed5c97e61582ad2ff90b4ea4209355aa0d555717/src/main/java/io/github/tlaplus/hardening/workflow/execution/OccupancyGate.java#L10) · module [workflow](../modules/workflow.md)

Bounds the current occupancy of one stage's result directories across its workers.

```mermaid
classDiagram
  class OccupancyGate
  class AggregatorStage {
    <<process>>
  }
  class CheckerRouting {
    <<policy>>
  }
  class ParserRouting {
    <<policy>>
  }
  AggregatorStage --> OccupancyGate : has
  CheckerRouting --> OccupancyGate : has
  ParserRouting --> OccupancyGate : has
```

The diagram draws 3 of 5 relations; the table lists them all.

| Direction | Relation | Type | Summary |
|---|---|---|---|
| ← | has | [AggregatorStage](io.github.tlaplus.hardening.workflow.aggregator.AggregatorStage.md) | Joins completed TLC and Apalache results with one in-process worker. |
| ← | has | [CheckerRouting](io.github.tlaplus.hardening.workflow.checker.CheckerRouting.md) | Routes a model checker's results. |
| ← | has | [ParserRouting](io.github.tlaplus.hardening.workflow.parser.ParserRouting.md) | Routes parser results. |
| → | depends | [Preconditions](io.github.tlaplus.hardening.common.Preconditions.md) | Shared validation of method and constructor arguments. |
| ← | depends | [StageGraph](io.github.tlaplus.hardening.workflow.StageGraph.md) | The stages that run one workflow invocation, wired to their queues, result capacities, and shared collaborators, and the order in which they start and stop. |
