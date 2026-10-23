# `PredefinedSet`

`io.github.tlaplus.hardening.signature` · enum · other · [source](https://github.com/tlaplus/model-checker-hardening/blob/af09a9e32eb710979ef0c76f29507ce1ffe85a15/src/main/java/io/github/tlaplus/hardening/signature/PredefinedSet.java#L15) · module [signature](../modules/signature.md)

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
