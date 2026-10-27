# `PredefinedSet`

`io.github.tlaplus.hardening.signature` · enum · other · [source](https://github.com/tlaplus/model-checker-hardening/blob/3b201567990514c2a43b90dca521dbe32af37023/src/main/java/io/github/tlaplus/hardening/signature/PredefinedSet.java#L15) · module [signature](../modules/signature.md)

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
