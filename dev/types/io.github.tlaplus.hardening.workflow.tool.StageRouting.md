# `StageRouting`

`io.github.tlaplus.hardening.workflow.tool` · interface · contract · key · [source](https://github.com/tlaplus/model-checker-hardening/blob/4c5cb469fa5cdcada2cd5740bce130b825b35343/src/main/java/io/github/tlaplus/hardening/workflow/tool/StageRouting.java#L24) · module [workflow](../modules/workflow.md)

Where a `ToolStage` takes its inputs from, how it bounds its result directories, and where a recorded result goes next.

```mermaid
classDiagram
  class StageRouting {
    <<contract>>
  }
  class CheckerRouting {
    <<policy>>
  }
  class ParserRouting {
    <<policy>>
  }
  class ToolStage {
    <<process>>
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
  StageRouting <|.. ParserRouting
  ToolStage --> StageRouting : has
  StageRouting ..> CorpusDirectory : uses
  StageRouting ..> CorpusInput : uses
  StageRouting ..> CorpusVerdict : uses
  StageRouting ..> StageResult : uses
  StageRouting ..> CpuBudget : uses
```

The diagram draws 8 of 10 relations; the table lists them all.

| Direction | Relation | Type | Summary |
|---|---|---|---|
| ← | implements | [CheckerRouting](io.github.tlaplus.hardening.workflow.checker.CheckerRouting.md) | Routes a model checker's results. |
| ← | implements | [ParserRouting](io.github.tlaplus.hardening.workflow.parser.ParserRouting.md) | Routes parser results. |
| ← | has | [ToolStage](io.github.tlaplus.hardening.workflow.tool.ToolStage.md) | One stage that runs an external tool over corpus entries: the parser, TLC, or Apalache. |
| → | uses | [CorpusDirectory](io.github.tlaplus.hardening.corpus.CorpusDirectory.md) | Owns the on-disk layout and integrity checks for one FuzzTLA corpus. |
| → | uses | [CorpusInput](io.github.tlaplus.hardening.corpus.CorpusInput.md) | The required fields of one encoded corpus input. |
| → | uses | [CorpusVerdict](io.github.tlaplus.hardening.corpus.CorpusVerdict.md) | A verdict supported by the implemented corpus stages. |
| → | uses | [StageResult](io.github.tlaplus.hardening.corpus.StageResult.md) | What one stage decided about one input, and the tool output kept for triage. |
| → | uses | [CpuBudget](io.github.tlaplus.hardening.workflow.execution.CpuBudget.md) | Priority-aware logical CPU budget shared by all stage workers. |
| → | depends | [CorpusException](io.github.tlaplus.hardening.corpus.CorpusException.md) | Reports an invalid corpus layout or corpus entry. |
| ← | depends | [StageGraph](io.github.tlaplus.hardening.workflow.StageGraph.md) | The stages that run one workflow invocation, wired to their queues, result capacities, and shared collaborators, and the order in which they start and stop. |
