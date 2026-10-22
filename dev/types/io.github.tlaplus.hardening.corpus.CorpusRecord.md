# `CorpusRecord`

`io.github.tlaplus.hardening.corpus` · enum · other · [source](https://github.com/tlaplus/model-checker-hardening/blob/4206d99417cbe58b5b339e189936abe305c56cca/src/main/java/io/github/tlaplus/hardening/corpus/CorpusRecord.java#L7) · module [corpus](../modules/corpus.md)

A small corpus file that pins how the corpus's entries replay.

```mermaid
classDiagram
  class CorpusRecord {
    <<enum>>
  }
  class CorpusPath {
    <<enum>>
  }
  class ReplayRecord {
    <<record>>
  }
  class CorpusDirectory {
    <<facade>>
  }
  CorpusRecord --> CorpusPath : has
  ReplayRecord --> CorpusRecord : has
  CorpusDirectory ..> CorpusRecord : uses
```

The diagram draws 3 of 5 relations; the table lists them all.

| Direction | Relation | Type | Summary |
|---|---|---|---|
| → | has | [CorpusPath](io.github.tlaplus.hardening.corpus.CorpusPath.md) | A fixed path in the on-disk corpus layout. |
| ← | has | [ReplayRecord](io.github.tlaplus.hardening.workflow.ReplayRecord.md) | Replay policy for one corpus record: a run may proceed only when the record says what the run would write. |
| ← | uses | [CorpusDirectory](io.github.tlaplus.hardening.corpus.CorpusDirectory.md) | Owns the on-disk layout and integrity checks for one FuzzTLA corpus. |
| ← | depends | [CorpusRecords](io.github.tlaplus.hardening.workflow.CorpusRecords.md) | The corpus records a run repeats on every later run (ADR 0016 §1, §5, ADR 0017 §6): the technique, the checker set and the rewrite rules. |
| ← | depends | [LibraryManifest](io.github.tlaplus.hardening.workflow.library.LibraryManifest.md) | Replay policy for external source snapshots. |
