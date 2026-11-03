# `TechniqueConverter`

`io.github.tlaplus.hardening.cli` · class · other · [source](https://github.com/tlaplus/model-checker-hardening/blob/7b97336d8ec341ec48f89de4a368b6a488a145ac/src/main/java/io/github/tlaplus/hardening/cli/TechniqueConverter.java#L8) · module [cli](../modules/cli.md)

Reads the `--how` option of `fuzztla init` by the technique's encoded name.

```mermaid
classDiagram
  class TechniqueConverter
  class Technique {
    <<enum>>
  }
  Technique <|.. TechniqueConverter
```

| Direction | Relation | Type | Summary |
|---|---|---|---|
| → | implements | [Technique](io.github.tlaplus.hardening.corpus.Technique.md) | The fuzzing technique a corpus runs: `[workflow] technique`, which `fuzztla init --how` sets (ADR 0025 §1), so every run and every replay decodes and judges its entries the same way. |
