# `OrderedParallel`

`io.github.tlaplus.hardening.common` · class · other · [source](https://github.com/tlaplus/model-checker-hardening/blob/e4ab65335fef2f7aa9e835e4805d8e294545cba2/src/main/java/io/github/tlaplus/hardening/common/OrderedParallel.java#L28) · module [common](../modules/common.md)

Applies a function to the items of a list on a pool of platform threads, and returns the results in list order to the calling thread.

| Direction | Relation | Type | Summary |
|---|---|---|---|
| → | depends | [Preconditions](io.github.tlaplus.hardening.common.Preconditions.md) | Shared validation of method and constructor arguments. |
| ← | depends | [CorpusEntries](io.github.tlaplus.hardening.corpus.CorpusEntries.md) | Reads corpus entries and states what makes one valid: a decodable CBOR envelope holding an expression, a name that matches its payload digest, a payload the caller's validator accepts, and stage metadata consistent with the directory the entry sits in. |
