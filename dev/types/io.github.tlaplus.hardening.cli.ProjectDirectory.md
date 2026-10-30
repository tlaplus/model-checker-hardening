# `ProjectDirectory`

`io.github.tlaplus.hardening.cli` · class · other · [source](https://github.com/tlaplus/model-checker-hardening/blob/b96eff75a5bbf8a60b15ad020d6e84735c75a47c/src/main/java/io/github/tlaplus/hardening/cli/ProjectDirectory.java#L9) · module [cli](../modules/cli.md)

Locates the FuzzTLA project directory that ships the signature databases and scripts.

| Direction | Relation | Type | Summary |
|---|---|---|---|
| → | depends | [WorkflowException](io.github.tlaplus.hardening.workflow.WorkflowException.md) | Reports an infrastructure or lifecycle failure in a fuzzing workflow. |
| → | depends | [ApalacheDistribution](io.github.tlaplus.hardening.workflow.apalache.ApalacheDistribution.md) | Locates the pinned Apalache release JAR staged beside FuzzTLA's build output. |
| ← | depends | [InitialConfig](io.github.tlaplus.hardening.cli.InitialConfig.md) | The configuration `fuzztla init` writes (ADR 0025 §1): the defaults, the shipped known-defect database, and the settings that follow from the technique. |
| ← | depends | [ReportCommand](io.github.tlaplus.hardening.cli.ReportCommand.md) | `fuzztla report`: prints the instructions for writing a campaign report (ADR 0020). |
