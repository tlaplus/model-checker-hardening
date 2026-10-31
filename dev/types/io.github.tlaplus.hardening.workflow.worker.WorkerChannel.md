# `WorkerChannel`

`io.github.tlaplus.hardening.workflow.worker` · class · adapter · key · [source](https://github.com/tlaplus/model-checker-hardening/blob/5b16596dd480095d323ac67ff0f85eb85227b4b5/src/main/java/io/github/tlaplus/hardening/workflow/worker/WorkerChannel.java#L26) · module [workflow](../modules/workflow.md)

The parent's end of one worker's control channel: message framing, and the deadline every read runs under.

```mermaid
classDiagram
  class WorkerChannel {
    <<adapter>>
  }
  class IsolatedWorkerProcess {
    <<adapter>>
  }
  class CheckRequest {
    <<record>>
  }
  class ToolResult {
    <<model>>
  }
  class WorkerLaunch {
    <<process>>
  }
  IsolatedWorkerProcess --> WorkerChannel : has
  WorkerChannel ..> CheckRequest : uses
  WorkerChannel ..> ToolResult : uses
  WorkerLaunch ..> WorkerChannel : uses
```

The diagram draws 4 of 5 relations; the table lists them all.

| Direction | Relation | Type | Summary |
|---|---|---|---|
| ← | has | [IsolatedWorkerProcess](io.github.tlaplus.hardening.workflow.worker.IsolatedWorkerProcess.md) | Parent-side supervisor for one isolated Java tool worker. |
| → | uses | [CheckRequest](io.github.tlaplus.hardening.workflow.worker.CheckRequest.md) | What a checker is asked about one assembled module. |
| → | uses | [ToolResult](io.github.tlaplus.hardening.workflow.worker.ToolResult.md) | One request result returned by an isolated tool worker. |
| ← | uses | [WorkerLaunch](io.github.tlaplus.hardening.workflow.worker.WorkerLaunch.md) | Brings one isolated worker up: private scratch storage, a private loopback listener, the child JVM, and an authenticated control connection. |
| → | depends | [ToolWorkerProtocol](io.github.tlaplus.hardening.workflow.worker.ToolWorkerProtocol.md) | Binary framing shared by isolated parser and model-checker workers. |
