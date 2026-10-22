# `IrBinding`

`io.github.tlaplus.hardening.gen.ir` · enum · other · [source](https://github.com/tlaplus/model-checker-hardening/blob/a304ba37155136c3ba79e4f2fb414cbb47d01606/src/main/java/io/github/tlaplus/hardening/gen/ir/IrBinding.java#L25) · module [gen](../modules/gen.md)

Where an operator binds names: which of its arguments introduce bound names, and which arguments see them.

| Direction | Relation | Type | Summary |
|---|---|---|---|
| ← | depends | [IrAlpha](io.github.tlaplus.hardening.gen.ir.IrAlpha.md) | Alpha-equivalence of typed IR: two expressions are equivalent when they differ only in the names of the variables and definitions they bind. |
| ← | depends | [IrNames](io.github.tlaplus.hardening.gen.ir.IrNames.md) | Names in typed IR: injective renaming and the names an expression reads from its context. |
| ← | depends | [IrSubstitution](io.github.tlaplus.hardening.gen.ir.IrSubstitution.md) | Substitution and beta-reduction of typed IR. |
| ← | depends | [LibraryExpressions](io.github.tlaplus.hardening.gen.library.LibraryExpressions.md) | Lexical dependency analysis of library IR. |
| ← | depends | [LabelPlacement](io.github.tlaplus.hardening.gen.rewrite.LabelPlacement.md) | Where a rule's replacement may not carry a label of the node it rewrites. |
| ← | depends | [RewritePositions](io.github.tlaplus.hardening.gen.rewrite.RewritePositions.md) | Where the rewriter may apply a rule. |
| ← | depends | [Rewriter](io.github.tlaplus.hardening.gen.rewrite.Rewriter.md) | Decodes a rewrite payload into a rewrite of a base (ADR 0016 §2). |
| ← | depends | [RuleContract](io.github.tlaplus.hardening.gen.rewrite.RuleContract.md) | Reads one rule definition and checks the parts of the rule contract that the loader can decide (ADR 0017 §2, §3, §5): the shape `A = B` or `A &lt;=&gt; B`, the parameter classes, level preservation and closedness. |
| ← | depends | [RuleMatcher](io.github.tlaplus.hardening.gen.rewrite.RuleMatcher.md) | Matches a rule's pattern at one node (ADR 0017 §4). |
