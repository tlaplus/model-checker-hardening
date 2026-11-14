# `TlcValueShape`

`io.github.tlaplus.hardening.workflow.tlc` · class · other · [source](https://github.com/tlaplus/model-checker-hardening/blob/4bb3bb7ddd8f7fa681b44bd858847122dbd84907/src/main/java/io/github/tlaplus/hardening/workflow/tlc/TlcValueShape.java#L20) · module [workflow](../modules/workflow.md)

The shape of the values of one state: how many value nodes they hold, their largest collection, and their deepest nesting.

| Direction | Relation | Type | Summary |
|---|---|---|---|
| → | depends | [Preconditions](io.github.tlaplus.hardening.common.Preconditions.md) | Shared validation of method and constructor arguments. |
| ← | depends | [TlcStateMetrics](io.github.tlaplus.hardening.workflow.tlc.TlcStateMetrics.md) | Measures the state graph TLC explores, through the state-writer callbacks TLC makes for every initial state and every generated transition. |
