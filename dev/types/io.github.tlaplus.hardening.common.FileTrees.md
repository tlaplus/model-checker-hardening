# `FileTrees`

`io.github.tlaplus.hardening.common` · class · utility · key · [source](https://github.com/tlaplus/model-checker-hardening/blob/47a7fcfd71d20c6a404ebf5a8a3e7d9a0e250e9b/src/main/java/io/github/tlaplus/hardening/common/FileTrees.java#L11) · module [common](../modules/common.md)

File-tree operations shared by the corpus and by isolated worker processes.

| Direction | Relation | Type | Summary |
|---|---|---|---|
| ← | depends | [TemporaryDirectory](io.github.tlaplus.hardening.common.TemporaryDirectory.md) | Owned temporary storage, removed on normal and exceptional completion. |
| ← | depends | [StageScratch](io.github.tlaplus.hardening.corpus.StageScratch.md) | Corpus-owned transient storage for one stage during one workflow invocation. |
| ← | depends | [ApalacheWorkerMain](io.github.tlaplus.hardening.workflow.apalache.ApalacheWorkerMain.md) | Persistent child process that invokes Apalache's `Tool.run` sequentially. |
| ← | depends | [WorkerLaunch](io.github.tlaplus.hardening.workflow.worker.WorkerLaunch.md) | Brings one isolated worker up: private scratch storage, a private loopback listener, the child JVM, and an authenticated control connection. |
| ← | depends | [WorkerOutput](io.github.tlaplus.hardening.workflow.worker.WorkerOutput.md) | Everything a child process says outside the control channel, and the scratch tree it says it in. |
