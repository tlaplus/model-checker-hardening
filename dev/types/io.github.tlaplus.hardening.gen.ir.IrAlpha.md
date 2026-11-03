# `IrAlpha`

`io.github.tlaplus.hardening.gen.ir` · class · utility · key · [source](https://github.com/tlaplus/model-checker-hardening/blob/efb80f561357d9a62d3b24e493bb2f450ab6410c/src/main/java/io/github/tlaplus/hardening/gen/ir/IrAlpha.java#L27) · module [gen](../modules/gen.md)

Alpha-equivalence of typed IR: two expressions are equivalent when they differ only in the names of the variables and definitions they bind.

| Direction | Relation | Type | Summary |
|---|---|---|---|
| → | depends | [IrBinding](io.github.tlaplus.hardening.gen.ir.IrBinding.md) | Where an operator binds names: which of its arguments introduce bound names, and which arguments see them. |
| ← | depends | [Rewriter](io.github.tlaplus.hardening.gen.rewrite.Rewriter.md) | Decodes a rewrite payload into a rewrite of a base (ADR 0016 §2). |
| ← | depends | [RuleMatcher](io.github.tlaplus.hardening.gen.rewrite.RuleMatcher.md) | Matches a rule's pattern at one node (ADR 0017 §4). |
