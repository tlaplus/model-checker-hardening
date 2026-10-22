# `ToolThreadStacks`

`io.github.tlaplus.hardening.workflow.worker` · class · other · [source](https://github.com/tlaplus/model-checker-hardening/blob/0b7c167b636088dd43d36713d157e22f7ebd4f79/src/main/java/io/github/tlaplus/hardening/workflow/worker/ToolThreadStacks.java#L20) · module [workflow](../modules/workflow.md)

The stacks of the threads a stopped worker was working on, for the diagnostic of a timeout (ADR 0021).

**Decisions:** [ADR 0021: Diagnostics of a timed-out worker](https://github.com/tlaplus/model-checker-hardening/blob/0b7c167b636088dd43d36713d157e22f7ebd4f79/docs/decisions/0021-timeout-diagnostics.md)

| Direction | Relation | Type | Summary |
|---|---|---|---|
| → | depends | [BoundedTextOutputStream](io.github.tlaplus.hardening.workflow.worker.BoundedTextOutputStream.md) | Retains a bounded UTF-8 byte prefix while discarding subsequent output. |
| ← | depends | [ToolWorkerRuntime](io.github.tlaplus.hardening.workflow.worker.ToolWorkerRuntime.md) | The part of a child worker that is the same for every tool: report an escaped failure by exiting non-zero, announce itself to the parent, serve inputs until the parent stops asking, write each input where the tool reads it, and keep a bounded record of what the tool printed. |
| ← | depends | [WorkerOutput](io.github.tlaplus.hardening.workflow.worker.WorkerOutput.md) | Everything a child process says outside the control channel, and the scratch tree it says it in. |
