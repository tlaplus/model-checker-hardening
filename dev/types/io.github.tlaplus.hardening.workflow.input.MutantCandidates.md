# `MutantCandidates`

`io.github.tlaplus.hardening.workflow.input` · class · policy · key · [source](https://github.com/tlaplus/model-checker-hardening/blob/0b7c167b636088dd43d36713d157e22f7ebd4f79/src/main/java/io/github/tlaplus/hardening/workflow/input/MutantCandidates.java#L19) · module [workflow](../modules/workflow.md)

Mutants of the parent pool (ADR 0010): every candidate mutates a uniformly drawn parent, keeps its cohort, and is rejected as a clone when it renders to the parent's module.

**Decisions:** [ADR 0010: Generational mutation](https://github.com/tlaplus/model-checker-hardening/blob/0b7c167b636088dd43d36713d157e22f7ebd4f79/docs/decisions/0010-mutation.md)

```mermaid
classDiagram
  class MutantCandidates {
    <<policy>>
  }
  class CandidateSource {
    <<contract>>
  }
  class Generator {
    <<contract>>
  }
  class ByteMutator {
    <<facade>>
  }
  class ParentPool
  class SpecArtifact {
    <<model>>
  }
  CandidateSource <|.. MutantCandidates
  MutantCandidates --> Generator : has
  MutantCandidates --> ByteMutator : has
  MutantCandidates --> ParentPool : has
  MutantCandidates --> SpecArtifact : has
```

The diagram draws 5 of 7 relations; the table lists them all.

| Direction | Relation | Type | Summary |
|---|---|---|---|
| → | implements | [CandidateSource](io.github.tlaplus.hardening.workflow.input.CandidateSource.md) | Where the candidates for a target entry of the input stage come from: property-based generation or mutation of a selected parent (ADR 0010). |
| → | has | [Generator](io.github.tlaplus.hardening.gen.Generator.md) | A composable recipe for decoding a value from a shared `Draw` cursor. |
| → | has | [ByteMutator](io.github.tlaplus.hardening.mutation.ByteMutator.md) | Derives one mutant from a parent by stacking weighted operator edits. |
| → | has | [ParentPool](io.github.tlaplus.hardening.workflow.input.ParentPool.md) | The entries of `04quality-pass` that a generation mutates or adopts: those of the kind the run generates, read once when the generation starts. |
| → | has | [SpecArtifact](io.github.tlaplus.hardening.workflow.spec.SpecArtifact.md) | One assembled module, what its checkers are asked about it, and what its decoder produced. |
| → | depends | [Mutation](io.github.tlaplus.hardening.corpus.Mutation.md) | How a mutated input was derived: the digest of the parent it was mutated from and the operators applied to the parent, in order. |
| ← | depends | [GenerationLoop](io.github.tlaplus.hardening.workflow.GenerationLoop.md) | Runs the generations of one workflow invocation over a single stage graph (ADR 0010). |
