# `ToolWorkerRuntime`

`io.github.tlaplus.hardening.workflow.worker` · class · process · key · [source](https://github.com/tlaplus/model-checker-hardening/blob/4c5cb469fa5cdcada2cd5740bce130b825b35343/src/main/java/io/github/tlaplus/hardening/workflow/worker/ToolWorkerRuntime.java#L25) · module [workflow](../modules/workflow.md)

The part of a child worker that is the same for every tool: report an escaped failure by exiting non-zero, announce itself to the parent, serve inputs until the parent stops asking, write each input where the tool reads it, and keep a bounded record of what the tool printed.

```mermaid
classDiagram
  class ToolWorkerRuntime {
    <<process>>
  }
  class ThrowingRunnable {
    <<interface>>
  }
  class BoundedTextOutputStream
  class ToolInput {
    <<model>>
  }
  class ToolResult {
    <<model>>
  }
  class ToolWorkerConnection
  ToolWorkerRuntime ..> ThrowingRunnable : uses
  ToolWorkerRuntime ..> BoundedTextOutputStream : uses
  ToolWorkerRuntime ..> ToolInput : uses
  ToolWorkerRuntime ..> ToolResult : uses
  ToolWorkerRuntime ..> ToolWorkerConnection : uses
```

The diagram draws 5 of 11 relations; the table lists them all.

| Direction | Relation | Type | Summary |
|---|---|---|---|
| → | uses | [ThrowingRunnable](io.github.tlaplus.hardening.common.ThrowingRunnable.md) | An operation that takes no value and may fail with a checked exception. |
| → | uses | [BoundedTextOutputStream](io.github.tlaplus.hardening.workflow.worker.BoundedTextOutputStream.md) | Retains a bounded UTF-8 byte prefix while discarding subsequent output. |
| → | uses | [ToolInput](io.github.tlaplus.hardening.workflow.worker.ToolInput.md) | One rendered specification and what the receiving checker is asked about it. |
| → | uses | [ToolResult](io.github.tlaplus.hardening.workflow.worker.ToolResult.md) | One request result returned by an isolated tool worker. |
| → | uses | [ToolWorkerConnection](io.github.tlaplus.hardening.workflow.worker.ToolWorkerConnection.md) | Child-side connection to the parent's private worker-protocol listener. |
| → | depends | [StageOutcome](io.github.tlaplus.hardening.workflow.worker.StageOutcome.md) | Common result of processing one input in a checking stage. |
| → | depends | [ToolThreadStacks](io.github.tlaplus.hardening.workflow.worker.ToolThreadStacks.md) | The stacks of the threads a stopped worker was working on, for the diagnostic of a timeout (ADR 0021). |
| → | depends | [ToolWorkerProtocol](io.github.tlaplus.hardening.workflow.worker.ToolWorkerProtocol.md) | Binary framing shared by isolated parser and model-checker workers. |
| ← | depends | [ApalacheWorkerMain](io.github.tlaplus.hardening.workflow.apalache.ApalacheWorkerMain.md) | Persistent child process that invokes Apalache's `Tool.run` sequentially. |
| ← | depends | [ParserWorkerMain](io.github.tlaplus.hardening.workflow.parser.ParserWorkerMain.md) | Child-process entry point for persistent isolated SANY requests. |
| ← | depends | [TlcWorkerMain](io.github.tlaplus.hardening.workflow.tlc.TlcWorkerMain.md) | One-shot TLC child process. |
