# `GenericConvention`

`io.github.tlaplus.hardening.workflow.generic` · record · policy · key · [source](https://github.com/tlaplus/model-checker-hardening/blob/6fa04b06239faa249744652fc9a20140aed0d561/src/main/java/io/github/tlaplus/hardening/workflow/generic/GenericConvention.java#L37) · module [workflow](../modules/workflow.md)

What a compatibility means to the generic checker (ADR 0033 §2): the configuration file written as `{config}`, how an exit status becomes a verdict, and which exploration metrics the tool's output gives (§5).

**Decisions:** [ADR 0033: A generic checker for TLC- and Apalache-compatible tools](https://github.com/tlaplus/model-checker-hardening/blob/6fa04b06239faa249744652fc9a20140aed0d561/docs/decisions/0033-generic-checker.md)

```mermaid
classDiagram
  class GenericConvention {
    <<policy>>
  }
  class CheckRequest {
    <<record>>
  }
  class ToolResult {
    <<model>>
  }
  class GenericCheckerBackend {
    <<adapter>>
  }
  class GenericCompatibility {
    <<enum>>
  }
  class GenericErrors {
    <<enum>>
  }
  GenericConvention --> CheckRequest : has
  GenericConvention --> ToolResult : has
  GenericCheckerBackend --> GenericConvention : has
  GenericConvention ..> GenericCompatibility : uses
  GenericConvention ..> GenericErrors : uses
```

The diagram draws 5 of 14 relations; the table lists them all.

| Direction | Relation | Type | Summary |
|---|---|---|---|
| → | has | [CheckRequest](io.github.tlaplus.hardening.workflow.worker.CheckRequest.md) | What a checker is asked about one assembled module. |
| → | has | [ToolResult](io.github.tlaplus.hardening.workflow.worker.ToolResult.md) | One request result returned by an isolated tool worker. |
| ← | has | [GenericCheckerBackend](io.github.tlaplus.hardening.workflow.generic.GenericCheckerBackend.md) | Runs the generic checker of ADR 0033 as one native process per input, the lifecycle `ToolBackend` describes for TLC, but without a JVM. |
| → | uses | [GenericCompatibility](io.github.tlaplus.hardening.config.GenericCompatibility.md) | The reference checker whose conventions a generic checker follows (ADR 0033 §2): the configuration file FuzzTLA writes for it and how its exit status becomes a verdict. |
| → | uses | [GenericErrors](io.github.tlaplus.hardening.config.GenericErrors.md) | How much of a generic checker's exit status the aggregator compares (ADR 0033 §7). |
| → | depends | [CheckerFailureCode](io.github.tlaplus.hardening.checker.CheckerFailureCode.md) | Stable failure codes shared by model-checker stages. |
| → | depends | [ApalacheArguments](io.github.tlaplus.hardening.workflow.apalache.ApalacheArguments.md) | The Apalache command line for one checked module. |
| → | depends | [ApalacheOutcomeClassifier](io.github.tlaplus.hardening.workflow.apalache.ApalacheOutcomeClassifier.md) | Maps Apalache tool exit statuses to shared workflow outcomes, for Apalache and for a generic checker that follows Apalache's exit statuses (ADR 0033 §2). |
| → | depends | [FuzzInputModule](io.github.tlaplus.hardening.workflow.spec.FuzzInputModule.md) | Assembles the module checked by the parser and model-checker stages. |
| → | depends | [TlcCommandLineMetrics](io.github.tlaplus.hardening.workflow.tlc.TlcCommandLineMetrics.md) | Reads the size of a completed search from the statistics that TLC's command line prints, for a generic checker that follows TLC's output (ADR 0033 §5). |
| → | depends | [TlcConfiguration](io.github.tlaplus.hardening.workflow.tlc.TlcConfiguration.md) | The TLC configuration file for one checked module. |
| → | depends | [TlcOutcomeClassifier](io.github.tlaplus.hardening.workflow.tlc.TlcOutcomeClassifier.md) | Maps TLC's internal error constants and exit statuses to workflow outcomes, for TLC and for a generic checker that follows TLC's exit statuses (ADR 0033 §2). |
| → | depends | [CheckKind](io.github.tlaplus.hardening.workflow.worker.CheckKind.md) | What one checker invocation checks in an assembled module: which side's specification it explores, and whether it checks the invariant, the action invariant of a metamorphic module and a temporal property. |
| → | depends | [StageOutcome](io.github.tlaplus.hardening.workflow.worker.StageOutcome.md) | Common result of processing one input in a checking stage. |
