# `CheckingPolicy`

`io.github.tlaplus.hardening.corpus` · record · policy · key · [source](https://github.com/tlaplus/model-checker-hardening/blob/6bbf8b66c375696d1798f7b7a061b81573a3f42a/src/main/java/io/github/tlaplus/hardening/corpus/CheckingPolicy.java#L18) · module [corpus](../modules/corpus.md)

Which checkers a workflow run feeds and how the aggregator judges their verdicts.

```mermaid
classDiagram
  class CheckingPolicy {
    <<policy>>
  }
  class CheckerSet {
    <<record>>
  }
  class ExpectedOutcomes {
    <<contract>>
  }
  class Oracle {
    <<policy>>
  }
  class AggregationInput {
    <<record>>
  }
  class AggregationRecovery
  class CorpusDirectory {
    <<facade>>
  }
  class WorkflowRunner {
    <<facade>>
  }
  class Technique {
    <<enum>>
  }
  class AggregationTransition
  CheckingPolicy --> CheckerSet : has
  CheckingPolicy --> ExpectedOutcomes : has
  CheckingPolicy --> Oracle : has
  AggregationInput --> CheckingPolicy : has
  AggregationRecovery --> CheckingPolicy : has
  CorpusDirectory --> CheckingPolicy : has
  WorkflowRunner --> CheckingPolicy : has
  CheckingPolicy ..> Technique : uses
  AggregationTransition ..> CheckingPolicy : uses
```

| Direction | Relation | Type | Summary |
|---|---|---|---|
| → | has | [CheckerSet](io.github.tlaplus.hardening.corpus.CheckerSet.md) | The checker branches a corpus runs: a non-empty set of checking stages, in stage order. |
| → | has | [ExpectedOutcomes](io.github.tlaplus.hardening.corpus.ExpectedOutcomes.md) | Predicts the outcome of one stored input, typically by replaying it through the generator (ADR 0023 §5). |
| → | has | [Oracle](io.github.tlaplus.hardening.corpus.Oracle.md) | How the aggregator judges the checker results of one entry (fuzzing-workflows.md §1.3, ADR 0016 §4, ADR 0019, ADR 0023 §5). |
| ← | has | [AggregationInput](io.github.tlaplus.hardening.corpus.AggregationInput.md) | A logical corpus entry whose non-crash checker results are ready for aggregation. |
| ← | has | [AggregationRecovery](io.github.tlaplus.hardening.corpus.AggregationRecovery.md) | Recovers aggregate commits and inventories the aggregator's result directories. |
| ← | has | [CorpusDirectory](io.github.tlaplus.hardening.corpus.CorpusDirectory.md) | Owns the on-disk layout and integrity checks for one FuzzTLA corpus. |
| ← | has | [WorkflowRunner](io.github.tlaplus.hardening.workflow.WorkflowRunner.md) | Runs generation, parsing, TLC, Apalache, conformance aggregation and the quality gate under one CPU budget. |
| → | uses | [Technique](io.github.tlaplus.hardening.corpus.Technique.md) | The fuzzing technique a corpus runs: `[workflow] technique`, which `fuzztla init --how` sets (ADR 0025 §1), so every run and every replay decodes and judges its entries the same way. |
| ← | uses | [AggregationTransition](io.github.tlaplus.hardening.corpus.AggregationTransition.md) | Joins the checker copies of one logical input into one durable aggregator result. |
