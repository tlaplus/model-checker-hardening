# `RunView`

`io.github.tlaplus.hardening.cli` · record · view · key · [source](https://github.com/tlaplus/model-checker-hardening/blob/b1d642d707df18a7597ce87d7e826c837e78f842/src/main/java/io/github/tlaplus/hardening/cli/RunView.java#L21) · module [cli](../modules/cli.md)

The statistics shared by live and final reports, without layout or terminal state.

```mermaid
classDiagram
  class RunView {
    <<view>>
  }
  class CorpusStage {
    <<model>>
  }
  class CorpusEntryCounts {
    <<record>>
  }
  class GeneratorSummary {
    <<record>>
  }
  class StageVerdictSummary {
    <<record>>
  }
  class RunMetric {
    <<interface>>
  }
  class RunValue {
    <<interface>>
  }
  class WorkflowProgress {
    <<record>>
  }
  class WorkflowRunSummary {
    <<record>>
  }
  RunView --> CorpusStage : has
  RunView --> CorpusEntryCounts : has
  RunView --> GeneratorSummary : has
  RunView --> StageVerdictSummary : has
  RunView ..> RunMetric : uses
  RunView ..> RunValue : uses
  RunView ..> WorkflowProgress : uses
  RunView ..> WorkflowRunSummary : uses
  RunMetric ..> RunView : uses
```

The diagram draws 9 of 13 relations; the table lists them all.

| Direction | Relation | Type | Summary |
|---|---|---|---|
| → | has | [CorpusStage](io.github.tlaplus.hardening.corpus.CorpusStage.md) | One implemented stage of the fuzzing pipeline, and the corpus directories it owns. |
| → | has | [CorpusEntryCounts](io.github.tlaplus.hardening.workflow.CorpusEntryCounts.md) | How many entries a corpus holds, split the way `workflow.max_entries` counts them (ADR 0034 §6): `sized` entries share the generations' sizes and are bounded by `maximum`, while `replays` come on top of them and are not. |
| → | has | [GeneratorSummary](io.github.tlaplus.hardening.workflow.execution.GeneratorSummary.md) | Cumulative generation statistics decorated with invocation-facing replay and timing fields. |
| → | has | [StageVerdictSummary](io.github.tlaplus.hardening.workflow.execution.StageVerdictSummary.md) | Cumulative verdict counters and summed worker elapsed time for one stage. |
| → | uses | [RunMetric](io.github.tlaplus.hardening.cli.RunMetric.md) | Stable field identity, independent of screen coordinates or formatted text. |
| → | uses | [RunValue](io.github.tlaplus.hardening.cli.RunValue.md) | Unrounded, typed values used both for formatting and change comparison. |
| → | uses | [WorkflowProgress](io.github.tlaplus.hardening.workflow.WorkflowProgress.md) | Best-effort snapshot of cumulative corpus statistics and current pending work. |
| → | uses | [WorkflowRunSummary](io.github.tlaplus.hardening.workflow.WorkflowRunSummary.md) | Cumulative corpus statistics and stopping reason after one complete workflow invocation. |
| ← | uses | [RunMetric](io.github.tlaplus.hardening.cli.RunMetric.md) | Stable field identity, independent of screen coordinates or formatted text. |
| → | depends | [CorpusInventory](io.github.tlaplus.hardening.corpus.CorpusInventory.md) | Immutable startup inventory used to seed stage queues and capacities. |
| → | depends | [CorpusVerdict](io.github.tlaplus.hardening.corpus.CorpusVerdict.md) | A verdict supported by the implemented corpus stages. |
| ← | depends | [RunTable](io.github.tlaplus.hardening.cli.RunTable.md) | Complete, exact statistics grouped by stage; suitable for scrollback and redirected output. |
| ← | depends | [TerminalProgressDisplay](io.github.tlaplus.hardening.cli.TerminalProgressDisplay.md) | Terminal mechanics of live progress: snapshots, change highlighting, resizing, and screen restoration. |
