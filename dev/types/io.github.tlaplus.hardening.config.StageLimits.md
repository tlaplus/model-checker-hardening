# `StageLimits`

`io.github.tlaplus.hardening.config` · interface · other · [source](https://github.com/tlaplus/model-checker-hardening/blob/3df8c8564b58e946b95079b671788c8315f98831/src/main/java/io/github/tlaplus/hardening/config/StageLimits.java#L6) · module [config](../modules/config.md)

The limits every configured tool stage takes: its result capacity and a per-input time limit.

```mermaid
classDiagram
  class StageLimits {
    <<interface>>
  }
  class CheckerStageConfig {
    <<record>>
  }
  class ParserStageConfig {
    <<record>>
  }
  class WorkflowConfig {
    <<config>>
  }
  StageLimits <|.. CheckerStageConfig
  StageLimits <|.. ParserStageConfig
  WorkflowConfig ..> StageLimits : uses
```

| Direction | Relation | Type | Summary |
|---|---|---|---|
| ← | implements | [CheckerStageConfig](io.github.tlaplus.hardening.config.CheckerStageConfig.md) | Capacity and resource limits for one model-checker stage. |
| ← | implements | [ParserStageConfig](io.github.tlaplus.hardening.config.ParserStageConfig.md) | Capacity and timeout limits for the parser stage. |
| ← | uses | [WorkflowConfig](io.github.tlaplus.hardening.config.WorkflowConfig.md) | Global and per-stage limits for the implemented fuzzing workflow. |
