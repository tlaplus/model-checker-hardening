# `GenericPlaceholder`

`io.github.tlaplus.hardening.config` · enum · other · [source](https://github.com/tlaplus/model-checker-hardening/blob/e4ab65335fef2f7aa9e835e4805d8e294545cba2/src/main/java/io/github/tlaplus/hardening/config/GenericPlaceholder.java#L16) · module [config](../modules/config.md)

A placeholder in `[generic] arguments` (ADR 0033 §3), written `{name}`.

**Decisions:** [ADR 0033: A generic checker for TLC- and Apalache-compatible tools](https://github.com/tlaplus/model-checker-hardening/blob/e4ab65335fef2f7aa9e835e4805d8e294545cba2/docs/decisions/0033-generic-checker.md)

| Direction | Relation | Type | Summary |
|---|---|---|---|
| ← | depends | [GenericCheckerConfig](io.github.tlaplus.hardening.config.GenericCheckerConfig.md) | The `[generic]` table (ADR 0033 §1): a model checker that claims compatibility with TLC or Apalache, run as a native process for each input. |
