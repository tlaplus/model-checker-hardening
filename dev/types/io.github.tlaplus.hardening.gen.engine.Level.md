# `Level`

`io.github.tlaplus.hardening.gen.engine` · enum · other · [source](https://github.com/tlaplus/model-checker-hardening/blob/a304ba37155136c3ba79e4f2fb414cbb47d01606/src/main/java/io/github/tlaplus/hardening/gen/engine/Level.java#L9) · module [gen](../modules/gen.md)

The TLA+ level of the formula an expression form builds, as Specifying Systems defines it and SANY checks it.

```mermaid
classDiagram
  class Level {
    <<enum>>
  }
  class BooleanExpressionKind {
    <<enum>>
  }
  class LevelContext {
    <<enum>>
  }
  class ExpressionKind {
    <<interface>>
  }
  class GeneralExpressionKind {
    <<enum>>
  }
  class TemporalActionExpressionKind {
    <<enum>>
  }
  BooleanExpressionKind --> Level : has
  LevelContext --> Level : has
  ExpressionKind ..> Level : uses
  GeneralExpressionKind ..> Level : uses
  TemporalActionExpressionKind ..> Level : uses
```

The diagram draws 5 of 6 relations; the table lists them all.

| Direction | Relation | Type | Summary |
|---|---|---|---|
| ← | has | [BooleanExpressionKind](io.github.tlaplus.hardening.gen.engine.BooleanExpressionKind.md) | Boolean-valued expression forms. |
| ← | has | [LevelContext](io.github.tlaplus.hardening.gen.engine.LevelContext.md) | The levels an expression request may produce, which `GenerationContext` keeps as a dynamic ceiling while a body is drawn. |
| ← | uses | [ExpressionKind](io.github.tlaplus.hardening.gen.engine.ExpressionKind.md) | A selectable expression form grouped by the component that constructs it. |
| ← | uses | [GeneralExpressionKind](io.github.tlaplus.hardening.gen.engine.GeneralExpressionKind.md) | Type-polymorphic and terminal expression forms. |
| ← | uses | [TemporalActionExpressionKind](io.github.tlaplus.hardening.gen.engine.TemporalActionExpressionKind.md) | The temporal formulas over an action that Specifying Systems defines: an action occurs in a temporal formula only through them and through fairness. |
| ← | depends | [IrExprGenFactory](io.github.tlaplus.hardening.gen.engine.IrExprGenFactory.md) | Factory for deferred, type-directed expression generators within one generation run. |
