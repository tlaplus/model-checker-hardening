# `Trace`

`io.github.tlaplus.hardening.gen.trace` · record · model · key · [source](https://github.com/tlaplus/model-checker-hardening/blob/ec784e8112438ead18c35b5a2e7e27b2d25a076b/src/main/java/io/github/tlaplus/hardening/gen/trace/Trace.java#L22) · module [gen](../modules/gen.md)

The states of one recorded counterexample and what its last state claims (ADR 0034 §1, §2).

**Decisions:** [ADR 0034: Replaying counterexamples as corpus inputs](https://github.com/tlaplus/model-checker-hardening/blob/ec784e8112438ead18c35b5a2e7e27b2d25a076b/docs/decisions/0034-counterexample-replay.md)

```mermaid
classDiagram
  class Trace {
    <<model>>
  }
  class TraceEnding {
    <<model>>
  }
  class TracePayload {
    <<codec>>
  }
  class FuzzInputModule {
    <<facade>>
  }
  class SpecArtifact {
    <<model>>
  }
  Trace --> TraceEnding : has
  TracePayload --> Trace : has
  FuzzInputModule ..> Trace : uses
  SpecArtifact ..> Trace : uses
```

The diagram draws 4 of 8 relations; the table lists them all.

| Direction | Relation | Type | Summary |
|---|---|---|---|
| → | has | [TraceEnding](io.github.tlaplus.hardening.gen.trace.TraceEnding.md) | What the last state of a recorded trace claims (ADR 0034 §1), which decides what its replay checks there. |
| ← | has | [TracePayload](io.github.tlaplus.hardening.gen.trace.TracePayload.md) | The payload of a `trace` input (ADR 0034 §2), a CBOR array in deterministic encoding: |
| ← | uses | [FuzzInputModule](io.github.tlaplus.hardening.workflow.spec.FuzzInputModule.md) | Assembles the module checked by the parser and model-checker stages. |
| ← | uses | [SpecArtifact](io.github.tlaplus.hardening.workflow.spec.SpecArtifact.md) | One assembled module, what its checkers are asked about it, and what its decoder produced. |
| → | depends | [Preconditions](io.github.tlaplus.hardening.common.Preconditions.md) | Shared validation of method and constructor arguments. |
| ← | depends | [DecodedInputRenderer](io.github.tlaplus.hardening.cli.DecodedInputRenderer.md) | Renders a decoded input independently of picocli command wiring. |
| ← | depends | [PredictedOutcomes](io.github.tlaplus.hardening.workflow.spec.PredictedOutcomes.md) | Predicts the outcome of an entry whose kind predicts one: a graph entry, by replaying it through the decoders (ADR 0023 §4): a counterexample of the target's distance when a behaviour reaches the target within the step bound, and a pass otherwise; a trace entry (ADR 0034 §4): a counterexample of exactly the trace's transitions, which its payload states without decoding the base. |
| ← | depends | [SpecDecoders](io.github.tlaplus.hardening.workflow.spec.SpecDecoders.md) | Immutable registry of the decoders that turn stored payloads into checkable modules. |
