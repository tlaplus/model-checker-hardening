# `TemporaryDirectory`

`io.github.tlaplus.hardening.common` · record · other · [source](https://github.com/tlaplus/model-checker-hardening/blob/4f182776ab66c4f93ff45cb83dfcd11e34fa32d1/src/main/java/io/github/tlaplus/hardening/common/TemporaryDirectory.java#L8) · module [common](../modules/common.md)

Owned temporary storage, removed on normal and exceptional completion.

| Direction | Relation | Type | Summary |
|---|---|---|---|
| → | depends | [FileTrees](io.github.tlaplus.hardening.common.FileTrees.md) | File-tree operations shared by the corpus and by isolated worker processes. |
| ← | depends | [LibraryPreparation](io.github.tlaplus.hardening.workflow.library.LibraryPreparation.md) | Prepares custom libraries once before decoding; no importer/typechecker Maven dependency. |
| ← | depends | [RuleLibraryPreparation](io.github.tlaplus.hardening.workflow.library.RuleLibraryPreparation.md) | Loads the rule module of a metamorphic run once, before decoding (ADR 0017 §1, §6): SANY names its rules and reads their weight annotations, Snowcat types them, and `RewriteLibrary` checks the rule contract. |
