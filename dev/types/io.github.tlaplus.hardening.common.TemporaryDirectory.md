# `TemporaryDirectory`

`io.github.tlaplus.hardening.common` · record · other · [source](https://github.com/tlaplus/model-checker-hardening/blob/4feb6f8e880d1e15ce9ef20c65ab53a237bd330d/src/main/java/io/github/tlaplus/hardening/common/TemporaryDirectory.java#L8) · module [common](../modules/common.md)

Owned temporary storage, removed on normal and exceptional completion.

| Direction | Relation | Type | Summary |
|---|---|---|---|
| → | depends | [FileTrees](io.github.tlaplus.hardening.common.FileTrees.md) | File-tree operations shared by the corpus and by isolated worker processes. |
| ← | depends | [LibraryPreparation](io.github.tlaplus.hardening.workflow.library.LibraryPreparation.md) | Prepares custom libraries once before decoding; no importer/typechecker Maven dependency. |
| ← | depends | [RuleLibraryPreparation](io.github.tlaplus.hardening.workflow.library.RuleLibraryPreparation.md) | Loads a rule module once, before decoding (ADR 0017 §1, §6): the rewrites of a metamorphic run, its temporal rewrites (ADR 0027 §1), or the edge guards of graph inputs (ADR 0023 §6). |
