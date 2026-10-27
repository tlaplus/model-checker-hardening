# `SequenceExprGenFactory`

`io.github.tlaplus.hardening.gen.engine` · class · other · [source](https://github.com/tlaplus/model-checker-hardening/blob/3b201567990514c2a43b90dca521dbe32af37023/src/main/java/io/github/tlaplus/hardening/gen/engine/SequenceExprGenFactory.java#L7) · module [gen](../modules/gen.md)

Constructs sequence-valued expression generators.

```mermaid
classDiagram
  class SequenceExprGenFactory
  class AbstractExprGenFactory {
    <<contract>>
  }
  class IrExprGenFactory
  class Generator {
    <<contract>>
  }
  class GenerationContext {
    <<model>>
  }
  class IrTypeGenFactory
  class SequenceExpressionKind {
    <<enum>>
  }
  class SequenceType {
    <<record>>
  }
  AbstractExprGenFactory <|-- SequenceExprGenFactory
  IrExprGenFactory --> SequenceExprGenFactory : has
  SequenceExprGenFactory ..> Generator : uses
  SequenceExprGenFactory ..> GenerationContext : uses
  SequenceExprGenFactory ..> IrExprGenFactory : uses
  SequenceExprGenFactory ..> IrTypeGenFactory : uses
  SequenceExprGenFactory ..> SequenceExpressionKind : uses
  SequenceExprGenFactory ..> SequenceType : uses
```

The diagram draws 8 of 11 relations; the table lists them all.

| Direction | Relation | Type | Summary |
|---|---|---|---|
| → | extends | [AbstractExprGenFactory](io.github.tlaplus.hardening.gen.engine.AbstractExprGenFactory.md) | Shared access to the state and recursive factories used by expression generator families. |
| ← | has | [IrExprGenFactory](io.github.tlaplus.hardening.gen.engine.IrExprGenFactory.md) | Factory for deferred, type-directed expression generators within one generation run. |
| → | uses | [Generator](io.github.tlaplus.hardening.gen.Generator.md) | A composable recipe for decoding a value from a shared `Draw` cursor. |
| → | uses | [GenerationContext](io.github.tlaplus.hardening.gen.engine.GenerationContext.md) | Mutable semantic state shared by every component of one generation run. |
| → | uses | [IrExprGenFactory](io.github.tlaplus.hardening.gen.engine.IrExprGenFactory.md) | Factory for deferred, type-directed expression generators within one generation run. |
| → | uses | [IrTypeGenFactory](io.github.tlaplus.hardening.gen.engine.IrTypeGenFactory.md) | Factory for deferred generators of internal types within the configured remaining-depth budget. |
| → | uses | [SequenceExpressionKind](io.github.tlaplus.hardening.gen.engine.SequenceExpressionKind.md) | Sequence-valued expression forms. |
| → | uses | [SequenceType](io.github.tlaplus.hardening.gen.engine.SequenceType.md) | Sequence type. |
| → | depends | [Draw](io.github.tlaplus.hardening.gen.Draw.md) | A forward-only deterministic cursor that decodes structured choices from an unstructured byte array. |
| → | depends | [IrType](io.github.tlaplus.hardening.gen.engine.IrType.md) | Internal type model used to direct checked expression construction. |
| → | depends | [PrimitiveType](io.github.tlaplus.hardening.gen.engine.PrimitiveType.md) | Primitive expression result types. |
