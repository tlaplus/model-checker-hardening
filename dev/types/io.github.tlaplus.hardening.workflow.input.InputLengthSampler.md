# `InputLengthSampler`

`io.github.tlaplus.hardening.workflow.input` · class · other · [source](https://github.com/tlaplus/model-checker-hardening/blob/47a7fcfd71d20c6a404ebf5a8a3e7d9a0e250e9b/src/main/java/io/github/tlaplus/hardening/workflow/input/InputLengthSampler.java#L8) · module [workflow](../modules/workflow.md)

Samples bounded lengths from uniformly selected logarithmic size buckets.

| Direction | Relation | Type | Summary |
|---|---|---|---|
| → | depends | [Preconditions](io.github.tlaplus.hardening.common.Preconditions.md) | Shared validation of method and constructor arguments. |
| ← | depends | [PbtCandidates](io.github.tlaplus.hardening.workflow.input.PbtCandidates.md) | Property-based candidates (ADR 0002): a target draws one richness cohort, and every candidate for it is a uniformly random byte array of a length from `InputLengthSampler`. |
