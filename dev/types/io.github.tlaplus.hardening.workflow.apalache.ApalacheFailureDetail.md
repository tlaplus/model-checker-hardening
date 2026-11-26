# `ApalacheFailureDetail`

`io.github.tlaplus.hardening.workflow.apalache` · class · other · [source](https://github.com/tlaplus/model-checker-hardening/blob/dc773e71f8f182a2870398126f091b88fadea49d/src/main/java/io/github/tlaplus/hardening/workflow/apalache/ApalacheFailureDetail.java#L8) · module [workflow](../modules/workflow.md)

Extracts bounded human-readable context from Apalache diagnostics.

| Direction | Relation | Type | Summary |
|---|---|---|---|
| → | depends | [CheckerFailure](io.github.tlaplus.hardening.checker.CheckerFailure.md) | A stable checker failure code with optional bounded diagnostic context. |
| → | depends | [ApalacheOutcomeClassifier](io.github.tlaplus.hardening.workflow.apalache.ApalacheOutcomeClassifier.md) | Maps Apalache tool exit statuses to shared workflow outcomes, for Apalache and for a generic checker that follows Apalache's exit statuses (ADR 0033 §2). |
| ← | depends | [ApalacheCheckerBackend](io.github.tlaplus.hardening.workflow.apalache.ApalacheCheckerBackend.md) | Adapts persistent Apalache tool workers to the shared tool stage. |
