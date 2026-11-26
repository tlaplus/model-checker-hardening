# `OrderedParallel`

`io.github.tlaplus.hardening.common` · class · other · [source](https://github.com/tlaplus/model-checker-hardening/blob/dc773e71f8f182a2870398126f091b88fadea49d/src/main/java/io/github/tlaplus/hardening/common/OrderedParallel.java#L28) · module [common](../modules/common.md)

Applies a function to the items of a list on a pool of platform threads, and returns the results in list order to the calling thread.

| Direction | Relation | Type | Summary |
|---|---|---|---|
| → | depends | [Preconditions](io.github.tlaplus.hardening.common.Preconditions.md) | Shared validation of method and constructor arguments. |
| ← | depends | [CorpusEntries](io.github.tlaplus.hardening.corpus.CorpusEntries.md) | Reads corpus entries and states what makes one valid: a decodable CBOR envelope holding an expression, a name that matches its payload digest, a payload the caller's validator accepts, and stage metadata consistent with the directory the entry sits in. |
