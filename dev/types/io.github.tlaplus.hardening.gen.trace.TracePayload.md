# `TracePayload`

`io.github.tlaplus.hardening.gen.trace` · record · codec · key · [source](https://github.com/tlaplus/model-checker-hardening/blob/218ef666053227c924e8c5067ad85b75f69870ca/src/main/java/io/github/tlaplus/hardening/gen/trace/TracePayload.java#L35) · module [gen](../modules/gen.md)

The payload of a `trace` input (ADR 0034 §2), a CBOR array in deterministic encoding:

**Decisions:** [ADR 0034: Replaying counterexamples as corpus inputs](https://github.com/tlaplus/model-checker-hardening/blob/218ef666053227c924e8c5067ad85b75f69870ca/docs/decisions/0034-counterexample-replay.md)

```mermaid
classDiagram
  class TracePayload {
    <<codec>>
  }
  class InputKind {
    <<model>>
  }
  class Trace {
    <<model>>
  }
  TracePayload --> InputKind : has
  TracePayload --> Trace : has
```

The diagram draws 2 of 9 relations; the table lists them all.

| Direction | Relation | Type | Summary |
|---|---|---|---|
| → | has | [InputKind](io.github.tlaplus.hardening.gen.InputKind.md) | What a generator payload decodes to. |
| → | has | [Trace](io.github.tlaplus.hardening.gen.trace.Trace.md) | The states of one recorded counterexample and what its last state claims (ADR 0034 §1, §2). |
| → | depends | [InputRejectedException](io.github.tlaplus.hardening.gen.InputRejectedException.md) | Indicates that the current input cannot be decoded into a valid value. |
| → | depends | [TraceEnding](io.github.tlaplus.hardening.gen.trace.TraceEnding.md) | What the last state of a recorded trace claims (ADR 0034 §1), which decides what its replay checks there. |
| ← | depends | [ItfTraces](io.github.tlaplus.hardening.gen.trace.ItfTraces.md) | Reads a counterexample that a checker worker recorded (ADR 0034 §5) into a canonical `Trace`. |
| ← | depends | [ReplayCandidates](io.github.tlaplus.hardening.workflow.input.ReplayCandidates.md) | Replays one recorded counterexample (ADR 0034 §6): the single candidate of its target is the `trace` payload of a parent's trace. |
| ← | depends | [PredictedOutcomes](io.github.tlaplus.hardening.workflow.spec.PredictedOutcomes.md) | Predicts the outcome of an entry whose kind predicts one: a graph entry, by replaying it through the decoders (ADR 0023 §4): a counterexample of the target's distance when a behaviour reaches the target within the step bound, and a pass otherwise; a trace entry (ADR 0034 §4): a counterexample of exactly the trace's transitions, which its payload states without decoding the base. |
| ← | depends | [SpecDecoders](io.github.tlaplus.hardening.workflow.spec.SpecDecoders.md) | Immutable registry of the decoders that turn stored payloads into checkable modules. |
| ← | depends | [RecordedTraces](io.github.tlaplus.hardening.workflow.tool.RecordedTraces.md) | Turns the trace a checker worker recorded into what its stage stores (ADR 0034 §5): read at the types of the module's variables, then encoded canonically. |
