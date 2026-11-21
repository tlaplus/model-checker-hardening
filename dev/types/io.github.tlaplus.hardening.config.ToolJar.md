# `ToolJar`

`io.github.tlaplus.hardening.config` · enum · other · [source](https://github.com/tlaplus/model-checker-hardening/blob/f8e3696af4019dbcdc642f10bde6a3edccf359c2/src/main/java/io/github/tlaplus/hardening/config/ToolJar.java#L13) · module [config](../modules/config.md)

A checker distribution that a corpus copies into its `tools/` directory, so its entries keep being checked, and its libraries typed, with the release they were admitted under (ADR 0025 §3).

**Decisions:** [ADR 0025: Self-contained corpora, chosen at `init`](https://github.com/tlaplus/model-checker-hardening/blob/f8e3696af4019dbcdc642f10bde6a3edccf359c2/docs/decisions/0025-self-contained-corpora.md)

```mermaid
classDiagram
  class ToolJar {
    <<enum>>
  }
  class ToolOverride {
    <<record>>
  }
  class ToolsConfig {
    <<config>>
  }
  class ConfigSchema {
    <<config>>
  }
  class StagedTools
  ToolOverride --> ToolJar : has
  ToolsConfig --> ToolJar : has
  ConfigSchema ..> ToolJar : uses
  StagedTools ..> ToolJar : uses
```

The diagram draws 4 of 9 relations; the table lists them all.

| Direction | Relation | Type | Summary |
|---|---|---|---|
| ← | has | [ToolOverride](io.github.tlaplus.hardening.cli.ToolOverride.md) | One `--tool TOOL=FILE` of `fuzztla init`: the JAR a new corpus copies into ` tools/` in place of the staged one (ADR 0032 §2). |
| ← | has | [ToolsConfig](io.github.tlaplus.hardening.config.ToolsConfig.md) | The `[tools]` table (ADR 0025 §3): the tool JARs a corpus checks and types with. |
| ← | uses | [ConfigSchema](io.github.tlaplus.hardening.config.ConfigSchema.md) | The declaration of every key in `config.toml`: where it lives, what it means, how its value is read, and how it is written back. |
| ← | uses | [StagedTools](io.github.tlaplus.hardening.workflow.StagedTools.md) | Locates the tool JARs the build stages beside FuzzTLA's own JAR, or beside its classes when it runs from the build directory. |
| ← | depends | [InitCommand](io.github.tlaplus.hardening.cli.InitCommand.md) | `fuzztla init`: writes the configuration of a new corpus, which names the technique every run follows (ADR 0025 §1), and copies its libraries and tool JARs into it (§2, §3). |
| ← | depends | [ProjectDirectory](io.github.tlaplus.hardening.cli.ProjectDirectory.md) | Locates the FuzzTLA project directory that ships the signature databases and scripts. |
| ← | depends | [ToolOverrideConverter](io.github.tlaplus.hardening.cli.ToolOverrideConverter.md) | Reads a `--tool TOOL=FILE` option of `fuzztla init`, TOOL being a `[tools]` key. |
| ← | depends | [CorpusTools](io.github.tlaplus.hardening.workflow.CorpusTools.md) | The tool JARs a corpus runs (ADR 0025 §3): those of its `[tools]` table, or the ones staged with FuzzTLA for a tool the table leaves out. |
| ← | depends | [LibraryInstall](io.github.tlaplus.hardening.workflow.library.LibraryInstall.md) | Copies into a new corpus every file its configuration names (ADR 0025 §2, §3), so the corpus keeps the releases it was initialized with: the classpath of the operator library, of the rewrite rules and of the edge guards goes to `tla/`. |
