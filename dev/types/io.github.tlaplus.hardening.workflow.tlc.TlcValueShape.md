# `TlcValueShape`

`io.github.tlaplus.hardening.workflow.tlc` · class · other · [source](https://github.com/tlaplus/model-checker-hardening/blob/a304ba37155136c3ba79e4f2fb414cbb47d01606/src/main/java/io/github/tlaplus/hardening/workflow/tlc/TlcValueShape.java#L20) · module [workflow](../modules/workflow.md)

The shape of the values of one state: how many value nodes they hold, their largest collection, and their deepest nesting.

| Direction | Relation | Type | Summary |
|---|---|---|---|
| → | depends | [Preconditions](io.github.tlaplus.hardening.common.Preconditions.md) | Shared validation of method and constructor arguments. |
| ← | depends | [TlcStateMetrics](io.github.tlaplus.hardening.workflow.tlc.TlcStateMetrics.md) | Measures the state graph TLC explores, through the state-writer callbacks TLC makes for every initial state and every generated transition. |
