# `TlcTraceRecorder`

`io.github.tlaplus.hardening.workflow.tlc` · class · other · [source](https://github.com/tlaplus/model-checker-hardening/blob/d5f36672ab371b1224968e82791fde50c3daaeaf/src/main/java/io/github/tlaplus/hardening/workflow/tlc/TlcTraceRecorder.java#L30) · module [workflow](../modules/workflow.md)

Records the counterexample TLC prints (ADR 0034 §5), as the JSON a worker returns: its states in the Informal Trace Format and what its last state claims.

**Decisions:** [ADR 0034: Replaying counterexamples as corpus inputs](https://github.com/tlaplus/model-checker-hardening/blob/d5f36672ab371b1224968e82791fde50c3daaeaf/docs/decisions/0034-counterexample-replay.md)

```mermaid
classDiagram
  class TlcTraceRecorder
  class TlcExplorationMetrics
  TlcExplorationMetrics ..> TlcTraceRecorder : uses
```

The diagram draws 1 of 4 relations; the table lists them all.

| Direction | Relation | Type | Summary |
|---|---|---|---|
| ← | uses | [TlcExplorationMetrics](io.github.tlaplus.hardening.workflow.tlc.TlcExplorationMetrics.md) | Measures one TLC run and attaches the result to its classified outcome (ADR 0008). |
| → | depends | [FuzzInputModule](io.github.tlaplus.hardening.workflow.spec.FuzzInputModule.md) | Assembles the module checked by the parser and model-checker stages. |
| → | depends | [TlcItfValues](io.github.tlaplus.hardening.workflow.tlc.TlcItfValues.md) | Writes TLC's values in the Informal Trace Format (ADR 0034 §5), as a recorded trace carries them from the worker to its stage. |
| ← | depends | [TlcWorkerMain](io.github.tlaplus.hardening.workflow.tlc.TlcWorkerMain.md) | One-shot TLC child process. |
