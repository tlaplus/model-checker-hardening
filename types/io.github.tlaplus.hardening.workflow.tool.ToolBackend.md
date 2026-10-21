# `ToolBackend`

`io.github.tlaplus.hardening.workflow.tool` · interface · contract · key · [source](https://github.com/tlaplus/model-checker-hardening/blob/47dd62c3064b76d8140ecbc017a45de80d885015/src/main/java/io/github/tlaplus/hardening/workflow/tool/ToolBackend.java#L30) · module [workflow](../modules/workflow.md)

Tool-specific behavior injected into the shared `ToolStage`.

```mermaid
classDiagram
  class ToolBackend {
    <<contract>>
  }
  class ApalacheCheckerBackend {
    <<adapter>>
  }
  class ParserBackend {
    <<adapter>>
  }
  class TlcCheckerBackend {
    <<adapter>>
  }
  class ToolStage {
    <<process>>
  }
  class CorpusStage {
    <<model>>
  }
  class SpecArtifact {
    <<model>>
  }
  class ToolWorker {
    <<contract>>
  }
  class CheckerBackends {
    <<policy>>
  }
  class MetamorphicShrinker {
    <<facade>>
  }
  ToolBackend <|.. ApalacheCheckerBackend
  ToolBackend <|.. ParserBackend
  ToolBackend <|.. TlcCheckerBackend
  ToolStage --> ToolBackend : has
  ToolBackend ..> CorpusStage : uses
  ToolBackend ..> SpecArtifact : uses
  ToolBackend ..> ToolWorker : uses
  CheckerBackends ..> ToolBackend : uses
  MetamorphicShrinker ..> ToolBackend : uses
```

The diagram draws 9 of 12 relations; the table lists them all.

| Direction | Relation | Type | Summary |
|---|---|---|---|
| ← | implements | [ApalacheCheckerBackend](io.github.tlaplus.hardening.workflow.apalache.ApalacheCheckerBackend.md) | Adapts persistent Apalache tool workers to the shared tool stage. |
| ← | implements | [ParserBackend](io.github.tlaplus.hardening.workflow.parser.ParserBackend.md) | Runs SANY in persistent isolated JVMs, one per stage worker, each replaced after a crash. |
| ← | implements | [TlcCheckerBackend](io.github.tlaplus.hardening.workflow.tlc.TlcCheckerBackend.md) | Adapts fresh isolated TLC invocations to the shared tool stage. |
| ← | has | [ToolStage](io.github.tlaplus.hardening.workflow.tool.ToolStage.md) | One stage that runs an external tool over corpus entries: the parser, TLC, or Apalache. |
| → | uses | [CorpusStage](io.github.tlaplus.hardening.corpus.CorpusStage.md) | One implemented stage of the fuzzing pipeline, and the corpus directories it owns. |
| → | uses | [SpecArtifact](io.github.tlaplus.hardening.workflow.spec.SpecArtifact.md) | One assembled module, what its checkers are asked about it, and what its decoder produced. |
| → | uses | [ToolWorker](io.github.tlaplus.hardening.workflow.tool.ToolWorker.md) | Worker-local tool state used for one or more sequential inputs. |
| ← | uses | [CheckerBackends](io.github.tlaplus.hardening.workflow.CheckerBackends.md) | How each model-checker stage builds its backend for one invocation. |
| ← | uses | [MetamorphicShrinker](io.github.tlaplus.hardening.workflow.MetamorphicShrinker.md) | Reduces a metamorphic counterexample for triage (ADR 0016 §8). |
| → | depends | [WorkflowException](io.github.tlaplus.hardening.workflow.WorkflowException.md) | Reports an infrastructure or lifecycle failure in a fuzzing workflow. |
| → | depends | [SpecText](io.github.tlaplus.hardening.workflow.spec.SpecText.md) | Renders an assembled module as the TLA+ source that the parser and TLC consume. |
| ← | depends | [StageGraph](io.github.tlaplus.hardening.workflow.StageGraph.md) | The stages that run one workflow invocation, wired to their queues, result capacities, and shared collaborators, and the order in which they start and stop. |
