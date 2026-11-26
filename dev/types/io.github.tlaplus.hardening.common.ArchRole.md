# `ArchRole`

`io.github.tlaplus.hardening.common` · annotation · other · [source](https://github.com/tlaplus/model-checker-hardening/blob/dc773e71f8f182a2870398126f091b88fadea49d/src/main/java/io/github/tlaplus/hardening/common/ArchRole.java#L15) · module [common](../modules/common.md)

Declares the `Role` of a top-level type for the architecture atlas (ADR 0022).

**Decisions:** [ADR 0022: Architecture atlas](https://github.com/tlaplus/model-checker-hardening/blob/dc773e71f8f182a2870398126f091b88fadea49d/docs/decisions/0022-architecture-atlas.md)

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
