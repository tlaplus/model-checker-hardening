# `ReportCommand`

`io.github.tlaplus.hardening.cli` · class · entry point · key · [source](https://github.com/tlaplus/model-checker-hardening/blob/5b16596dd480095d323ac67ff0f85eb85227b4b5/src/main/java/io/github/tlaplus/hardening/cli/ReportCommand.java#L15) · module [cli](../modules/cli.md)

`fuzztla report`: prints the instructions for writing a campaign report (ADR 0020).

**Decisions:** [ADR 0020: Campaign report](https://github.com/tlaplus/model-checker-hardening/blob/5b16596dd480095d323ac67ff0f85eb85227b4b5/docs/decisions/0020-campaign-report.md)

| Direction | Relation | Type | Summary |
|---|---|---|---|
| → | depends | [ProjectDirectory](io.github.tlaplus.hardening.cli.ProjectDirectory.md) | Locates the FuzzTLA project directory that ships the signature databases and scripts. |
| → | depends | [ReportInstructions](io.github.tlaplus.hardening.cli.ReportInstructions.md) | The text of `fuzztla report`. |
