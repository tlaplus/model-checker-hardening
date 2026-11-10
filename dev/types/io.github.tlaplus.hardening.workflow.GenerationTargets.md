# `GenerationTargets`

`io.github.tlaplus.hardening.workflow` · class · other · [source](https://github.com/tlaplus/model-checker-hardening/blob/da386b24fc0ea1f5a200a0d74df0b119a27ca0ba/src/main/java/io/github/tlaplus/hardening/workflow/GenerationTargets.java#L27) · module [workflow](../modules/workflow.md)

Which target ordinals of one generation are already spoken for, and which source fills the rest (ADR 0010, ADR 0016 §6).

**Decisions:** [ADR 0010: Generational mutation](https://github.com/tlaplus/model-checker-hardening/blob/da386b24fc0ea1f5a200a0d74df0b119a27ca0ba/docs/decisions/0010-mutation.md), [ADR 0016: Metamorphic testing](https://github.com/tlaplus/model-checker-hardening/blob/da386b24fc0ea1f5a200a0d74df0b119a27ca0ba/docs/decisions/0016-metamorphic-testing.md)

```mermaid
classDiagram
  class GenerationTargets
  class EntryOrigin {
    <<enum>>
  }
  class GenerationLoop {
    <<process>>
  }
  GenerationTargets --> EntryOrigin : has
  GenerationLoop --> GenerationTargets : has
```

The diagram draws 2 of 3 relations; the table lists them all.

| Direction | Relation | Type | Summary |
|---|---|---|---|
| → | has | [EntryOrigin](io.github.tlaplus.hardening.corpus.EntryOrigin.md) | Which candidate source admitted an entry, as its generation metadata records it (ADR 0010, ADR 0016 §6). |
| ← | has | [GenerationLoop](io.github.tlaplus.hardening.workflow.GenerationLoop.md) | Runs the generations of one workflow invocation over a single stage graph (ADR 0010). |
| → | depends | [Preconditions](io.github.tlaplus.hardening.common.Preconditions.md) | Shared validation of method and constructor arguments. |
