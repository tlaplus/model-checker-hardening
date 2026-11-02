# `PropertyRelation`

`io.github.tlaplus.hardening.workflow.spec` · enum · other · [source](https://github.com/tlaplus/model-checker-hardening/blob/dffbe46e47e389e21aea3743d4657df794d7bbc7/src/main/java/io/github/tlaplus/hardening/workflow/spec/PropertyRelation.java#L9) · module [workflow](../modules/workflow.md)

How the relation module of a metamorphic pair of modules treats their properties and fairness.

```mermaid
classDiagram
  class PropertyRelation {
    <<enum>>
  }
  class CheckKind {
    <<enum>>
  }
  class GeneratedSpec {
    <<model>>
  }
  class FuzzInputModule {
    <<facade>>
  }
  class SpecArtifact {
    <<model>>
  }
  PropertyRelation --> CheckKind : has
  PropertyRelation ..> GeneratedSpec : uses
  FuzzInputModule ..> PropertyRelation : uses
  SpecArtifact ..> PropertyRelation : uses
```

The diagram draws 4 of 6 relations; the table lists them all.

| Direction | Relation | Type | Summary |
|---|---|---|---|
| → | has | [CheckKind](io.github.tlaplus.hardening.workflow.worker.CheckKind.md) | What one checker invocation checks in an assembled module: which side's specification it explores, and whether it checks the invariant, the action invariant of a metamorphic module and a temporal property. |
| → | uses | [GeneratedSpec](io.github.tlaplus.hardening.gen.GeneratedSpec.md) | The declarations one generated module consists of. |
| ← | uses | [FuzzInputModule](io.github.tlaplus.hardening.workflow.spec.FuzzInputModule.md) | Assembles the module checked by the parser and model-checker stages. |
| ← | uses | [SpecArtifact](io.github.tlaplus.hardening.workflow.spec.SpecArtifact.md) | One assembled module, what its checkers are asked about it, and what its decoder produced. |
| → | depends | [FuzzInputModule](io.github.tlaplus.hardening.workflow.spec.FuzzInputModule.md) | Assembles the module checked by the parser and model-checker stages. |
| ← | depends | [SpecDecoders](io.github.tlaplus.hardening.workflow.spec.SpecDecoders.md) | Immutable registry of the decoders that turn stored payloads into checkable modules. |
