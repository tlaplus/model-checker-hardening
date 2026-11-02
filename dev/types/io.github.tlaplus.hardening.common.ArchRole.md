# `ArchRole`

`io.github.tlaplus.hardening.common` · annotation · other · [source](https://github.com/tlaplus/model-checker-hardening/blob/2c6aced743b4276ca683c29709daab946d1b9536/src/main/java/io/github/tlaplus/hardening/common/ArchRole.java#L15) · module [common](../modules/common.md)

Declares the `Role` of a top-level type for the architecture atlas (ADR 0022).

**Decisions:** [ADR 0022: Architecture atlas](https://github.com/tlaplus/model-checker-hardening/blob/2c6aced743b4276ca683c29709daab946d1b9536/docs/decisions/0022-architecture-atlas.md)

```mermaid
classDiagram
  class ArchRole {
    <<annotation>>
  }
  class Role {
    <<enum>>
  }
  ArchRole ..> Role : uses
```

| Direction | Relation | Type | Summary |
|---|---|---|---|
| → | uses | [Role](io.github.tlaplus.hardening.common.Role.md) | What a type does in its package, as the architecture atlas shows it (ADR 0022). |
