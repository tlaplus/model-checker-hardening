# `ProjectDirectory`

`io.github.tlaplus.hardening.cli` · class · other · [source](https://github.com/tlaplus/model-checker-hardening/blob/af09a9e32eb710979ef0c76f29507ce1ffe85a15/src/main/java/io/github/tlaplus/hardening/cli/ProjectDirectory.java#L9) · module [cli](../modules/cli.md)

Locates the FuzzTLA project directory that ships the signature databases and scripts.

| Direction | Relation | Type | Summary |
|---|---|---|---|
| → | depends | [WorkflowException](io.github.tlaplus.hardening.workflow.WorkflowException.md) | Reports an infrastructure or lifecycle failure in a fuzzing workflow. |
| → | depends | [ApalacheDistribution](io.github.tlaplus.hardening.workflow.apalache.ApalacheDistribution.md) | Locates the pinned Apalache release JAR staged beside FuzzTLA's build output. |
| ← | depends | [InitCommand](io.github.tlaplus.hardening.cli.InitCommand.md) | `fuzztla init`: writes the default configuration of a new corpus and installs its libraries. |
| ← | depends | [ReportCommand](io.github.tlaplus.hardening.cli.ReportCommand.md) | `fuzztla report`: prints the instructions for writing a campaign report (ADR 0020). |
