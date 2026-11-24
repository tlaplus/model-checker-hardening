# `TlcConfiguration`

`io.github.tlaplus.hardening.workflow.tlc` · class · codec · key · [source](https://github.com/tlaplus/model-checker-hardening/blob/390ea420071a36987998dbae4a6bab1d394188dc/src/main/java/io/github/tlaplus/hardening/workflow/tlc/TlcConfiguration.java#L13) · module [workflow](../modules/workflow.md)

The TLC configuration file for one checked module.

```mermaid
classDiagram
  class TlcConfiguration {
    <<codec>>
  }
  class CheckRequest {
    <<record>>
  }
  TlcConfiguration ..> CheckRequest : uses
```

The diagram draws 1 of 6 relations; the table lists them all.

| Direction | Relation | Type | Summary |
|---|---|---|---|
| → | uses | [CheckRequest](io.github.tlaplus.hardening.workflow.worker.CheckRequest.md) | What a checker is asked about one assembled module. |
| → | depends | [FuzzInputModule](io.github.tlaplus.hardening.workflow.spec.FuzzInputModule.md) | Assembles the module checked by the parser and model-checker stages. |
| → | depends | [CheckKind](io.github.tlaplus.hardening.workflow.worker.CheckKind.md) | What one checker invocation checks in an assembled module: which side's specification it explores, and whether it checks the invariant, the action invariant of a metamorphic module and a temporal property. |
| ← | depends | [DecodedInputRenderer](io.github.tlaplus.hardening.cli.DecodedInputRenderer.md) | Renders a decoded input independently of picocli command wiring. |
| ← | depends | [GenericConvention](io.github.tlaplus.hardening.workflow.generic.GenericConvention.md) | What a compatibility means to the generic checker (ADR 0033 §2): the configuration file written as `{config}`, and how an exit status becomes a verdict. |
| ← | depends | [TlcWorkerMain](io.github.tlaplus.hardening.workflow.tlc.TlcWorkerMain.md) | One-shot TLC child process. |
