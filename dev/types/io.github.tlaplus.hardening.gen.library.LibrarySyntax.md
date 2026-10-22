# `LibrarySyntax`

`io.github.tlaplus.hardening.gen.library` · enum · other · [source](https://github.com/tlaplus/model-checker-hardening/blob/4f182776ab66c4f93ff45cb83dfcd11e34fa32d1/src/main/java/io/github/tlaplus/hardening/gen/library/LibrarySyntax.java#L15) · module [gen](../modules/gen.md)

Supported imported IR syntax and its capabilities; `IrBinding` gives its binding layout.

```mermaid
classDiagram
  class LibrarySyntax {
    <<enum>>
  }
  class ExpressionCategory {
    <<enum>>
  }
  LibrarySyntax --> ExpressionCategory : has
```

The diagram draws 1 of 2 relations; the table lists them all.

| Direction | Relation | Type | Summary |
|---|---|---|---|
| → | has | [ExpressionCategory](io.github.tlaplus.hardening.gen.ExpressionCategory.md) | A user-facing category assigned to generated expression forms. |
| ← | depends | [LibraryExpressions](io.github.tlaplus.hardening.gen.library.LibraryExpressions.md) | Lexical dependency analysis of library IR. |
