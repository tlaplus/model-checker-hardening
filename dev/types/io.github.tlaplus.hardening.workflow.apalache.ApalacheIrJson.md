# `ApalacheIrJson`

`io.github.tlaplus.hardening.workflow.apalache` · class · codec · key · [source](https://github.com/tlaplus/model-checker-hardening/blob/a304ba37155136c3ba79e4f2fb414cbb47d01606/src/main/java/io/github/tlaplus/hardening/workflow/apalache/ApalacheIrJson.java#L23) · module [workflow](../modules/workflow.md)

Renders an assembled module as the typed Apalache IR JSON that Apalache consumes.

| Direction | Relation | Type | Summary |
|---|---|---|---|
| ← | depends | [DecodedInputRenderer](io.github.tlaplus.hardening.cli.DecodedInputRenderer.md) | Renders a decoded input independently of picocli command wiring. |
| ← | depends | [ApalacheCheckerBackend](io.github.tlaplus.hardening.workflow.apalache.ApalacheCheckerBackend.md) | Adapts persistent Apalache tool workers to the shared tool stage. |
| ← | depends | [LibraryTypechecker](io.github.tlaplus.hardening.workflow.library.LibraryTypechecker.md) | Finite, isolated Snowcat invocation producing a typed module rather than a checker verdict. |
