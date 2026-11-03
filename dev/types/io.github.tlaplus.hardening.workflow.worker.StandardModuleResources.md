# `StandardModuleResources`

`io.github.tlaplus.hardening.workflow.worker` · class · other · [source](https://github.com/tlaplus/model-checker-hardening/blob/42db61d5d40ed26cac733c168205f8f2a65d2211/src/main/java/io/github/tlaplus/hardening/workflow/worker/StandardModuleResources.java#L7) · module [workflow](../modules/workflow.md)

Verifies that generated specifications can resolve their packaged standard modules.

| Direction | Relation | Type | Summary |
|---|---|---|---|
| ← | depends | [LibrarySources](io.github.tlaplus.hardening.workflow.library.LibrarySources.md) | Materializes an ordered module search path as one source-only snapshot for SANY. |
| ← | depends | [ParserWorkerMain](io.github.tlaplus.hardening.workflow.parser.ParserWorkerMain.md) | Child-process entry point for persistent isolated SANY requests. |
| ← | depends | [TlcWorkerMain](io.github.tlaplus.hardening.workflow.tlc.TlcWorkerMain.md) | One-shot TLC child process. |
