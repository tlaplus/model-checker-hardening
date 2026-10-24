# `Glyphs`

`io.github.tlaplus.hardening.cli` · enum · other · [source](https://github.com/tlaplus/model-checker-hardening/blob/e180a3892e661e5d7ba7636c4f24a6fca66ded14/src/main/java/io/github/tlaplus/hardening/cli/Glyphs.java#L10) · module [cli](../modules/cli.md)

Characters for the live diagram and change markers.

```mermaid
classDiagram
  class Glyphs {
    <<enum>>
  }
  class RunPalette {
    <<record>>
  }
  class RunValue {
    <<interface>>
  }
  RunPalette --> Glyphs : has
  Glyphs ..> RunValue : uses
```

The diagram draws 2 of 4 relations; the table lists them all.

| Direction | Relation | Type | Summary |
|---|---|---|---|
| ← | has | [RunPalette](io.github.tlaplus.hardening.cli.RunPalette.md) | Capability-aware styles on the terminal's own palette and default background. |
| → | uses | [RunValue](io.github.tlaplus.hardening.cli.RunValue.md) | Unrounded, typed values used both for formatting and change comparison. |
| ← | depends | [ProgressLayout](io.github.tlaplus.hardening.cli.ProgressLayout.md) | Pure, size-bounded flow and table layouts for the same statistics and styles. |
| ← | depends | [RunText](io.github.tlaplus.hardening.cli.RunText.md) | Shared field formatting; attributed text keeps terminal escapes out of width arithmetic. |
