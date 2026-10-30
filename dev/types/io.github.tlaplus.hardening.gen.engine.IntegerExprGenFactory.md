# `IntegerExprGenFactory`

`io.github.tlaplus.hardening.gen.engine` · class · other · [source](https://github.com/tlaplus/model-checker-hardening/blob/4df6a11e5b4bf93609393935c4cbeceec08cce0c/src/main/java/io/github/tlaplus/hardening/gen/engine/IntegerExprGenFactory.java#L10) · module [gen](../modules/gen.md)

Constructs integer-valued expression generators.

```mermaid
classDiagram
  class IntegerExprGenFactory
  class AbstractExprGenFactory {
    <<contract>>
  }
  class IrExprGenFactory
  class Generator {
    <<contract>>
  }
  class BoundaryInteger {
    <<enum>>
  }
  class GenerationContext {
    <<model>>
  }
  class IntegerExpressionKind {
    <<enum>>
  }
  class IrTypeGenFactory
  AbstractExprGenFactory <|-- IntegerExprGenFactory
  IrExprGenFactory --> IntegerExprGenFactory : has
  IntegerExprGenFactory ..> Generator : uses
  IntegerExprGenFactory ..> BoundaryInteger : uses
  IntegerExprGenFactory ..> GenerationContext : uses
  IntegerExprGenFactory ..> IntegerExpressionKind : uses
  IntegerExprGenFactory ..> IrExprGenFactory : uses
  IntegerExprGenFactory ..> IrTypeGenFactory : uses
```

The diagram draws 8 of 18 relations; the table lists them all.

| Direction | Relation | Type | Summary |
|---|---|---|---|
| → | extends | [AbstractExprGenFactory](io.github.tlaplus.hardening.gen.engine.AbstractExprGenFactory.md) | Shared access to the state and recursive factories used by expression generator families. |
| ← | has | [IrExprGenFactory](io.github.tlaplus.hardening.gen.engine.IrExprGenFactory.md) | Factory for deferred, type-directed expression generators within one generation run. |
| → | uses | [Generator](io.github.tlaplus.hardening.gen.Generator.md) | A composable recipe for decoding a value from a shared `Draw` cursor. |
| → | uses | [BoundaryInteger](io.github.tlaplus.hardening.gen.engine.BoundaryInteger.md) | Integer values at which checker arithmetic changes behaviour (ADR 0012). |
| → | uses | [GenerationContext](io.github.tlaplus.hardening.gen.engine.GenerationContext.md) | Mutable semantic state shared by every component of one generation run. |
| → | uses | [IntegerExpressionKind](io.github.tlaplus.hardening.gen.engine.IntegerExpressionKind.md) | Integer-valued expression forms. |
| → | uses | [IrExprGenFactory](io.github.tlaplus.hardening.gen.engine.IrExprGenFactory.md) | Factory for deferred, type-directed expression generators within one generation run. |
| → | uses | [IrTypeGenFactory](io.github.tlaplus.hardening.gen.engine.IrTypeGenFactory.md) | Factory for deferred generators of internal types within the configured remaining-depth budget. |
| → | depends | [BasicGenerators](io.github.tlaplus.hardening.gen.BasicGenerators.md) | Factory methods for basic generators that compose through a shared `Draw`. |
| → | depends | [Draw](io.github.tlaplus.hardening.gen.Draw.md) | A forward-only deterministic cursor that decodes structured choices from an unstructured byte array. |
| → | depends | [ExpressionLimits](io.github.tlaplus.hardening.gen.ExpressionLimits.md) | Bounds on recursive construction and variable-size payloads within one expression. |
| → | depends | [IntegerLimits](io.github.tlaplus.hardening.gen.IntegerLimits.md) | Integer literals and the closed integer terminal. |
| → | depends | [IntegerLiteralMode](io.github.tlaplus.hardening.gen.IntegerLiteralMode.md) | How an integer literal decodes its value (ADR 0012). |
| → | depends | [IrGenerationConfig](io.github.tlaplus.hardening.gen.IrGenerationConfig.md) | Settings for generated TLA+ expressions and modules. |
| → | depends | [IrType](io.github.tlaplus.hardening.gen.engine.IrType.md) | Internal type model used to direct checked expression construction. |
| → | depends | [PrimitiveType](io.github.tlaplus.hardening.gen.engine.PrimitiveType.md) | Primitive expression result types. |
| → | depends | [SequenceType](io.github.tlaplus.hardening.gen.engine.SequenceType.md) | Sequence type. |
| → | depends | [SetType](io.github.tlaplus.hardening.gen.engine.SetType.md) | Set type. |
