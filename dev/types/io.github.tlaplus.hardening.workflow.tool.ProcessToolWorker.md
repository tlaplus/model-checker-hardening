# `ProcessToolWorker`

`io.github.tlaplus.hardening.workflow.tool` · class · adapter · key · [source](https://github.com/tlaplus/model-checker-hardening/blob/da386b24fc0ea1f5a200a0d74df0b119a27ca0ba/src/main/java/io/github/tlaplus/hardening/workflow/tool/ProcessToolWorker.java#L14) · module [workflow](../modules/workflow.md)

A tool worker served by one isolated child process until a crash verdict retires it.

```mermaid
classDiagram
  class ProcessToolWorker {
    <<adapter>>
  }
  class ToolWorker {
    <<contract>>
  }
  class IsolatedWorkerProcess {
    <<adapter>>
  }
  class ToolInput {
    <<model>>
  }
  class ToolResult {
    <<model>>
  }
  ToolWorker <|.. ProcessToolWorker
  ProcessToolWorker --> IsolatedWorkerProcess : has
  ProcessToolWorker ..> ToolInput : uses
  ProcessToolWorker ..> ToolResult : uses
```

The diagram draws 4 of 6 relations; the table lists them all.

| Direction | Relation | Type | Summary |
|---|---|---|---|
| → | implements | [ToolWorker](io.github.tlaplus.hardening.workflow.tool.ToolWorker.md) | Worker-local tool state used for one or more sequential inputs. |
| → | has | [IsolatedWorkerProcess](io.github.tlaplus.hardening.workflow.worker.IsolatedWorkerProcess.md) | Parent-side supervisor for one isolated Java tool worker. |
| → | uses | [ToolInput](io.github.tlaplus.hardening.workflow.worker.ToolInput.md) | One rendered specification and what the receiving checker is asked about it. |
| → | uses | [ToolResult](io.github.tlaplus.hardening.workflow.worker.ToolResult.md) | One request result returned by an isolated tool worker. |
| → | depends | [WorkflowException](io.github.tlaplus.hardening.workflow.WorkflowException.md) | Reports an infrastructure or lifecycle failure in a fuzzing workflow. |
| ← | depends | [ParserBackend](io.github.tlaplus.hardening.workflow.parser.ParserBackend.md) | Runs SANY in persistent isolated JVMs, one per stage worker, each replaced after a crash. |
