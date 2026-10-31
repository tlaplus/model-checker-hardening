# `Rewrite`

`io.github.tlaplus.hardening.gen.rewrite` · record · model · key · [source](https://github.com/tlaplus/model-checker-hardening/blob/6bbf8b66c375696d1798f7b7a061b81573a3f42a/src/main/java/io/github/tlaplus/hardening/gen/rewrite/Rewrite.java#L13) · module [gen](../modules/gen.md)

A decoded metamorphic pair: the base the payload's base part decodes to, its rewrite, the orientation, and the rules applied in order.

```mermaid
classDiagram
  class Rewrite {
    <<model>>
  }
  class Orientation {
    <<enum>>
  }
  class DecodedInputRenderer
  class Rewriter {
    <<facade>>
  }
  class SpecArtifact {
    <<model>>
  }
  Rewrite --> Orientation : has
  DecodedInputRenderer ..> Rewrite : uses
  Rewriter ..> Rewrite : uses
  SpecArtifact ..> Rewrite : uses
```

The diagram draws 4 of 6 relations; the table lists them all.

| Direction | Relation | Type | Summary |
|---|---|---|---|
| → | has | [Orientation](io.github.tlaplus.hardening.gen.rewrite.Orientation.md) | Which side of a metamorphic pair the checker explores (ADR 0016 §3). |
| ← | uses | [DecodedInputRenderer](io.github.tlaplus.hardening.cli.DecodedInputRenderer.md) | Renders a decoded input independently of picocli command wiring. |
| ← | uses | [Rewriter](io.github.tlaplus.hardening.gen.rewrite.Rewriter.md) | Decodes a rewrite payload into a rewrite of a base (ADR 0016 §2). |
| ← | uses | [SpecArtifact](io.github.tlaplus.hardening.workflow.spec.SpecArtifact.md) | One assembled module, what its checkers are asked about it, and what its decoder produced. |
| ← | depends | [EvaluatedExprs](io.github.tlaplus.hardening.workflow.spec.EvaluatedExprs.md) | Replays a stored input and counts the expression constructs of the code the checkers evaluate: the definitions reachable from `SpecArtifact.entryPoints`. |
| ← | depends | [SpecDecoders](io.github.tlaplus.hardening.workflow.spec.SpecDecoders.md) | Immutable registry of the decoders that turn stored payloads into checkable modules. |
