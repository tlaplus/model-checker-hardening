# `ProjectDirectory`

`io.github.tlaplus.hardening.cli` · class · other · [source](https://github.com/tlaplus/model-checker-hardening/blob/6bbf8b66c375696d1798f7b7a061b81573a3f42a/src/main/java/io/github/tlaplus/hardening/cli/ProjectDirectory.java#L10) · module [cli](../modules/cli.md)

Locates the FuzzTLA project directory that ships the signature databases and scripts.

| Direction | Relation | Type | Summary |
|---|---|---|---|
| → | depends | [ToolJar](io.github.tlaplus.hardening.config.ToolJar.md) | A checker distribution that a corpus copies into its `tools/` directory, so its entries keep being checked, and its libraries typed, with the release they were admitted under (ADR 0025 §3). |
| → | depends | [StagedTools](io.github.tlaplus.hardening.workflow.StagedTools.md) | Locates the tool JARs the build stages beside FuzzTLA's own JAR, or beside its classes when it runs from the build directory. |
| → | depends | [WorkflowException](io.github.tlaplus.hardening.workflow.WorkflowException.md) | Reports an infrastructure or lifecycle failure in a fuzzing workflow. |
| ← | depends | [InitialConfig](io.github.tlaplus.hardening.cli.InitialConfig.md) | The configuration `fuzztla init` writes (ADR 0025 §1): the defaults, the shipped known-defect database, and the settings that follow from the technique. |
| ← | depends | [ReportCommand](io.github.tlaplus.hardening.cli.ReportCommand.md) | `fuzztla report`: prints the instructions for writing a campaign report (ADR 0020). |
