# `EntryProgress`

`io.github.tlaplus.hardening.corpus` · record · other · [source](https://github.com/tlaplus/model-checker-hardening/blob/6bbf8b66c375696d1798f7b7a061b81573a3f42a/src/main/java/io/github/tlaplus/hardening/corpus/EntryProgress.java#L18) · module [corpus](../modules/corpus.md)

How far one logical entry has moved through the pipeline, and the single definition of the two questions generation scheduling asks of it (ADR 0010).

**Decisions:** [ADR 0010: Generational mutation](https://github.com/tlaplus/model-checker-hardening/blob/6bbf8b66c375696d1798f7b7a061b81573a3f42a/docs/decisions/0010-mutation.md)

```mermaid
classDiagram
  class EntryProgress {
    <<record>>
  }
  class CorpusStage {
    <<model>>
  }
  class CorpusVerdict {
    <<model>>
  }
  class CorpusInventory {
    <<record>>
  }
  class GenerationProgress {
    <<model>>
  }
  class CheckerSet {
    <<record>>
  }
  class CorpusEnvelope {
    <<record>>
  }
  class InventoryScan
  EntryProgress --> CorpusStage : has
  EntryProgress --> CorpusVerdict : has
  CorpusInventory --> EntryProgress : has
  GenerationProgress --> EntryProgress : has
  EntryProgress ..> CheckerSet : uses
  EntryProgress ..> CorpusEnvelope : uses
  InventoryScan ..> EntryProgress : uses
```

The diagram draws 7 of 9 relations; the table lists them all.

| Direction | Relation | Type | Summary |
|---|---|---|---|
| → | has | [CorpusStage](io.github.tlaplus.hardening.corpus.CorpusStage.md) | One implemented stage of the fuzzing pipeline, and the corpus directories it owns. |
| → | has | [CorpusVerdict](io.github.tlaplus.hardening.corpus.CorpusVerdict.md) | A verdict supported by the implemented corpus stages. |
| ← | has | [CorpusInventory](io.github.tlaplus.hardening.corpus.CorpusInventory.md) | Immutable startup inventory used to seed stage queues and capacities. |
| ← | has | [GenerationProgress](io.github.tlaplus.hardening.workflow.GenerationProgress.md) | Where every generation of a run stands, tracked from stage events instead of a corpus rescan (ADR 0010). |
| → | uses | [CheckerSet](io.github.tlaplus.hardening.corpus.CheckerSet.md) | The checker branches a corpus runs: a non-empty set of checking stages, in stage order. |
| → | uses | [CorpusEnvelope](io.github.tlaplus.hardening.corpus.CorpusEnvelope.md) | Supported fields decoded from one corpus input envelope. |
| ← | uses | [InventoryScan](io.github.tlaplus.hardening.corpus.InventoryScan.md) | The second pass of startup recovery: validates every entry of a corpus in which no transition is half-finished, and reports what it holds. |
| → | depends | [Preconditions](io.github.tlaplus.hardening.common.Preconditions.md) | Shared validation of method and constructor arguments. |
| → | depends | [StageMetadata](io.github.tlaplus.hardening.corpus.StageMetadata.md) | Metadata recorded when one workflow stage finishes processing an input. |
