# `IrTypes`

`io.github.tlaplus.hardening.gen.ir` · class · other · [source](https://github.com/tlaplus/model-checker-hardening/blob/6bd7d927f1cffd7642029a3d925dff47091ddde9/src/main/java/io/github/tlaplus/hardening/gen/ir/IrTypes.java#L13) · module [gen](../modules/gen.md)

Type tags of typed IR.

| Direction | Relation | Type | Summary |
|---|---|---|---|
| ← | depends | [LetSpecialization](io.github.tlaplus.hardening.gen.rewrite.LetSpecialization.md) | Specializes every LET definition of a rule's replacement to the types at which the replacement applies it. |
| ← | depends | [RuleInstantiation](io.github.tlaplus.hardening.gen.rewrite.RuleInstantiation.md) | Builds the replacement of a match (ADR 0017 §4): the rule's replacement with its own binders renamed apart, its types instantiated, its parameters substituted and its lambdas applied. |
