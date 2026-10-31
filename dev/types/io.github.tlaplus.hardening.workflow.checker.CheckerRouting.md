# `CheckerRouting`

`io.github.tlaplus.hardening.workflow.checker` · class · policy · key · [source](https://github.com/tlaplus/model-checker-hardening/blob/5b16596dd480095d323ac67ff0f85eb85227b4b5/src/main/java/io/github/tlaplus/hardening/workflow/checker/CheckerRouting.java#L24) · module [workflow](../modules/workflow.md)

Routes a model checker's results.

```mermaid
classDiagram
  class CheckerRouting {
    <<policy>>
  }
  class StageRouting {
    <<contract>>
  }
  class OccupancyGate
  class WorkQueue {
    <<utility>>
  }
  class CorpusDirectory {
    <<facade>>
  }
  class CorpusInput {
    <<record>>
  }
  class CorpusVerdict {
    <<model>>
  }
  class StageResult {
    <<record>>
  }
  class CpuBudget {
    <<policy>>
  }
  StageRouting <|.. CheckerRouting
  CheckerRouting --> OccupancyGate : has
  CheckerRouting --> WorkQueue : has
  CheckerRouting ..> CorpusDirectory : uses
  CheckerRouting ..> CorpusInput : uses
  CheckerRouting ..> CorpusVerdict : uses
  CheckerRouting ..> StageResult : uses
  CheckerRouting ..> CpuBudget : uses
```

The diagram draws 8 of 10 relations; the table lists them all.

| Direction | Relation | Type | Summary |
|---|---|---|---|
| → | implements | [StageRouting](io.github.tlaplus.hardening.workflow.tool.StageRouting.md) | Where a `ToolStage` takes its inputs from, how it bounds its result directories, and where a recorded result goes next. |
| → | has | [OccupancyGate](io.github.tlaplus.hardening.workflow.execution.OccupancyGate.md) | Bounds the current occupancy of one stage's result directories across its workers. |
| → | has | [WorkQueue](io.github.tlaplus.hardening.workflow.execution.WorkQueue.md) | A close-aware multi-producer, multi-consumer queue; optionally ordered by priority. |
| → | uses | [CorpusDirectory](io.github.tlaplus.hardening.corpus.CorpusDirectory.md) | Owns the on-disk layout and integrity checks for one FuzzTLA corpus. |
| → | uses | [CorpusInput](io.github.tlaplus.hardening.corpus.CorpusInput.md) | The required fields of one encoded corpus input. |
| → | uses | [CorpusVerdict](io.github.tlaplus.hardening.corpus.CorpusVerdict.md) | A verdict supported by the implemented corpus stages. |
| → | uses | [StageResult](io.github.tlaplus.hardening.corpus.StageResult.md) | What one stage decided about one input, and the tool output kept for triage. |
| → | uses | [CpuBudget](io.github.tlaplus.hardening.workflow.execution.CpuBudget.md) | Priority-aware logical CPU budget shared by all stage workers. |
| → | depends | [CorpusException](io.github.tlaplus.hardening.corpus.CorpusException.md) | Reports an invalid corpus layout or corpus entry. |
| ← | depends | [StageGraph](io.github.tlaplus.hardening.workflow.StageGraph.md) | The stages that run one workflow invocation, wired to their queues, result capacities, and shared collaborators, and the order in which they start and stop. |
