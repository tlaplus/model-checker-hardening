# `RunMode`

`io.github.tlaplus.hardening.workflow` · enum · other · [source](https://github.com/tlaplus/model-checker-hardening/blob/7b97336d8ec341ec48f89de4a368b6a488a145ac/src/main/java/io/github/tlaplus/hardening/workflow/RunMode.java#L7) · module [workflow](../modules/workflow.md)

Whether a run may extend a corpus that an earlier run has written (ADR 0026).

**Decisions:** [ADR 0026: Reproducible runs: seeds chosen at `init`, `run --continue`](https://github.com/tlaplus/model-checker-hardening/blob/7b97336d8ec341ec48f89de4a368b6a488a145ac/docs/decisions/0026-reproducible-runs.md)

```mermaid
classDiagram
  class RunMode {
    <<enum>>
  }
  class RunOutput
  class WorkflowRunner {
    <<facade>>
  }
  RunOutput ..> RunMode : uses
  WorkflowRunner ..> RunMode : uses
```

The diagram draws 2 of 3 relations; the table lists them all.

| Direction | Relation | Type | Summary |
|---|---|---|---|
| ← | uses | [RunOutput](io.github.tlaplus.hardening.cli.RunOutput.md) | The output of one run: the command's configured writer and an optional live display in front of it. |
| ← | uses | [WorkflowRunner](io.github.tlaplus.hardening.workflow.WorkflowRunner.md) | Runs generation, parsing, TLC, Apalache, conformance aggregation and the quality gate under one CPU budget. |
| ← | depends | [RunCommand](io.github.tlaplus.hardening.cli.RunCommand.md) | `fuzztla run`: reads a corpus configuration and runs `WorkflowRunner` with live progress output. |
