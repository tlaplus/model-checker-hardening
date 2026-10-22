# `ModuleSection`

`io.github.tlaplus.hardening.gen.engine` · enum · other · [source](https://github.com/tlaplus/model-checker-hardening/blob/4206d99417cbe58b5b339e189936abe305c56cca/src/main/java/io/github/tlaplus/hardening/gen/engine/ModuleSection.java#L24) · module [gen](../modules/gen.md)

The independent parts of a module input, each decoded from its own contiguous range of bytes.

```mermaid
classDiagram
  class ModuleSection {
    <<enum>>
  }
  class ExpressionCategory {
    <<enum>>
  }
  class Draw {
    <<model>>
  }
  class IrGenerationConfig {
    <<config>>
  }
  ModuleSection --> ExpressionCategory : has
  ModuleSection ..> Draw : uses
  ModuleSection ..> IrGenerationConfig : uses
```

The diagram draws 3 of 4 relations; the table lists them all.

| Direction | Relation | Type | Summary |
|---|---|---|---|
| → | has | [ExpressionCategory](io.github.tlaplus.hardening.gen.ExpressionCategory.md) | A user-facing category assigned to generated expression forms. |
| → | uses | [Draw](io.github.tlaplus.hardening.gen.Draw.md) | A forward-only deterministic cursor that decodes structured choices from an unstructured byte array. |
| → | uses | [IrGenerationConfig](io.github.tlaplus.hardening.gen.IrGenerationConfig.md) | Settings for generated TLA+ expressions and modules. |
| ← | depends | [IrSpecGeneratorEngine](io.github.tlaplus.hardening.gen.engine.IrSpecGeneratorEngine.md) | Reusable coordinator for generating the declarations of one TLA+ module. |
