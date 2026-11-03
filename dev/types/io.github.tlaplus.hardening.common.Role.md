# `Role`

`io.github.tlaplus.hardening.common` · enum · other · [source](https://github.com/tlaplus/model-checker-hardening/blob/7b97336d8ec341ec48f89de4a368b6a488a145ac/src/main/java/io/github/tlaplus/hardening/common/Role.java#L9) · module [common](../modules/common.md)

What a type does in its package, as the architecture atlas shows it (ADR 0022).

**Decisions:** [ADR 0022: Architecture atlas](https://github.com/tlaplus/model-checker-hardening/blob/7b97336d8ec341ec48f89de4a368b6a488a145ac/docs/decisions/0022-architecture-atlas.md)

```mermaid
classDiagram
  class Role {
    <<enum>>
  }
  class ArchRole {
    <<annotation>>
  }
  ArchRole ..> Role : uses
```

| Direction | Relation | Type | Summary |
|---|---|---|---|
| ← | uses | [ArchRole](io.github.tlaplus.hardening.common.ArchRole.md) | Declares the `Role` of a top-level type for the architecture atlas (ADR 0022). |
