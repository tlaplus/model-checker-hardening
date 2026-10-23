# `TlcMessageMetrics`

`io.github.tlaplus.hardening.workflow.tlc` · class · other · [source](https://github.com/tlaplus/model-checker-hardening/blob/12d0a3fd62c72add470e6ee06490c4e3591f490e/src/main/java/io/github/tlaplus/hardening/workflow/tlc/TlcMessageMetrics.java#L14) · module [workflow](../modules/workflow.md)

Follows the messages TLC prints, which say when its initial states are complete and which states its error trace consists of.

```mermaid
classDiagram
  class TlcMessageMetrics
  class TlcExplorationMetrics
  TlcExplorationMetrics --> TlcMessageMetrics : has
```

| Direction | Relation | Type | Summary |
|---|---|---|---|
| ← | has | [TlcExplorationMetrics](io.github.tlaplus.hardening.workflow.tlc.TlcExplorationMetrics.md) | Measures one TLC run and attaches the result to its classified outcome (ADR 0008). |
