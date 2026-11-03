# `ReplayOutcome`

`io.github.tlaplus.hardening.database` · interface · other · [source](https://github.com/tlaplus/model-checker-hardening/blob/7b97336d8ec341ec48f89de4a368b6a488a145ac/src/main/java/io/github/tlaplus/hardening/database/ReplayOutcome.java#L11) · module [database](../modules/database.md)

The result of analysing one input: what its replay shows, or why it could not be replayed.

```mermaid
classDiagram
  class ReplayOutcome {
    <<interface>>
  }
  class CorpusInput {
    <<record>>
  }
  class InputAnalysis {
    <<interface>>
  }
  class ReplayedInput {
    <<record>>
  }
  class EntryRows {
    <<record>>
  }
  ReplayOutcome ..> CorpusInput : uses
  ReplayOutcome ..> InputAnalysis : uses
  ReplayOutcome ..> ReplayedInput : uses
  EntryRows ..> ReplayOutcome : uses
```

The diagram draws 4 of 6 relations; the table lists them all.

| Direction | Relation | Type | Summary |
|---|---|---|---|
| → | uses | [CorpusInput](io.github.tlaplus.hardening.corpus.CorpusInput.md) | The required fields of one encoded corpus input. |
| → | uses | [InputAnalysis](io.github.tlaplus.hardening.corpus.InputAnalysis.md) | Counts the evaluated nodes, expression constructs and construct edges of one stored input, and names the rewrite rules it applied, typically by replaying it through the generator. |
| → | uses | [ReplayedInput](io.github.tlaplus.hardening.corpus.ReplayedInput.md) | What replaying one stored input shows beyond its envelope. |
| ← | uses | [EntryRows](io.github.tlaplus.hardening.database.EntryRows.md) | The rows that one decoded corpus entry contributes: its `entry` row, followed by the rows that reference it: one `knownDefect` row per signature, one `mutationOperator` row per operator, one `stage` row per stage record, one `expr` row per expression construct of its replayed input and one `exprEdge` row per edge between constructs. |
| → | depends | [Diagnostics](io.github.tlaplus.hardening.common.Diagnostics.md) | Human-readable diagnostic formatting for failures. |
| ← | depends | [EntryBatchExporter](io.github.tlaplus.hardening.database.EntryBatchExporter.md) | Exports listed entry files in chunks. |
