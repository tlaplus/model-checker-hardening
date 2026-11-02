# `TemporalProperty`

`io.github.tlaplus.hardening.gen` · record · other · [source](https://github.com/tlaplus/model-checker-hardening/blob/dffbe46e47e389e21aea3743d4657df794d7bbc7/src/main/java/io/github/tlaplus/hardening/gen/TemporalProperty.java#L28) · module [gen](../modules/gen.md)

The temporal property of a generated module and the fairness that constrains it.

**Decisions:** [ADR 0007: Levels and temporal properties](https://github.com/tlaplus/model-checker-hardening/blob/dffbe46e47e389e21aea3743d4657df794d7bbc7/docs/decisions/0007-levels-and-temporal-properties.md)

```mermaid
classDiagram
  class TemporalProperty {
    <<record>>
  }
  class GeneratedSpec {
    <<model>>
  }
  class PropertyGenFactory
  class FuzzInputModule {
    <<facade>>
  }
  GeneratedSpec --> TemporalProperty : has
  PropertyGenFactory ..> TemporalProperty : uses
  FuzzInputModule ..> TemporalProperty : uses
```

The diagram draws 3 of 4 relations; the table lists them all.

| Direction | Relation | Type | Summary |
|---|---|---|---|
| ← | has | [GeneratedSpec](io.github.tlaplus.hardening.gen.GeneratedSpec.md) | The declarations one generated module consists of. |
| ← | uses | [PropertyGenFactory](io.github.tlaplus.hardening.gen.engine.PropertyGenFactory.md) | Module-level temporal property: the property formula and its fairness conditions. |
| ← | uses | [FuzzInputModule](io.github.tlaplus.hardening.workflow.spec.FuzzInputModule.md) | Assembles the module checked by the parser and model-checker stages. |
| ← | depends | [Rewriter](io.github.tlaplus.hardening.gen.rewrite.Rewriter.md) | Decodes a rewrite payload into a rewrite of a base (ADR 0016 §2). |
