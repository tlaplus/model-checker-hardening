# `IrTypes`

`io.github.tlaplus.hardening.gen.ir` · class · other · [source](https://github.com/tlaplus/model-checker-hardening/blob/70629b77cdc32904ca4bee1787c9d1446340bd90/src/main/java/io/github/tlaplus/hardening/gen/ir/IrTypes.java#L13) · module [gen](../modules/gen.md)

Type tags of typed IR.

| Direction | Relation | Type | Summary |
|---|---|---|---|
| ← | depends | [LetSpecialization](io.github.tlaplus.hardening.gen.rewrite.LetSpecialization.md) | Specializes every LET definition of a rule's replacement to the types at which the replacement applies it. |
| ← | depends | [RuleInstantiation](io.github.tlaplus.hardening.gen.rewrite.RuleInstantiation.md) | Builds the instance of a rule (ADR 0017 §4): the rule's replacement for a match, or its whole formula for an edge guard (ADR 0023 §6). |
