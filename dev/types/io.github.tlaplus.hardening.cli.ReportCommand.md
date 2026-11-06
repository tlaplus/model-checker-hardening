# `ReportCommand`

`io.github.tlaplus.hardening.cli` · class · entry point · key · [source](https://github.com/tlaplus/model-checker-hardening/blob/39531345378d8db5a429220f50aed4254ea670fb/src/main/java/io/github/tlaplus/hardening/cli/ReportCommand.java#L15) · module [cli](../modules/cli.md)

`fuzztla report`: prints the instructions for writing a campaign report (ADR 0020).

**Decisions:** [ADR 0020: Campaign report](https://github.com/tlaplus/model-checker-hardening/blob/39531345378d8db5a429220f50aed4254ea670fb/docs/decisions/0020-campaign-report.md)

| Direction | Relation | Type | Summary |
|---|---|---|---|
| → | depends | [ProjectDirectory](io.github.tlaplus.hardening.cli.ProjectDirectory.md) | Locates the FuzzTLA project directory that ships the signature databases and scripts. |
| → | depends | [ReportInstructions](io.github.tlaplus.hardening.cli.ReportInstructions.md) | The text of `fuzztla report`. |
