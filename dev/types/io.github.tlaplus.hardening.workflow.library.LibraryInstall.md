# `LibraryInstall`

`io.github.tlaplus.hardening.workflow.library` · class · store · key · [source](https://github.com/tlaplus/model-checker-hardening/blob/3df8c8564b58e946b95079b671788c8315f98831/src/main/java/io/github/tlaplus/hardening/workflow/library/LibraryInstall.java#L20) · module [workflow](../modules/workflow.md)

Copies a library's classpath into a corpus, so that the corpus keeps the release it was initialized with.

```mermaid
classDiagram
  class LibraryInstall {
    <<store>>
  }
  class OperatorLibraryConfig {
    <<record>>
  }
  LibraryInstall ..> OperatorLibraryConfig : uses
```

The diagram draws 1 of 3 relations; the table lists them all.

| Direction | Relation | Type | Summary |
|---|---|---|---|
| → | uses | [OperatorLibraryConfig](io.github.tlaplus.hardening.config.OperatorLibraryConfig.md) | Source-only library settings. |
| → | depends | [WorkflowException](io.github.tlaplus.hardening.workflow.WorkflowException.md) | Reports an infrastructure or lifecycle failure in a fuzzing workflow. |
| ← | depends | [InitCommand](io.github.tlaplus.hardening.cli.InitCommand.md) | `fuzztla init`: writes the default configuration of a new corpus and installs its libraries. |
