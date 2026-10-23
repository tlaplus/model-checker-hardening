# `ReportCommand`

`io.github.tlaplus.hardening.cli` · class · entry point · key · [source](https://github.com/tlaplus/model-checker-hardening/blob/47a7fcfd71d20c6a404ebf5a8a3e7d9a0e250e9b/src/main/java/io/github/tlaplus/hardening/cli/ReportCommand.java#L15) · module [cli](../modules/cli.md)

`fuzztla report`: prints the instructions for writing a campaign report (ADR 0020).

**Decisions:** [ADR 0020: Campaign report](https://github.com/tlaplus/model-checker-hardening/blob/47a7fcfd71d20c6a404ebf5a8a3e7d9a0e250e9b/docs/decisions/0020-campaign-report.md)

| Direction | Relation | Type | Summary |
|---|---|---|---|
| → | depends | [ProjectDirectory](io.github.tlaplus.hardening.cli.ProjectDirectory.md) | Locates the FuzzTLA project directory that ships the signature databases and scripts. |
| → | depends | [ReportInstructions](io.github.tlaplus.hardening.cli.ReportInstructions.md) | The text of `fuzztla report`. |
