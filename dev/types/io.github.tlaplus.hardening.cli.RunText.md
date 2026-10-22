# `RunText`

`io.github.tlaplus.hardening.cli` · record · other · [source](https://github.com/tlaplus/model-checker-hardening/blob/4f182776ab66c4f93ff45cb83dfcd11e34fa32d1/src/main/java/io/github/tlaplus/hardening/cli/RunText.java#L13) · module [cli](../modules/cli.md)

Shared field formatting; attributed text keeps terminal escapes out of width arithmetic.

```mermaid
classDiagram
  class RunText {
    <<record>>
  }
  class Precision {
    <<enum>>
  }
  class RunMetric {
    <<interface>>
  }
  class RunPalette {
    <<record>>
  }
  class RunValue {
    <<interface>>
  }
  class CorpusStage {
    <<model>>
  }
  class CorpusVerdict {
    <<model>>
  }
  class ProgressLayout
  RunText --> Precision : has
  RunText --> RunMetric : has
  RunText --> RunPalette : has
  RunText --> RunValue : has
  RunText ..> CorpusStage : uses
  RunText ..> CorpusVerdict : uses
  ProgressLayout ..> RunText : uses
```

The diagram draws 7 of 10 relations; the table lists them all.

| Direction | Relation | Type | Summary |
|---|---|---|---|
| → | has | [Precision](io.github.tlaplus.hardening.cli.Precision.md) | How a reported value is rendered: exactly for scrollback, or bounded for live screens. |
| → | has | [RunMetric](io.github.tlaplus.hardening.cli.RunMetric.md) | Stable field identity, independent of screen coordinates or formatted text. |
| → | has | [RunPalette](io.github.tlaplus.hardening.cli.RunPalette.md) | Capability-aware styles on the terminal's own palette and default background. |
| → | has | [RunValue](io.github.tlaplus.hardening.cli.RunValue.md) | Unrounded, typed values used both for formatting and change comparison. |
| → | uses | [CorpusStage](io.github.tlaplus.hardening.corpus.CorpusStage.md) | One implemented stage of the fuzzing pipeline, and the corpus directories it owns. |
| → | uses | [CorpusVerdict](io.github.tlaplus.hardening.corpus.CorpusVerdict.md) | A verdict supported by the implemented corpus stages. |
| ← | uses | [ProgressLayout](io.github.tlaplus.hardening.cli.ProgressLayout.md) | Pure, size-bounded flow and table layouts for the same statistics and styles. |
| → | depends | [Glyphs](io.github.tlaplus.hardening.cli.Glyphs.md) | Characters for the live diagram and change markers. |
| ← | depends | [RunTable](io.github.tlaplus.hardening.cli.RunTable.md) | Complete, exact statistics grouped by stage; suitable for scrollback and redirected output. |
| ← | depends | [TerminalProgressDisplay](io.github.tlaplus.hardening.cli.TerminalProgressDisplay.md) | Terminal mechanics of live progress: snapshots, change highlighting, resizing, and screen restoration. |
