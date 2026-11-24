# `GenericCompatibility`

`io.github.tlaplus.hardening.config` · enum · other · [source](https://github.com/tlaplus/model-checker-hardening/blob/e4ab65335fef2f7aa9e835e4805d8e294545cba2/src/main/java/io/github/tlaplus/hardening/config/GenericCompatibility.java#L8) · module [config](../modules/config.md)

The reference checker whose conventions a generic checker follows (ADR 0033 §2): the configuration file FuzzTLA writes for it and how its exit status becomes a verdict.

**Decisions:** [ADR 0033: A generic checker for TLC- and Apalache-compatible tools](https://github.com/tlaplus/model-checker-hardening/blob/e4ab65335fef2f7aa9e835e4805d8e294545cba2/docs/decisions/0033-generic-checker.md)

```mermaid
classDiagram
  class GenericCompatibility {
    <<enum>>
  }
  class GenericCheckerConfig {
    <<config>>
  }
  class ConfigSchema {
    <<config>>
  }
  class ConfigValueType {
    <<record>>
  }
  GenericCheckerConfig --> GenericCompatibility : has
  ConfigSchema ..> GenericCompatibility : uses
  ConfigValueType ..> GenericCompatibility : uses
```

| Direction | Relation | Type | Summary |
|---|---|---|---|
| ← | has | [GenericCheckerConfig](io.github.tlaplus.hardening.config.GenericCheckerConfig.md) | The `[generic]` table (ADR 0033 §1): a model checker that claims compatibility with TLC or Apalache, run as a native process for each input. |
| ← | uses | [ConfigSchema](io.github.tlaplus.hardening.config.ConfigSchema.md) | The declaration of every key in `config.toml`: where it lives, what it means, how its value is read, and how it is written back. |
| ← | uses | [ConfigValueType](io.github.tlaplus.hardening.config.ConfigValueType.md) | How the value of one configuration key is read from TOML and written back as TOML syntax. |
