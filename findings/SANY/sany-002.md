---
state: open
labels: [sany]
---

# SANY rejects labels inside `EXCEPT` clauses

## Summary

SANY accepts labels in ordinary expressions but rejects a label anywhere inside
an `EXCEPT` clause during semantic analysis:

```text
Labels inside EXCEPT clauses are not yet implemented.
```

The `corpus4` parser-failure corpus contains 179 instances of this diagnostic.
Of those, 176 contain only this limitation and three also violate a label's
formal-parameter requirement. All are ordinary parser failures, not crashes.
The representative corpus input is
[`013fb3b5...`](../../corpus4/01parser-fail/013fb3b5fb27369e7e8bbc075815e68062d387a7820b55c8d0890f01689741e6.cbor).

## Reproduction

```tla
---- MODULE LabelInExcept ----

VARIABLE x

Init == x = [[a |-> 0] EXCEPT !["a"] = (inside :: 1)]
Next == UNCHANGED x

====
```

SANY parses the module and then reports:

```text
line 5, col 41 to line 5, col 51 of module LabelInExcept

Labels inside EXCEPT clauses are not yet implemented.
```

## Expected behavior and impact

A label is semantically transparent and should not make an otherwise valid
`EXCEPT` expression invalid. SANY should either support labels throughout an
update clause or reject this placement through an explicit documented syntax
restriction before semantic analysis.

Until then, generators targeting SANY-valid source must not place labels in the
base expression, path selectors, or replacement expression of `EXCEPT`.
