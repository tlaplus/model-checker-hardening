# `TraceLiterals`

`io.github.tlaplus.hardening.gen.trace` · class · codec · key · [source](https://github.com/tlaplus/model-checker-hardening/blob/1397e7b533b2a521ed26d8d6ad957519716682dc/src/main/java/io/github/tlaplus/hardening/gen/trace/TraceLiterals.java#L41) · module [gen](../modules/gen.md)

Builds the typed IR literal of a recorded value (ADR 0034 §3).

**Decisions:** [ADR 0034: Replaying counterexamples as corpus inputs](https://github.com/tlaplus/model-checker-hardening/blob/1397e7b533b2a521ed26d8d6ad957519716682dc/docs/decisions/0034-counterexample-replay.md)

| Direction | Relation | Type | Summary |
|---|---|---|---|
| → | depends | [InputRejectedException](io.github.tlaplus.hardening.gen.InputRejectedException.md) | Indicates that the current input cannot be decoded into a valid value. |
| → | depends | [BuilderArrays](io.github.tlaplus.hardening.gen.ir.BuilderArrays.md) | Array conversions for the builder's generic varargs APIs. |
| ← | depends | [FuzzInputModule](io.github.tlaplus.hardening.workflow.spec.FuzzInputModule.md) | Assembles the module checked by the parser and model-checker stages. |
