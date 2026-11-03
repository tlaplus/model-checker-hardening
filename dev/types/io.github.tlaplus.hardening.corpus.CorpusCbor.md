# `CorpusCbor`

`io.github.tlaplus.hardening.corpus` · class · other · [source](https://github.com/tlaplus/model-checker-hardening/blob/7b97336d8ec341ec48f89de4a368b6a488a145ac/src/main/java/io/github/tlaplus/hardening/corpus/CorpusCbor.java#L7) · module [corpus](../modules/corpus.md)

The Jackson wiring shared by every codec of the corpus CBOR formats.

| Direction | Relation | Type | Summary |
|---|---|---|---|
| ← | depends | [CborMapWriter](io.github.tlaplus.hardening.corpus.CborMapWriter.md) | Collects the fields of one CBOR map so that its definite length is derived from what is written rather than maintained beside it. |
| ← | depends | [CborReader](io.github.tlaplus.hardening.corpus.CborReader.md) | Reads one CBOR document of a corpus, checking its shape as it goes. |
| ← | depends | [CorpusEnvelopeCodec](io.github.tlaplus.hardening.corpus.CorpusEnvelopeCodec.md) | Reads and rewrites the `stages` metadata of a corpus envelope. |
