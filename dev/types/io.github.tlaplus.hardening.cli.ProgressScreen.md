# `ProgressScreen`

`io.github.tlaplus.hardening.cli` · class · other · [source](https://github.com/tlaplus/model-checker-hardening/blob/6fa04b06239faa249744652fc9a20140aed0d561/src/main/java/io/github/tlaplus/hardening/cli/ProgressScreen.java#L11) · module [cli](../modules/cli.md)

Owns the alternate screen: entering it, updating changed rows, and restoring scrollback.

```mermaid
classDiagram
  class ProgressScreen
  class TerminalProgressDisplay {
    <<view>>
  }
  TerminalProgressDisplay --> ProgressScreen : has
```

| Direction | Relation | Type | Summary |
|---|---|---|---|
| ← | has | [TerminalProgressDisplay](io.github.tlaplus.hardening.cli.TerminalProgressDisplay.md) | Terminal mechanics of live progress: snapshots, change highlighting, resizing, and screen restoration. |
