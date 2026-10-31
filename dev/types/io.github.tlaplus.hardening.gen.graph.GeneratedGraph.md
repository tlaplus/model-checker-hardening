# `GeneratedGraph`

`io.github.tlaplus.hardening.gen.graph` · record · model · key · [source](https://github.com/tlaplus/model-checker-hardening/blob/6bbf8b66c375696d1798f7b7a061b81573a3f42a/src/main/java/io/github/tlaplus/hardening/gen/graph/GeneratedGraph.java#L26) · module [gen](../modules/gen.md)

A module decoded from a graph input (ADR 0023 §2), with the graph it encodes and the target its invariant `pc /= target` names.

**Decisions:** [ADR 0023: Graph-based testing](https://github.com/tlaplus/model-checker-hardening/blob/6bbf8b66c375696d1798f7b7a061b81573a3f42a/docs/decisions/0023-graph-based-testing.md)

```mermaid
classDiagram
  class GeneratedGraph {
    <<model>>
  }
  class GeneratedSpec {
    <<model>>
  }
  class TransitionGraph {
    <<model>>
  }
  class IrGenerators {
    <<facade>>
  }
  class IrGraphGeneratorEngine {
    <<facade>>
  }
  class GraphOutcomes {
    <<adapter>>
  }
  class SpecArtifact {
    <<model>>
  }
  GeneratedGraph --> GeneratedSpec : has
  GeneratedGraph --> TransitionGraph : has
  IrGenerators ..> GeneratedGraph : uses
  IrGraphGeneratorEngine ..> GeneratedGraph : uses
  GraphOutcomes ..> GeneratedGraph : uses
  SpecArtifact ..> GeneratedGraph : uses
```

The diagram draws 6 of 9 relations; the table lists them all.

| Direction | Relation | Type | Summary |
|---|---|---|---|
| → | has | [GeneratedSpec](io.github.tlaplus.hardening.gen.GeneratedSpec.md) | The declarations one generated module consists of. |
| → | has | [TransitionGraph](io.github.tlaplus.hardening.gen.graph.TransitionGraph.md) | A directed graph over the nodes `0..N-1`, the control structure of a graph module (ADR 0023 §2). |
| ← | uses | [IrGenerators](io.github.tlaplus.hardening.gen.IrGenerators.md) | Factory methods for deterministic generators of type-checked Apalache IR expressions. |
| ← | uses | [IrGraphGeneratorEngine](io.github.tlaplus.hardening.gen.engine.IrGraphGeneratorEngine.md) | Reusable decoder of graph inputs (ADR 0023 §2, §3): a control graph whose nodes carry assignment-only actions, tracked by the variable `pc`, and the invariant `pc /= k`. |
| ← | uses | [GraphOutcomes](io.github.tlaplus.hardening.workflow.spec.GraphOutcomes.md) | Predicts the outcome of a graph entry by replaying it through the decoders (ADR 0023 §4): a counterexample of the target's distance when a behaviour reaches the target within the step bound, and a pass otherwise. |
| ← | uses | [SpecArtifact](io.github.tlaplus.hardening.workflow.spec.SpecArtifact.md) | One assembled module, what its checkers are asked about it, and what its decoder produced. |
| → | depends | [Preconditions](io.github.tlaplus.hardening.common.Preconditions.md) | Shared validation of method and constructor arguments. |
| ← | depends | [DecodedInputRenderer](io.github.tlaplus.hardening.cli.DecodedInputRenderer.md) | Renders a decoded input independently of picocli command wiring. |
| ← | depends | [SpecDecoders](io.github.tlaplus.hardening.workflow.spec.SpecDecoders.md) | Immutable registry of the decoders that turn stored payloads into checkable modules. |
