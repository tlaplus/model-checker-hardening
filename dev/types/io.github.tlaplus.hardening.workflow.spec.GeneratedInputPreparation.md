# `GeneratedInputPreparation`

`io.github.tlaplus.hardening.workflow.spec` · class · other · [source](https://github.com/tlaplus/model-checker-hardening/blob/3b201567990514c2a43b90dca521dbe32af37023/src/main/java/io/github/tlaplus/hardening/workflow/spec/GeneratedInputPreparation.java#L13) · module [workflow](../modules/workflow.md)

Regenerates and renders one tool input while preserving generator-crash artifacts.

```mermaid
classDiagram
  class GeneratedInputPreparation
  class CorpusDirectory {
    <<facade>>
  }
  class SpecArtifact {
    <<model>>
  }
  class SpecDecoders {
    <<facade>>
  }
  class ToolStage {
    <<process>>
  }
  class CorpusInput {
    <<record>>
  }
  class ToolInput {
    <<model>>
  }
  GeneratedInputPreparation --> CorpusDirectory : has
  GeneratedInputPreparation --> SpecArtifact : has
  GeneratedInputPreparation --> SpecDecoders : has
  ToolStage --> GeneratedInputPreparation : has
  GeneratedInputPreparation ..> CorpusInput : uses
  GeneratedInputPreparation ..> ToolInput : uses
```

The diagram draws 6 of 11 relations; the table lists them all.

| Direction | Relation | Type | Summary |
|---|---|---|---|
| → | has | [CorpusDirectory](io.github.tlaplus.hardening.corpus.CorpusDirectory.md) | Owns the on-disk layout and integrity checks for one FuzzTLA corpus. |
| → | has | [SpecArtifact](io.github.tlaplus.hardening.workflow.spec.SpecArtifact.md) | One assembled module, what its checkers are asked about it, and what its decoder produced. |
| → | has | [SpecDecoders](io.github.tlaplus.hardening.workflow.spec.SpecDecoders.md) | Immutable registry of the decoders that turn stored payloads into checkable modules. |
| ← | has | [ToolStage](io.github.tlaplus.hardening.workflow.tool.ToolStage.md) | One stage that runs an external tool over corpus entries: the parser, TLC, or Apalache. |
| → | uses | [CorpusInput](io.github.tlaplus.hardening.corpus.CorpusInput.md) | The required fields of one encoded corpus input. |
| → | uses | [ToolInput](io.github.tlaplus.hardening.workflow.worker.ToolInput.md) | One rendered specification and what the receiving checker is asked about it. |
| → | depends | [Diagnostics](io.github.tlaplus.hardening.common.Diagnostics.md) | Human-readable diagnostic formatting for failures. |
| → | depends | [GeneratorCrashRecording](io.github.tlaplus.hardening.corpus.GeneratorCrashRecording.md) | Result of trying to preserve a payload that crashed its generator. |
| → | depends | [InputKind](io.github.tlaplus.hardening.gen.InputKind.md) | What a generator payload decodes to. |
| → | depends | [WorkflowException](io.github.tlaplus.hardening.workflow.WorkflowException.md) | Reports an infrastructure or lifecycle failure in a fuzzing workflow. |
| → | depends | [CheckRequest](io.github.tlaplus.hardening.workflow.worker.CheckRequest.md) | What a checker is asked about one assembled module. |
