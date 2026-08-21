# `JsonToTlaViaBuilder` cannot read `LABEL` written by `TlaToJson`

## Summary

Apalache's JSON writer serializes `TlaOper.label` as the operator name `LABEL`,
but its checked JSON reader does not register that operator. A JSON round trip
therefore fails with `key not found: LABEL` even though the same IR is accepted
when supplied as TLA+ source.

Observed with `org.apalache-mc:tla-io_2.13:0.61.1-SNAPSHOT` as the writer and
Apalache 0.62.0 as the reader.

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

## Workaround and TODO

FuzzTLA recursively replaces `LABEL(expression, ...)` with `expression` only in
the Apalache JSON representation. SANY and TLC still receive the labeled TLA+
module.

Add `TlaOper.label` to the checked JSON reader and cover writer-to-reader round
tripping with a label regression test. Once the pinned Apalache release includes
that fix, remove FuzzTLA's label erasure and add an integration test that passes
the unchanged label node to Apalache.
