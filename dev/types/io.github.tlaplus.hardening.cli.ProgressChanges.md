# `ProgressChanges`

`io.github.tlaplus.hardening.cli` · class · other · [source](https://github.com/tlaplus/model-checker-hardening/blob/4f182776ab66c4f93ff45cb83dfcd11e34fa32d1/src/main/java/io/github/tlaplus/hardening/cli/ProgressChanges.java#L8) · module [cli](../modules/cli.md)

Tracks changes and their direction for one refresh interval, using a caller-supplied monotonic clock.

```mermaid
classDiagram
  class ProgressChanges
  class RunMetric {
    <<interface>>
  }
  class RunValue {
    <<interface>>
  }
  class TerminalProgressDisplay {
    <<view>>
  }
  ProgressChanges --> RunMetric : has
  ProgressChanges --> RunValue : has
  TerminalProgressDisplay --> ProgressChanges : has
```

| Direction | Relation | Type | Summary |
|---|---|---|---|
| → | has | [RunMetric](io.github.tlaplus.hardening.cli.RunMetric.md) | Stable field identity, independent of screen coordinates or formatted text. |
| → | has | [RunValue](io.github.tlaplus.hardening.cli.RunValue.md) | Unrounded, typed values used both for formatting and change comparison. |
| ← | has | [TerminalProgressDisplay](io.github.tlaplus.hardening.cli.TerminalProgressDisplay.md) | Terminal mechanics of live progress: snapshots, change highlighting, resizing, and screen restoration. |
