---
state: closed
labels: [apalache]
---

# `JsonToTlaViaBuilder` cannot read `LABEL` written by `TlaToJson`

## Summary

Apalache's JSON writer serializes `TlaOper.label` as the operator name `LABEL`,
but its checked JSON reader does not register that operator. A JSON round trip
therefore fails with `key not found: LABEL` even though the same IR is accepted
when supplied as TLA+ source.

Observed with `org.apalache-mc:tla-io_2.13:0.61.1-SNAPSHOT` as the writer and
Apalache 0.62.0 as the reader.

Fixed in [Apalache 0.62.2](https://github.com/apalache-mc/apalache/releases/tag/v0.62.2).

## Minimal reproduction

Generate a typed module containing one label without applying FuzzTLA's
Apalache normalization:

```java
var builder = new TlaTypedScopeUncheckedBuilder();
var expression = builder.label(builder.bool(false), "label0");
var json = TlaToUJson$.MODULE$
        .apply(FuzzInputModule.create(expression))
        .render(2, false);
```

Save `json` as `FuzzInput.json` and run:

```sh
apalache-mc check --init=Init --next=Next --inv=Inv --length=0 FuzzInput.json
```

The JSON contains an expression node with `"oper": "LABEL"`. The reader fails
before type checking with:

```text
key not found: LABEL
```

## Root cause

`TlaToJson` handles every `OperEx` generically and therefore emits the canonical
operator name. `BuilderCallByName.nameMap`, used by `JsonToTlaViaBuilder`, omits
`TlaOper.label`, so it cannot reconstruct the node. Apalache's model-checker
`LabelRule` otherwise treats a label as its first operand.

## Workaround

FuzzTLA recursively replaces `LABEL(expression, ...)` with `expression` only in
the Apalache JSON representation. SANY and TLC still receive the labeled TLA+
module.

Now that the pinned Apalache release contains the reader fix, FuzzTLA's label
erasure can be removed and replaced with an integration test that passes the
unchanged label node to Apalache.

## Resolution

Apalache 0.62.2 added JSON IR deserialization support for labelled expressions.
The release notes reference upstream issue
[`apalache-mc/apalache#3466`](https://github.com/apalache-mc/apalache/issues/3466).
