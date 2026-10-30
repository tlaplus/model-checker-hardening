# `VisibleActionOperators`

`io.github.tlaplus.hardening.gen.engine` · class · other · [source](https://github.com/tlaplus/model-checker-hardening/blob/4c5cb469fa5cdcada2cd5740bce130b825b35343/src/main/java/io/github/tlaplus/hardening/gen/engine/VisibleActionOperators.java#L7) · module [gen](../modules/gen.md)

Immutable operator prefix; candidates retain declaration order for byte selection.

```mermaid
classDiagram
  class VisibleActionOperators
  class GeneratedActionOperator {
    <<record>>
  }
  class OperatorType {
    <<record>>
  }
  class ScopedName {
    <<record>>
  }
  class ActionGenFactory
  class ActionShapeGenFactory
  VisibleActionOperators ..> GeneratedActionOperator : uses
  VisibleActionOperators ..> OperatorType : uses
  VisibleActionOperators ..> ScopedName : uses
  ActionGenFactory ..> VisibleActionOperators : uses
  ActionShapeGenFactory ..> VisibleActionOperators : uses
```

The diagram draws 5 of 7 relations; the table lists them all.

| Direction | Relation | Type | Summary |
|---|---|---|---|
| → | uses | [GeneratedActionOperator](io.github.tlaplus.hardening.gen.GeneratedActionOperator.md) | One generated action operator: a definition the next-state action may apply. |
| → | uses | [OperatorType](io.github.tlaplus.hardening.gen.engine.OperatorType.md) | Operator type. |
| → | uses | [ScopedName](io.github.tlaplus.hardening.gen.engine.ScopedName.md) | A typed name visible while recursively generating a lexical body. |
| ← | uses | [ActionGenFactory](io.github.tlaplus.hardening.gen.engine.ActionGenFactory.md) | Module-level action assembly: Init, operator definitions, bounded parameters, guards, Next and the unconditional step update. |
| ← | uses | [ActionShapeGenFactory](io.github.tlaplus.hardening.gen.engine.ActionShapeGenFactory.md) | Recursive, complete action shapes. |
| → | depends | [ActionEffect](io.github.tlaplus.hardening.gen.ActionEffect.md) | Nonempty, duplicate-free effect in variable declaration order, with set-style identity. |
| ← | depends | [IrSpecGeneratorEngine](io.github.tlaplus.hardening.gen.engine.IrSpecGeneratorEngine.md) | Reusable coordinator for generating the declarations of one TLA+ module. |
