# `RuleParameter`

`io.github.tlaplus.hardening.gen.rewrite` · record · other · [source](https://github.com/tlaplus/model-checker-hardening/blob/39531345378d8db5a429220f50aed4254ea670fb/src/main/java/io/github/tlaplus/hardening/gen/rewrite/RuleParameter.java#L13) · module [gen](../modules/gen.md)

One formal parameter of a rewrite rule, classified by where it occurs (ADR 0017 §3).

**Decisions:** [ADR 0017: Rewrite rules as a TLA+ library](https://github.com/tlaplus/model-checker-hardening/blob/39531345378d8db5a429220f50aed4254ea670fb/docs/decisions/0017-rewrite-rule-library.md)

```mermaid
classDiagram
  class RuleParameter {
    <<record>>
  }
  class RewriteRule {
    <<model>>
  }
  class AssignmentAnalysis
  class LevelAnalysis
  class RuleContract
  class RuleOperands
  RewriteRule --> RuleParameter : has
  AssignmentAnalysis ..> RuleParameter : uses
  LevelAnalysis ..> RuleParameter : uses
  RuleContract ..> RuleParameter : uses
  RuleOperands ..> RuleParameter : uses
```

The diagram draws 5 of 9 relations; the table lists them all.

| Direction | Relation | Type | Summary |
|---|---|---|---|
| ← | has | [RewriteRule](io.github.tlaplus.hardening.gen.rewrite.RewriteRule.md) | One rewrite rule `F(p1, ..., pn) == A = B` or `A &lt;=&gt; B` of a rule module (ADR 0017 §2): the rewriter replaces an instance of `pattern` (A) with the same instance of ` replacement` (B). |
| ← | uses | [AssignmentAnalysis](io.github.tlaplus.hardening.gen.rewrite.AssignmentAnalysis.md) | Computes the `AssignmentFacts` of a rule (ADR 0017 §5.3): where its Boolean parameters stand in TLC's assignment positions, on each side and in each action (ADR 0027 §1). |
| ← | uses | [LevelAnalysis](io.github.tlaplus.hardening.gen.rewrite.LevelAnalysis.md) | Computes the `RuleLevels` of a rule as its loader checks it (ADR 0027 §1). |
| ← | uses | [RuleContract](io.github.tlaplus.hardening.gen.rewrite.RuleContract.md) | Reads one rule definition and checks the parts of the rule contract that the loader can decide (ADR 0017 §2, §3, §5, ADR 0027 §1): the shape `A = B` or `A &lt;=&gt; B`, the parameter classes, level preservation and closedness. |
| ← | uses | [RuleOperands](io.github.tlaplus.hardening.gen.rewrite.RuleOperands.md) | Draws what instantiating a rule needs beyond a match (ADR 0017 §3): a concrete type for every type variable the match leaves open, then a value for each drawn parameter at its type. |
| ← | depends | [Rewriter](io.github.tlaplus.hardening.gen.rewrite.Rewriter.md) | Decodes a rewrite payload into a rewrite of a base (ADR 0016 §2). |
| ← | depends | [RuleInstantiation](io.github.tlaplus.hardening.gen.rewrite.RuleInstantiation.md) | Builds the instance of a rule (ADR 0017 §4): the rule's replacement for a match, or its whole formula for an edge guard (ADR 0023 §6). |
| ← | depends | [RuleMatcher](io.github.tlaplus.hardening.gen.rewrite.RuleMatcher.md) | Matches a rule's pattern at one node (ADR 0017 §4). |
| ← | depends | [Tautologies](io.github.tlaplus.hardening.gen.rewrite.Tautologies.md) | Draws edge guards of a graph module from a rule library (ADR 0023 §6): a guard is the formula `A = B` or `A &lt;=&gt; B` of a rule, with every parameter drawn at its type. |
