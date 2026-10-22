# `OneShotTool`

`io.github.tlaplus.hardening.workflow.worker` · class · other · [source](https://github.com/tlaplus/model-checker-hardening/blob/4206d99417cbe58b5b339e189936abe305c56cca/src/main/java/io/github/tlaplus/hardening/workflow/worker/OneShotTool.java#L11) · module [workflow](../modules/workflow.md)

A finite CLI invocation, unlike protocol workers which serve a sequence of per-input requests.

| Direction | Relation | Type | Summary |
|---|---|---|---|
| → | depends | [WorkflowException](io.github.tlaplus.hardening.workflow.WorkflowException.md) | Reports an infrastructure or lifecycle failure in a fuzzing workflow. |
| → | depends | [BoundedOutputCapture](io.github.tlaplus.hardening.workflow.worker.BoundedOutputCapture.md) | Continuously drains a process stream while retaining only a bounded prefix. |
| → | depends | [WorkerLaunch](io.github.tlaplus.hardening.workflow.worker.WorkerLaunch.md) | Brings one isolated worker up: private scratch storage, a private loopback listener, the child JVM, and an authenticated control connection. |
| ← | depends | [LibraryTypechecker](io.github.tlaplus.hardening.workflow.library.LibraryTypechecker.md) | Finite, isolated Snowcat invocation producing a typed module rather than a checker verdict. |
