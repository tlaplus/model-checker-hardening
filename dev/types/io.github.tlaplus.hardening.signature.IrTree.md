# `IrTree`

`io.github.tlaplus.hardening.signature` · class · other · [source](https://github.com/tlaplus/model-checker-hardening/blob/7d4f60afbf46c7ec2fc12dec60b7747fc5defc4c/src/main/java/io/github/tlaplus/hardening/signature/IrTree.java#L37) · module [signature](../modules/signature.md)

The subexpressions a signature is tried against: those the tools evaluate.

**Decisions:** [ADR 0006: Known-defect signatures](https://github.com/tlaplus/model-checker-hardening/blob/7d4f60afbf46c7ec2fc12dec60b7747fc5defc4c/docs/decisions/0006-known-defect-signatures.md)

| Direction | Relation | Type | Summary |
|---|---|---|---|
| ← | depends | [IrExprCounts](io.github.tlaplus.hardening.signature.IrExprCounts.md) | Counts the size and constructs of the code the tools evaluate, over the walk that signatures match against. |
| ← | depends | [IrPattern](io.github.tlaplus.hardening.signature.IrPattern.md) | One pattern over the typed Apalache IR, as parsed by `PatternParser`. |
| ← | depends | [KnownDefectDatabase](io.github.tlaplus.hardening.signature.KnownDefectDatabase.md) | The known-defect signatures of one invocation, in database order. |
