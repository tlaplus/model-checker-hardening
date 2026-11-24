# `PatternException`

`io.github.tlaplus.hardening.signature` · class · other · [source](https://github.com/tlaplus/model-checker-hardening/blob/24ed0403c7b9dcb03d020ba52a985ca0f9e882f2/src/main/java/io/github/tlaplus/hardening/signature/PatternException.java#L4) · module [signature](../modules/signature.md)

A pattern that does not follow the pattern syntax, located by a one-based column.

| Direction | Relation | Type | Summary |
|---|---|---|---|
| ← | depends | [KnownDefectDatabaseReader](io.github.tlaplus.hardening.signature.KnownDefectDatabaseReader.md) | Reads one database file: a TOML document of `[[signature]]` tables with exactly the keys `id`, `references`, `description`, `status`, and `match`. |
| ← | depends | [PatternParser](io.github.tlaplus.hardening.signature.PatternParser.md) | Parses the S-expression pattern syntax that the known-defect manual specifies: |
