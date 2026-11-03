# `WorkflowProgressMonitor`

`io.github.tlaplus.hardening.workflow.execution` · class · other · [source](https://github.com/tlaplus/model-checker-hardening/blob/efb80f561357d9a62d3b24e493bb2f450ab6410c/src/main/java/io/github/tlaplus/hardening/workflow/execution/WorkflowProgressMonitor.java#L11) · module [workflow](../modules/workflow.md)

Periodically publishes serialized workflow snapshots without delaying stage workers.

```mermaid
classDiagram
  class WorkflowProgressMonitor
  class WorkflowProgress {
    <<record>>
  }
  WorkflowProgressMonitor --> WorkflowProgress : has
```

The diagram draws 1 of 3 relations; the table lists them all.

| Direction | Relation | Type | Summary |
|---|---|---|---|
| → | has | [WorkflowProgress](io.github.tlaplus.hardening.workflow.WorkflowProgress.md) | Best-effort snapshot of cumulative corpus statistics and current pending work. |
| → | depends | [Preconditions](io.github.tlaplus.hardening.common.Preconditions.md) | Shared validation of method and constructor arguments. |
| ← | depends | [WorkflowRunner](io.github.tlaplus.hardening.workflow.WorkflowRunner.md) | Runs generation, parsing, TLC, Apalache, conformance aggregation and the quality gate under one CPU budget. |
