---
state: open
labels: [manyan]
---

# Manyan cannot handle a `LET` whose body is temporal under a temporal operator

## Summary

Manyan rejects a property in which a `LET` expression with a temporal body appears under
`[]`, `<>`, `~>` or `~`:

```text
Error: TLC cannot handle the temporal formula line 8, col 9 to line 8, col 31 of module LetTemporal
```

TLC checks these properties. Manyan checks the same `LET` at the top of the property or
under `/\`, and it checks `[]Q` for an operator `Q == <>(x = 3)` defined outside the
property. The `LET` definition need not be used: `LET c == 1 IN <>(x = 3)` is enough.

Observed with Manyan `v0.17.0` (soasme/manyan
`a9ca4f38028f4e8b4cc3f016167d071b55e3abe0`, 2026-11-24), built with GCC 13.3.0 by
`make manyan-build` on aarch64 Linux; corpus89-manyan ran an x86-64 build of the same
revision. The reference is TLC `bc1b506` (tlaplus/tlaplus
`bc1b506bdcad80bb518bf3776a9161c34ff0fc4c`) on OpenJDK 25.0.5.1. Apalache is not
involved. FuzzTLA `fa5b6c03`.

## Reproduction

`LetTemporal.tla`:

```tla
---- MODULE LetTemporal ----
EXTENDS Naturals
VARIABLE x
Q == <>(x = 3)
Init == x = 0
Next == x < 3 /\ x' = x + 1
Spec == Init /\ [][Next]_x
P == [](LET c == 1 IN <>(x = 3))
====
```

`LetTemporal.cfg`:

```text
SPECIFICATION Spec
PROPERTY P
```

```sh
manyan -workers 1 -deadlock -config LetTemporal.cfg LetTemporal.tla
```

Manyan exits 255 with the error above.
`java -cp tla2tools.jar tlc2.TLC -workers 1 -deadlock -config LetTemporal.cfg LetTemporal`
exits 13 and reports that `P` is violated: the specification has no fairness, so `x` may
stay below 3.

### Variants

Each row is the body of `P ==` in the module above. The cells give the exit status and the
first error.

| Property | TLC | Manyan |
| --- | --- | --- |
| `LET c == 1 IN <>(x = 3)` | 13, violated | 13, violated |
| `(LET c == 1 IN <>(x = 3)) /\ TRUE` | 13, violated | 13, violated |
| `[]Q` | 13, violated | 13, violated |
| `[](LET c == 1 IN <>(x = 3))` | 13, violated | 255, cannot handle |
| `[](LET y == x IN <>(y = 3))` | 13, violated | 255, cannot handle |
| `<>(LET c == 1 IN [](x = 3))` | 13, violated | 255, cannot handle |
| `~(LET c == 1 IN <>(x = 3))` | 12, violated | 255, cannot handle |
| `(LET c == 1 IN <>(x = 3)) ~> (x = 3)` | 0, no error | 255, cannot handle |
| `(LET c == 1 IN x = 1) ~> (x = 3)` | 13, violated | 13, violated |

The last row shows that a `LET` with a state-level body is accepted in the same place.

## Expected behavior

Manyan should expand a `LET` inside a temporal formula as it expands one at the top of the
property, and check the rows above as TLC does.

## Impact

In corpus89-manyan, a 250,000-entry pbt campaign, Manyan printed this message on 183
entries. On 32 of them TLC did not report it: TLC passed 3 entries, such as `8c31af4b`,
found a counterexample in 2, failed with an evaluation error in 23, and crashed for another
reason in 4. Every one of the 32 properties contains a `LET`. The generator writes a `LET`
around a temporal operand, as in `(LET LocalOp16(p) == p IN []FALSE) ~> FALSE`.

TLC prints the same message for the temporal formulas it cannot handle
([tlc-temporal-formula-limits](../../conformance/tlc-temporal-formula-limits.md)), so the
message alone does not show which tool is at fault, and `script/triager.py` has no rule
for it.
