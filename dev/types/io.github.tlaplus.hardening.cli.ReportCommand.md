# `ReportCommand`

`io.github.tlaplus.hardening.cli` · class · entry point · key · [source](https://github.com/tlaplus/model-checker-hardening/blob/4206d99417cbe58b5b339e189936abe305c56cca/src/main/java/io/github/tlaplus/hardening/cli/ReportCommand.java#L15) · module [cli](../modules/cli.md)

`fuzztla report`: prints the instructions for writing a campaign report (ADR 0020).

**Decisions:** [ADR 0020: Campaign report](https://github.com/tlaplus/model-checker-hardening/blob/4206d99417cbe58b5b339e189936abe305c56cca/docs/decisions/0020-campaign-report.md)

| Direction | Relation | Type | Summary |
|---|---|---|---|
| → | depends | [ProjectDirectory](io.github.tlaplus.hardening.cli.ProjectDirectory.md) | Locates the FuzzTLA project directory that ships the signature databases and scripts. |
| → | depends | [ReportInstructions](io.github.tlaplus.hardening.cli.ReportInstructions.md) | The text of `fuzztla report`. |
