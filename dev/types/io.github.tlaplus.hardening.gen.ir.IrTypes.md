# `IrTypes`

`io.github.tlaplus.hardening.gen.ir` · class · other · [source](https://github.com/tlaplus/model-checker-hardening/blob/24ed0403c7b9dcb03d020ba52a985ca0f9e882f2/src/main/java/io/github/tlaplus/hardening/gen/ir/IrTypes.java#L13) · module [gen](../modules/gen.md)

Type tags of typed IR.

| Direction | Relation | Type | Summary |
|---|---|---|---|
| ← | depends | [LetSpecialization](io.github.tlaplus.hardening.gen.rewrite.LetSpecialization.md) | Specializes every LET definition of a rule's replacement to the types at which the replacement applies it. |
| ← | depends | [RuleInstantiation](io.github.tlaplus.hardening.gen.rewrite.RuleInstantiation.md) | Builds the instance of a rule (ADR 0017 §4): the rule's replacement for a match, or its whole formula for an edge guard (ADR 0023 §6). |
