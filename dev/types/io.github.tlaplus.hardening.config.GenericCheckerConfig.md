# `GenericCheckerConfig`

`io.github.tlaplus.hardening.config` · record · config · [source](https://github.com/tlaplus/model-checker-hardening/blob/218ef666053227c924e8c5067ad85b75f69870ca/src/main/java/io/github/tlaplus/hardening/config/GenericCheckerConfig.java#L20) · module [config](../modules/config.md)

The `[generic]` table (ADR 0033 §1): a model checker that claims compatibility with TLC or Apalache, run as a native process for each input.

**Decisions:** [ADR 0033: A generic checker for TLC- and Apalache-compatible tools](https://github.com/tlaplus/model-checker-hardening/blob/218ef666053227c924e8c5067ad85b75f69870ca/docs/decisions/0033-generic-checker.md)

```mermaid
classDiagram
  class GenericCheckerConfig {
    <<config>>
  }
  class GenericCapabilities {
    <<config>>
  }
  class GenericCompatibility {
    <<enum>>
  }
  class ToolsConfig {
    <<config>>
  }
  class GenericCheckerBackend {
    <<adapter>>
  }
  class GenericPlaceholder {
    <<enum>>
  }
  class ConfigSchema {
    <<config>>
  }
  class TomlConfig {
    <<codec>>
  }
  class CheckerBackends {
    <<policy>>
  }
  class GenericInvocation {
    <<model>>
  }
  GenericCheckerConfig --> GenericCapabilities : has
  GenericCheckerConfig --> GenericCompatibility : has
  ToolsConfig --> GenericCheckerConfig : has
  GenericCheckerBackend --> GenericCheckerConfig : has
  GenericCheckerConfig ..> GenericPlaceholder : uses
  ConfigSchema ..> GenericCheckerConfig : uses
  TomlConfig ..> GenericCheckerConfig : uses
  CheckerBackends ..> GenericCheckerConfig : uses
  GenericInvocation ..> GenericCheckerConfig : uses
```

The diagram draws 9 of 16 relations; the table lists them all.

| Direction | Relation | Type | Summary |
|---|---|---|---|
| → | has | [GenericCapabilities](io.github.tlaplus.hardening.config.GenericCapabilities.md) | What a generic checker supports of the reference checker's conventions (ADR 0033 §7, §8). |
| → | has | [GenericCompatibility](io.github.tlaplus.hardening.config.GenericCompatibility.md) | The reference checker whose conventions a generic checker follows (ADR 0033 §2): the configuration file FuzzTLA writes for it and how its exit status becomes a verdict. |
| ← | has | [ToolsConfig](io.github.tlaplus.hardening.config.ToolsConfig.md) | The tools a corpus checks and types with: the JARs of the `[tools]` table (ADR 0025 §3), and the generic checker of the `[generic]` table (ADR 0033). |
| ← | has | [GenericCheckerBackend](io.github.tlaplus.hardening.workflow.generic.GenericCheckerBackend.md) | Runs the generic checker of ADR 0033 as one native process per input, the lifecycle `ToolBackend` describes for TLC, but without a JVM. |
| → | uses | [GenericPlaceholder](io.github.tlaplus.hardening.config.GenericPlaceholder.md) | A placeholder in `[generic] arguments` (ADR 0033 §3), written `{name}`. |
| ← | uses | [ConfigSchema](io.github.tlaplus.hardening.config.ConfigSchema.md) | The declaration of every key in `config.toml`: where it lives, what it means, how its value is read, and how it is written back. |
| ← | uses | [TomlConfig](io.github.tlaplus.hardening.config.TomlConfig.md) | Reads and writes the strict `config.toml` format used by a corpus. |
| ← | uses | [CheckerBackends](io.github.tlaplus.hardening.workflow.CheckerBackends.md) | How each model-checker stage builds its backend for one invocation. |
| ← | uses | [GenericInvocation](io.github.tlaplus.hardening.workflow.generic.GenericInvocation.md) | The files and the command line of one generic checker invocation in its working directory (ADR 0033 §3, §4). |
| → | depends | [Preconditions](io.github.tlaplus.hardening.common.Preconditions.md) | Shared validation of method and constructor arguments. |
| → | depends | [ToolsConfig](io.github.tlaplus.hardening.config.ToolsConfig.md) | The tools a corpus checks and types with: the JARs of the `[tools]` table (ADR 0025 §3), and the generic checker of the `[generic]` table (ADR 0033). |
| ← | depends | [InitCommand](io.github.tlaplus.hardening.cli.InitCommand.md) | `fuzztla init`: writes the configuration of a new corpus, which names the technique every run follows (ADR 0025 §1), and copies its libraries and tool JARs into it (§2, §3). |
| ← | depends | [FuzzTlaConfig](io.github.tlaplus.hardening.config.FuzzTlaConfig.md) | Complete configuration of input generation and workflow execution. |
| ← | depends | [CorpusTools](io.github.tlaplus.hardening.workflow.CorpusTools.md) | The tool JARs a corpus runs (ADR 0025 §3): those of its `[tools]` table, or the ones staged with FuzzTLA for a tool the table leaves out. |
| ← | depends | [WorkflowRunner](io.github.tlaplus.hardening.workflow.WorkflowRunner.md) | Runs generation, parsing, TLC, Apalache, conformance aggregation and the quality gate under one CPU budget. |
| ← | depends | [LibraryInstall](io.github.tlaplus.hardening.workflow.library.LibraryInstall.md) | Copies into a new corpus every file its configuration names (ADR 0025 §2, §3), so the corpus keeps the releases it was initialized with: the classpath of the operator library, of the rewrite rules and of the edge guards goes to `tla/`. |
