# `ApalacheTrace`

`io.github.tlaplus.hardening.workflow.apalache` · class · other · [source](https://github.com/tlaplus/model-checker-hardening/blob/6fa04b06239faa249744652fc9a20140aed0d561/src/main/java/io/github/tlaplus/hardening/workflow/apalache/ApalacheTrace.java#L27) · module [workflow](../modules/workflow.md)

The counterexample Apalache wrote for one check, read once for its length and, when the request asks for it, its trace (ADR 0034 §5).

**Decisions:** [ADR 0034: Replaying counterexamples as corpus inputs](https://github.com/tlaplus/model-checker-hardening/blob/6fa04b06239faa249744652fc9a20140aed0d561/docs/decisions/0034-counterexample-replay.md)

| Direction | Relation | Type | Summary |
|---|---|---|---|
| ← | depends | [ApalacheWorkerMain](io.github.tlaplus.hardening.workflow.apalache.ApalacheWorkerMain.md) | Persistent child process that invokes Apalache's `Tool.run` sequentially. |
