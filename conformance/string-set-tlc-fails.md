# TLC cannot enumerate `STRING`

Observed once among corpus9's 40 unclassified aggregator deviations; TLC failed
and Apalache passed. It is the TLC-side counterpart of
[unsupported `STRING`](string-set-unsupported.md), where Apalache rejects the
operator and TLC passes.

`STRING` is the infinite set of all strings. TLC represents it as an overridden
value and rejects any operation that needs its size, including enumerating
`SUBSET STRING`. The expression is defined by TLA+ semantics; the failure is a
finite-value representation limit rather than a malformed specification.

The corpus input is
[`99caddec...61265.cbor`](../corpus9/03aggregator-fail/99caddece19724ee28395f3b05efa0660f43d8f67ddaa59c3074c7063a261265.cbor),
which reaches `CHOOSE bound5 \in SUBSET STRING : ...`.

## Representative MWE

```tla
---- MODULE StringSetTlcFails ----
VARIABLE
\* @type: Int;
x
Init == x = 0
Next == UNCHANGED x
Inv == \E s \in SUBSET STRING : s = {}
====
```

TLC exits with status 75 and reports `Attempted to compute the number of
elements in the overridden value STRING.` The MWE isolates TLC's limitation; the
observed Apalache pass applies to the complete generated expression.
