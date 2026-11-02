# `FormulaLevels`

`io.github.tlaplus.hardening.gen.rewrite` · class · other · [source](https://github.com/tlaplus/model-checker-hardening/blob/2010568d15d7d61fb6db265b00c968ea0aa49f9e/src/main/java/io/github/tlaplus/hardening/gen/rewrite/FormulaLevels.java#L21) · module [gen](../modules/gen.md)

The levels of formulas, as SANY checks them, that rules depend on (ADR 0017 §5.2, ADR 0027 §1, ir-generators.md §7.1).

**Decisions:** [ADR 0017: Rewrite rules as a TLA+ library](https://github.com/tlaplus/model-checker-hardening/blob/2010568d15d7d61fb6db265b00c968ea0aa49f9e/docs/decisions/0017-rewrite-rule-library.md), [ADR 0027: Metamorphic testing of temporal properties](https://github.com/tlaplus/model-checker-hardening/blob/2010568d15d7d61fb6db265b00c968ea0aa49f9e/docs/decisions/0027-temporal-metamorphic-testing.md)

| Direction | Relation | Type | Summary |
|---|---|---|---|
| ← | depends | [RuleContract](io.github.tlaplus.hardening.gen.rewrite.RuleContract.md) | Reads one rule definition and checks the parts of the rule contract that the loader can decide (ADR 0017 §2, §3, §5, ADR 0027 §1): the shape `A = B` or `A &lt;=&gt; B`, the parameter classes, level preservation and closedness. |
| ← | depends | [RuleMatcher](io.github.tlaplus.hardening.gen.rewrite.RuleMatcher.md) | Matches a rule's pattern at one node (ADR 0017 §4). |
