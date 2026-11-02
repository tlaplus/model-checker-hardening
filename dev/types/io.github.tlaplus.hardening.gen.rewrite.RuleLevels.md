# `RuleLevels`

`io.github.tlaplus.hardening.gen.rewrite` · record · other · [source](https://github.com/tlaplus/model-checker-hardening/blob/2010568d15d7d61fb6db265b00c968ea0aa49f9e/src/main/java/io/github/tlaplus/hardening/gen/rewrite/RuleLevels.java#L16) · module [gen](../modules/gen.md)

What the levels of a temporal rule require of a match (ADR 0027 §1).

**Decisions:** [ADR 0027: Metamorphic testing of temporal properties](https://github.com/tlaplus/model-checker-hardening/blob/2010568d15d7d61fb6db265b00c968ea0aa49f9e/docs/decisions/0027-temporal-metamorphic-testing.md)

```mermaid
classDiagram
  class RuleLevels {
    <<record>>
  }
  class RewriteRule {
    <<model>>
  }
  RewriteRule --> RuleLevels : has
```

The diagram draws 1 of 3 relations; the table lists them all.

| Direction | Relation | Type | Summary |
|---|---|---|---|
| ← | has | [RewriteRule](io.github.tlaplus.hardening.gen.rewrite.RewriteRule.md) | One rewrite rule `F(p1, ..., pn) == A = B` or `A &lt;=&gt; B` of a rule module (ADR 0017 §2): the rewriter replaces an instance of `pattern` (A) with the same instance of ` replacement` (B). |
| ← | depends | [RuleContract](io.github.tlaplus.hardening.gen.rewrite.RuleContract.md) | Reads one rule definition and checks the parts of the rule contract that the loader can decide (ADR 0017 §2, §3, §5, ADR 0027 §1): the shape `A = B` or `A &lt;=&gt; B`, the parameter classes, level preservation and closedness. |
| ← | depends | [RuleMatcher](io.github.tlaplus.hardening.gen.rewrite.RuleMatcher.md) | Matches a rule's pattern at one node (ADR 0017 §4). |
