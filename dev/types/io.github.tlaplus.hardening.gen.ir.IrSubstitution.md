# `IrSubstitution`

`io.github.tlaplus.hardening.gen.ir` · class · utility · key · [source](https://github.com/tlaplus/model-checker-hardening/blob/7b97336d8ec341ec48f89de4a368b6a488a145ac/src/main/java/io/github/tlaplus/hardening/gen/ir/IrSubstitution.java#L33) · module [gen](../modules/gen.md)

Substitution and beta-reduction of typed IR.

| Direction | Relation | Type | Summary |
|---|---|---|---|
| → | depends | [IrBinding](io.github.tlaplus.hardening.gen.ir.IrBinding.md) | Where an operator binds names: which of its arguments introduce bound names, and which arguments see them. |
| → | depends | [IrNames](io.github.tlaplus.hardening.gen.ir.IrNames.md) | Names in typed IR: injective renaming and the names an expression reads from its context. |
| ← | depends | [RuleInstantiation](io.github.tlaplus.hardening.gen.rewrite.RuleInstantiation.md) | Builds the instance of a rule (ADR 0017 §4): the rule's replacement for a match, or its whole formula for an edge guard (ADR 0023 §6). |
