---
state: open
labels: [manyan]
---

# Manyan reports a tautology for a property that TLC checks

## Summary

Manyan stops with exit status 77 on some properties that TLC checks normally:

```text
Error: Temporal formula is a tautology (its negation is unsatisfiable).
```

TLC has the same check and message. Both tools negate every property, and they report a
tautology when every negation is unsatisfiable. Manyan simplifies constant subformulas
before it decides this, and TLC does not. For example, Manyan folds `FALSE ~> FALSE` to
`TRUE`. On inputs where TLC finds no tautology, TLC checks the specification and Manyan
does not.

The difference depends on how the constants evaluate, not on the syntax.
`<>([](FALSE ~> FALSE) ~> FALSE) ~> FALSE` is also valid, but Manyan checks it like TLC.

Observed with Manyan `v0.17.0` (soasme/manyan
`a9ca4f38028f4e8b4cc3f016167d071b55e3abe0`, 2026-11-24), built with GCC 13.3.0
(`make manyan PGO_TOOLS=`). The reference is TLC `bc1b506` (tlaplus/tlaplus
`bc1b506bdcad80bb518bf3776a9161c34ff0fc4c`) on OpenJDK 25.0.5.1. Apalache is not
involved. FuzzTLA `390ea420`.

## Reproduction

`Tautology.tla`:

```tla
---- MODULE Tautology ----
EXTENDS Naturals
VARIABLE x
Init == x = 0
Next == x < 2 /\ x' = x + 1
Spec == Init /\ [][Next]_x
P == FALSE ~> FALSE
====
```

`Tautology.cfg`:

```text
SPECIFICATION Spec
PROPERTY P
```

```sh
manyan -workers 1 -deadlock -config Tautology.cfg Tautology.tla
```

Manyan exits 77:

```text
Error: Temporal formula is a tautology (its negation is unsatisfiable).
```

`java -cp tla2tools.jar tlc2.TLC -workers 1 -deadlock -config Tautology.cfg Tautology`
exits 0, with `Model checking completed. No error has been found.`

### Variants

Each row is the body of `P ==` in the module above.

| Property | TLC | Manyan |
| --- | --- | --- |
| `FALSE ~> FALSE` | 0, no error | 77, tautology |
| `FALSE ~> TRUE` | 0, no error | 77, tautology |
| `[](FALSE ~> FALSE)` | 0, no error | 77, tautology |
| `(1 \in {2}) ~> x = 7` | 0, no error | 77, tautology |
| `<>FALSE => []FALSE` | 0, no error | 77, tautology |
| `TRUE ~> TRUE` | 0, no error | 0, no error |
| `[]TRUE` | 0, no error | 0, no error |

## Expected behavior

Manyan should report a tautology on exactly the properties where TLC does. TLC checks the
properties above, so Manyan should check them too.

## Impact

In a 10,000-entry pbt campaign, Manyan reported this error on 76 entries. On 50 of them TLC
completed (24 passes, 26 counterexamples), so the aggregator recorded a disagreement; an
example is `00a86b15`. On the other 26, TLC failed for another reason. Generated properties
often have a constant left side of `~>`, which is why the error is common.

No known-defect signature quarantines these inputs. The closest pattern is a `LEADS_TO`
whose left side names no variable and applies no operator. It matched 932 of the 10,000
entries, and only 65 of them carried the error.
