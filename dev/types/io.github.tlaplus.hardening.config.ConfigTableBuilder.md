# `ConfigTableBuilder`

`io.github.tlaplus.hardening.config` · class · other · [source](https://github.com/tlaplus/model-checker-hardening/blob/0b7c167b636088dd43d36713d157e22f7ebd4f79/src/main/java/io/github/tlaplus/hardening/config/ConfigTableBuilder.java#L9) · module [config](../modules/config.md)

Registers typed keys in document order, then snapshots them as an immutable schema table.

```mermaid
classDiagram
  class ConfigTableBuilder
  class ConfigSchema {
    <<config>>
  }
  class FuzzTlaConfig {
    <<config>>
  }
  class ConfigValueType {
    <<record>>
  }
  class GeneratorLimitSchema
  ConfigTableBuilder --> ConfigSchema : has
  ConfigTableBuilder --> FuzzTlaConfig : has
  ConfigTableBuilder ..> ConfigValueType : uses
  ConfigSchema ..> ConfigTableBuilder : uses
  GeneratorLimitSchema ..> ConfigTableBuilder : uses
```

| Direction | Relation | Type | Summary |
|---|---|---|---|
| → | has | [ConfigSchema](io.github.tlaplus.hardening.config.ConfigSchema.md) | The declaration of every key in `config.toml`: where it lives, what it means, how its value is read, and how it is written back. |
| → | has | [FuzzTlaConfig](io.github.tlaplus.hardening.config.FuzzTlaConfig.md) | Complete configuration of input generation and workflow execution. |
| → | uses | [ConfigValueType](io.github.tlaplus.hardening.config.ConfigValueType.md) | How the value of one configuration key is read from TOML and written back as TOML syntax. |
| ← | uses | [ConfigSchema](io.github.tlaplus.hardening.config.ConfigSchema.md) | The declaration of every key in `config.toml`: where it lives, what it means, how its value is read, and how it is written back. |
| ← | uses | [GeneratorLimitSchema](io.github.tlaplus.hardening.config.GeneratorLimitSchema.md) | Declares and assembles the expression and module limit keys in the generator table. |
