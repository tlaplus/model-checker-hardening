# `HumanDuration`

`io.github.tlaplus.hardening.cli` · class · other · [source](https://github.com/tlaplus/model-checker-hardening/blob/4df6a11e5b4bf93609393935c4cbeceec08cce0c/src/main/java/io/github/tlaplus/hardening/cli/HumanDuration.java#L8) · module [cli](../modules/cli.md)

Formats nonnegative durations as compact day, hour, minute, and second parts.

| Direction | Relation | Type | Summary |
|---|---|---|---|
| → | depends | [HumanNumber](io.github.tlaplus.hardening.cli.HumanNumber.md) | Locale-independent numeric fields; compact counts occupy at most five columns. |
| → | depends | [Preconditions](io.github.tlaplus.hardening.common.Preconditions.md) | Shared validation of method and constructor arguments. |
| ← | depends | [EnvelopeReport](io.github.tlaplus.hardening.cli.EnvelopeReport.md) | Renders a decoded corpus envelope and its expression as plain text. |
| ← | depends | [RunValue](io.github.tlaplus.hardening.cli.RunValue.md) | Unrounded, typed values used both for formatting and change comparison. |
