# `KnownDefect`

`io.github.tlaplus.hardening.signature` · class · model · key · [source](https://github.com/tlaplus/model-checker-hardening/blob/6bbf8b66c375696d1798f7b7a061b81573a3f42a/src/main/java/io/github/tlaplus/hardening/signature/KnownDefect.java#L19) · module [signature](../modules/signature.md)

One known-defect signature: a documented tool defect and the IR shapes that trigger it.

```mermaid
classDiagram
  class KnownDefect {
    <<model>>
  }
  class DefectStatus {
    <<enum>>
  }
  class IrPattern {
    <<contract>>
  }
  class KnownDefectDatabase {
    <<facade>>
  }
  class KnownDefectMatch {
    <<model>>
  }
  class KnownDefectDatabaseReader {
    <<codec>>
  }
  KnownDefect --> DefectStatus : has
  KnownDefect --> IrPattern : has
  KnownDefectDatabase --> KnownDefect : has
  KnownDefectMatch --> KnownDefect : has
  KnownDefectDatabaseReader ..> KnownDefect : uses
```

The diagram draws 5 of 9 relations; the table lists them all.

| Direction | Relation | Type | Summary |
|---|---|---|---|
| → | has | [DefectStatus](io.github.tlaplus.hardening.signature.DefectStatus.md) | Whether the defect behind a signature is still present. |
| → | has | [IrPattern](io.github.tlaplus.hardening.signature.IrPattern.md) | One pattern over the typed Apalache IR, as parsed by `PatternParser`. |
| ← | has | [KnownDefectDatabase](io.github.tlaplus.hardening.signature.KnownDefectDatabase.md) | The known-defect signatures of one invocation, in database order. |
| ← | has | [KnownDefectMatch](io.github.tlaplus.hardening.signature.KnownDefectMatch.md) | A signature that matches a module, and the outermost subexpression it matched first. |
| ← | uses | [KnownDefectDatabaseReader](io.github.tlaplus.hardening.signature.KnownDefectDatabaseReader.md) | Reads one database file: a TOML document of `[[signature]]` tables with exactly the keys `id`, `references`, `description`, `status`, and `match`. |
| → | depends | [Preconditions](io.github.tlaplus.hardening.common.Preconditions.md) | Shared validation of method and constructor arguments. |
| → | depends | [Bindings](io.github.tlaplus.hardening.signature.Bindings.md) | The metavariables and type variables one pattern alternative has bound so far. |
| ← | depends | [KnownDefectReport](io.github.tlaplus.hardening.cli.KnownDefectReport.md) | Renders the known-defect signatures that match one decoded input: each signature's id, description, status, and references, and the subexpression it matched first, as TLA+. |
| ← | depends | [InputAdmission](io.github.tlaplus.hardening.workflow.input.InputAdmission.md) | Decides whether a decoded candidate enters `00-inputs`, and where a known defect goes. |
