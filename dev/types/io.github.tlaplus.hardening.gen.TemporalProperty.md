# `TemporalProperty`

`io.github.tlaplus.hardening.gen` · record · other · [source](https://github.com/tlaplus/model-checker-hardening/blob/5b16596dd480095d323ac67ff0f85eb85227b4b5/src/main/java/io/github/tlaplus/hardening/gen/TemporalProperty.java#L28) · module [gen](../modules/gen.md)

The temporal property of a generated module and the fairness that constrains it.

**Decisions:** [ADR 0007: Levels and temporal properties](https://github.com/tlaplus/model-checker-hardening/blob/5b16596dd480095d323ac67ff0f85eb85227b4b5/docs/decisions/0007-levels-and-temporal-properties.md)

```mermaid
classDiagram
  class TemporalProperty {
    <<record>>
  }
  class GeneratedSpec {
    <<model>>
  }
  class PropertyGenFactory
  GeneratedSpec --> TemporalProperty : has
  PropertyGenFactory ..> TemporalProperty : uses
```

The diagram draws 2 of 3 relations; the table lists them all.

| Direction | Relation | Type | Summary |
|---|---|---|---|
| ← | has | [GeneratedSpec](io.github.tlaplus.hardening.gen.GeneratedSpec.md) | The declarations one generated module consists of. |
| ← | uses | [PropertyGenFactory](io.github.tlaplus.hardening.gen.engine.PropertyGenFactory.md) | Module-level temporal property: the property formula and its fairness conditions. |
| ← | depends | [FuzzInputModule](io.github.tlaplus.hardening.workflow.spec.FuzzInputModule.md) | Assembles the module checked by the parser and model-checker stages. |
