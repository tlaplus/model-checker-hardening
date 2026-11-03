# `Digests`

`io.github.tlaplus.hardening.common` · class · utility · key · [source](https://github.com/tlaplus/model-checker-hardening/blob/42db61d5d40ed26cac733c168205f8f2a65d2211/src/main/java/io/github/tlaplus/hardening/common/Digests.java#L11) · module [common](../modules/common.md)

SHA-256 identities for corpus payloads and external replay dependencies.

| Direction | Relation | Type | Summary |
|---|---|---|---|
| ← | depends | [CorpusLayout](io.github.tlaplus.hardening.corpus.CorpusLayout.md) | Where one corpus keeps its files, how an entry is named, and how a file is written into it. |
| ← | depends | [LibraryManifest](io.github.tlaplus.hardening.workflow.library.LibraryManifest.md) | The identity of a custom operator library's source snapshot, which a metamorphic run compares with its base corpus's (ADR 0016 §6, ADR 0025 §5). |
