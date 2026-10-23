# `TlcConfiguration`

`io.github.tlaplus.hardening.workflow.tlc` · class · codec · key · [source](https://github.com/tlaplus/model-checker-hardening/blob/47a7fcfd71d20c6a404ebf5a8a3e7d9a0e250e9b/src/main/java/io/github/tlaplus/hardening/workflow/tlc/TlcConfiguration.java#L10) · module [workflow](../modules/workflow.md)

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

The diagram draws 1 of 3 relations; the table lists them all.

| Direction | Relation | Type | Summary |
|---|---|---|---|
| → | uses | [CheckRequest](io.github.tlaplus.hardening.workflow.worker.CheckRequest.md) | What a checker is asked about one assembled module. |
| → | depends | [FuzzInputModule](io.github.tlaplus.hardening.workflow.spec.FuzzInputModule.md) | Assembles the module checked by the parser and model-checker stages. |
| ← | depends | [TlcWorkerMain](io.github.tlaplus.hardening.workflow.tlc.TlcWorkerMain.md) | One-shot TLC child process. |
