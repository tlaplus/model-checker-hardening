# `LibrarySyntax`

`io.github.tlaplus.hardening.gen.library` · enum · other · [source](https://github.com/tlaplus/model-checker-hardening/blob/af09a9e32eb710979ef0c76f29507ce1ffe85a15/src/main/java/io/github/tlaplus/hardening/gen/library/LibrarySyntax.java#L15) · module [gen](../modules/gen.md)

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
