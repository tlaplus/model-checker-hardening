# `LetSpecialization`

`io.github.tlaplus.hardening.gen.rewrite` · class · other · [source](https://github.com/tlaplus/model-checker-hardening/blob/a304ba37155136c3ba79e4f2fb414cbb47d01606/src/main/java/io/github/tlaplus/hardening/gen/rewrite/LetSpecialization.java#L28) · module [gen](../modules/gen.md)

Specializes every LET definition of a rule's replacement to the types at which the replacement applies it.

| Direction | Relation | Type | Summary |
|---|---|---|---|
| → | depends | [IrTypes](io.github.tlaplus.hardening.gen.ir.IrTypes.md) | Type tags of typed IR. |
| → | depends | [RuleContract](io.github.tlaplus.hardening.gen.rewrite.RuleContract.md) | Reads one rule definition and checks the parts of the rule contract that the loader can decide (ADR 0017 §2, §3, §5): the shape `A = B` or `A &lt;=&gt; B`, the parameter classes, level preservation and closedness. |
| ← | depends | [RuleContract](io.github.tlaplus.hardening.gen.rewrite.RuleContract.md) | Reads one rule definition and checks the parts of the rule contract that the loader can decide (ADR 0017 §2, §3, §5): the shape `A = B` or `A &lt;=&gt; B`, the parameter classes, level preservation and closedness. |
