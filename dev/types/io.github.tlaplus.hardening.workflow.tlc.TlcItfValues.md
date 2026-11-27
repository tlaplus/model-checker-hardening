# `TlcItfValues`

`io.github.tlaplus.hardening.workflow.tlc` · class · other · [source](https://github.com/tlaplus/model-checker-hardening/blob/1397e7b533b2a521ed26d8d6ad957519716682dc/src/main/java/io/github/tlaplus/hardening/workflow/tlc/TlcItfValues.java#L29) · module [workflow](../modules/workflow.md)

Writes TLC's values in the Informal Trace Format (ADR 0034 §5), as a recorded trace carries them from the worker to its stage.

**Decisions:** [ADR 0034: Replaying counterexamples as corpus inputs](https://github.com/tlaplus/model-checker-hardening/blob/1397e7b533b2a521ed26d8d6ad957519716682dc/docs/decisions/0034-counterexample-replay.md)

| Direction | Relation | Type | Summary |
|---|---|---|---|
| ← | depends | [TlcTraceRecorder](io.github.tlaplus.hardening.workflow.tlc.TlcTraceRecorder.md) | Records the counterexample TLC prints (ADR 0034 §5), as the JSON a worker returns: its states in the Informal Trace Format and what its last state claims. |
