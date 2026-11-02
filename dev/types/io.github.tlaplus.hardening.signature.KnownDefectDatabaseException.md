# `KnownDefectDatabaseException`

`io.github.tlaplus.hardening.signature` · class · other · [source](https://github.com/tlaplus/model-checker-hardening/blob/2c6aced743b4276ca683c29709daab946d1b9536/src/main/java/io/github/tlaplus/hardening/signature/KnownDefectDatabaseException.java#L4) · module [signature](../modules/signature.md)

A known-defect database that cannot be read, or that does not follow the database format.

| Direction | Relation | Type | Summary |
|---|---|---|---|
| ← | depends | [PrintCommand](io.github.tlaplus.hardening.cli.PrintCommand.md) | `fuzztla print`: decodes a payload or corpus envelope and renders it, with the known defects it matches. |
| ← | depends | [KnownDefectDatabase](io.github.tlaplus.hardening.signature.KnownDefectDatabase.md) | The known-defect signatures of one invocation, in database order. |
| ← | depends | [KnownDefectDatabaseReader](io.github.tlaplus.hardening.signature.KnownDefectDatabaseReader.md) | Reads one database file: a TOML document of `[[signature]]` tables with exactly the keys `id`, `references`, `description`, `status`, and `match`. |
| ← | depends | [WorkflowRunner](io.github.tlaplus.hardening.workflow.WorkflowRunner.md) | Runs generation, parsing, TLC, Apalache, conformance aggregation and the quality gate under one CPU budget. |
