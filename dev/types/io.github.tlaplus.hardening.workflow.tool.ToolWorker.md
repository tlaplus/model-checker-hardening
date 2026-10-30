# `ToolWorker`

`io.github.tlaplus.hardening.workflow.tool` · interface · contract · key · [source](https://github.com/tlaplus/model-checker-hardening/blob/918f7db91890f302587d25c76dc7ac189db3d023/src/main/java/io/github/tlaplus/hardening/workflow/tool/ToolWorker.java#L11) · module [workflow](../modules/workflow.md)

Worker-local tool state used for one or more sequential inputs.

```mermaid
classDiagram
  class ToolWorker {
    <<contract>>
  }
  class ProcessToolWorker {
    <<adapter>>
  }
  class ToolInput {
    <<model>>
  }
  class ToolResult {
    <<model>>
  }
  class MetamorphicShrinker {
    <<facade>>
  }
  class ApalacheCheckerBackend {
    <<adapter>>
  }
  class ApalacheProcess {
    <<adapter>>
  }
  class ParserBackend {
    <<adapter>>
  }
  class TlcCheckerBackend {
    <<adapter>>
  }
  class ToolBackend {
    <<contract>>
  }
  class ToolStage {
    <<process>>
  }
  ToolWorker <|.. ProcessToolWorker
  ToolWorker ..> ToolInput : uses
  ToolWorker ..> ToolResult : uses
  MetamorphicShrinker ..> ToolWorker : uses
  ApalacheCheckerBackend ..> ToolWorker : uses
  ApalacheProcess ..> ToolWorker : uses
  ParserBackend ..> ToolWorker : uses
  TlcCheckerBackend ..> ToolWorker : uses
  ToolBackend ..> ToolWorker : uses
  ToolStage ..> ToolWorker : uses
```

The diagram draws 10 of 12 relations; the table lists them all.

| Direction | Relation | Type | Summary |
|---|---|---|---|
| ← | implements | [ProcessToolWorker](io.github.tlaplus.hardening.workflow.tool.ProcessToolWorker.md) | A tool worker served by one isolated child process until a crash verdict retires it. |
| → | uses | [ToolInput](io.github.tlaplus.hardening.workflow.worker.ToolInput.md) | One rendered specification and what the receiving checker is asked about it. |
| → | uses | [ToolResult](io.github.tlaplus.hardening.workflow.worker.ToolResult.md) | One request result returned by an isolated tool worker. |
| ← | uses | [MetamorphicShrinker](io.github.tlaplus.hardening.workflow.MetamorphicShrinker.md) | Reduces a metamorphic counterexample for triage (ADR 0016 §8). |
| ← | uses | [ApalacheCheckerBackend](io.github.tlaplus.hardening.workflow.apalache.ApalacheCheckerBackend.md) | Adapts persistent Apalache tool workers to the shared tool stage. |
| ← | uses | [ApalacheProcess](io.github.tlaplus.hardening.workflow.apalache.ApalacheProcess.md) | Starts one persistent isolated Apalache JVM, which serves inputs until a crash retires it. |
| ← | uses | [ParserBackend](io.github.tlaplus.hardening.workflow.parser.ParserBackend.md) | Runs SANY in persistent isolated JVMs, one per stage worker, each replaced after a crash. |
| ← | uses | [TlcCheckerBackend](io.github.tlaplus.hardening.workflow.tlc.TlcCheckerBackend.md) | Adapts fresh isolated TLC invocations to the shared tool stage. |
| ← | uses | [ToolBackend](io.github.tlaplus.hardening.workflow.tool.ToolBackend.md) | Tool-specific behavior injected into the shared `ToolStage`. |
| ← | uses | [ToolStage](io.github.tlaplus.hardening.workflow.tool.ToolStage.md) | One stage that runs an external tool over corpus entries: the parser, TLC, or Apalache. |
| → | depends | [WorkflowException](io.github.tlaplus.hardening.workflow.WorkflowException.md) | Reports an infrastructure or lifecycle failure in a fuzzing workflow. |
| ← | depends | [LibrarySourceProbe](io.github.tlaplus.hardening.workflow.library.LibrarySourceProbe.md) | Parses every source alias of a prepared library once, with the classpath the parser and TLC workers receive (ADR 0015). |
