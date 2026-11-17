# `Role`

`io.github.tlaplus.hardening.common` · enum · other · [source](https://github.com/tlaplus/model-checker-hardening/blob/a410726a3386c3c9938acd3065b88cce8664b5bf/src/main/java/io/github/tlaplus/hardening/common/Role.java#L9) · module [common](../modules/common.md)

What a type does in its package, as the architecture atlas shows it (ADR 0022).

**Decisions:** [ADR 0022: Architecture atlas](https://github.com/tlaplus/model-checker-hardening/blob/a410726a3386c3c9938acd3065b88cce8664b5bf/docs/decisions/0022-architecture-atlas.md)

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
