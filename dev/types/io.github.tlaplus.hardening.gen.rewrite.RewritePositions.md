# `RewritePositions`

`io.github.tlaplus.hardening.gen.rewrite` · class · other · [source](https://github.com/tlaplus/model-checker-hardening/blob/364baf41a0dd399c6d0563acc88702569b58407d/src/main/java/io/github/tlaplus/hardening/gen/rewrite/RewritePositions.java#L40) · module [gen](../modules/gen.md)

Where the rewriter may apply a rule.

**Decisions:** [ADR 0027: Metamorphic testing of temporal properties](https://github.com/tlaplus/model-checker-hardening/blob/364baf41a0dd399c6d0563acc88702569b58407d/docs/decisions/0027-temporal-metamorphic-testing.md)

| Direction | Relation | Type | Summary |
|---|---|---|---|
| → | depends | [IrBinding](io.github.tlaplus.hardening.gen.ir.IrBinding.md) | Where an operator binds names: which of its arguments introduce bound names, and which arguments see them. |
| → | depends | [FormulaLevels](io.github.tlaplus.hardening.gen.rewrite.FormulaLevels.md) | The levels of formulas, as SANY checks them, that rules depend on (ADR 0017 §5.2, ADR 0027 §1, ir-generators.md §7.1). |
| ← | depends | [LabelPlacement](io.github.tlaplus.hardening.gen.rewrite.LabelPlacement.md) | Where a rule's replacement may not carry a label of the node it rewrites. |
| ← | depends | [Rewriter](io.github.tlaplus.hardening.gen.rewrite.Rewriter.md) | Decodes a rewrite payload into a rewrite of a base (ADR 0016 §2). |
