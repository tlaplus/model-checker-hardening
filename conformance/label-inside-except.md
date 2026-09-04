# Label inside an `EXCEPT` replacement

## Classification

- **TLC source path:** semantic-analysis failure
- **Apalache typed-IR path:** accepted for the corpus input
- **Assessment:** explicit TLC/SANY language restriction; no new defect

## Representative MWE

```tla
---- MODULE LabelInsideExcept ----
VARIABLE f

Init ==
  f = [[x \in {"present"} |-> 0]
       EXCEPT !["present"] = inside :: 0]

Next == UNCHANGED f
Inv == TRUE
====
```

TLC accepts the syntax but rejects the label during semantic analysis:

```text
Semantic errors:

*** Errors: 1

Labels inside EXCEPT clauses are not yet implemented.

Error: Parsing or semantic analysis failed.
```

The resulting TLC error code is 3002 (`ERROR_SPEC_PARSE`). FuzzTLA therefore
records the result under its intentionally broad `parse` failure code, although
the syntax parser itself succeeded.

## Corpus origin

The corpus2 instance is:

```text
corpus2/03aggregator-fail/a5d2fbf1fb4536d09b641587767f31d70470497de779491482ff8cf9ad509c59.cbor
```

Its generated expression places several labels below `EXCEPT` replacement
expressions. Replaying the relevant historical generator shape reproduces the
semantic error with the TLC snapshot used by this project. The smaller module
above shows that neither the surrounding generated expression nor model
checking is required.

The corpus records an Apalache pass because that stage consumes typed IR JSON
rather than the `PrettyWriter` source consumed by SANY and TLC. Running the MWE
through Apalache's source parser also reaches SANY's rejection; this row is an
input-path capability difference, not evidence that Apalache's source language
accepts the construct.

## TLC implementation

In TLC revision `1239539`,
`tla2sany.semantic.Generator.generateLabel` checks whether semantic generation
is inside an `EXCEPT` specification. It emits
`ErrorCode.LABEL_NOT_ALLOWED_IN_FUNCTION_EXCEPT` when both exception-context
stacks are active. The adjacent source comment describes labels in this
position as deliberately unsupported. The same check remains on TLC's current
main branch.

This is therefore a precise explanation for the formerly unclassified corpus
failure, but it does not warrant a TLC defect finding.
