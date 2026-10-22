# `PredefinedSet`

`io.github.tlaplus.hardening.signature` · enum · other · [source](https://github.com/tlaplus/model-checker-hardening/blob/0b7c167b636088dd43d36713d157e22f7ebd4f79/src/main/java/io/github/tlaplus/hardening/signature/PredefinedSet.java#L15) · module [signature](../modules/signature.md)

The predefined sets a pattern names by their TLA+ spelling.

```mermaid
classDiagram
  class PredefinedSet {
    <<enum>>
  }
  class IrPattern {
    <<contract>>
  }
  IrPattern ..> PredefinedSet : uses
```

The diagram draws 1 of 2 relations; the table lists them all.

| Direction | Relation | Type | Summary |
|---|---|---|---|
| ← | uses | [IrPattern](io.github.tlaplus.hardening.signature.IrPattern.md) | One pattern over the typed Apalache IR, as parsed by `PatternParser`. |
| ← | depends | [PatternParser](io.github.tlaplus.hardening.signature.PatternParser.md) | Parses the S-expression pattern syntax that the known-defect manual specifies: |
