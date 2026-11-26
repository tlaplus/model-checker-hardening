---
state: closed
labels: [manyan]
---

# Manyan omits an evaluation error in a selected tuple element of a constant definition

## Summary

TLC evaluates a constant-level definition fully when it fingerprints its value, including
the bodies of function constructors that the definition never applies. Manyan emulates
this. In `"B" \notin DOMAIN [t \in {"A"} |-> 1 \div 0]` both tools report the division by
zero, and Manyan prints TLC's `TLC was unable to fingerprint.` But when the body selects a
tuple element, as in `<<1 \div 0>>[1]`, Manyan reports no error and evaluates the
definition to `TRUE`. TLC reports the division by zero:

```text
Error: The second argument of \div is 0.
Error: TLC was unable to fingerprint.
```

The same holds for `<<CHOOSE b \in {1} : FALSE>>[1]` and for
`(CHOOSE b \in {<<1>>} : FALSE)[1]`. An out-of-range index, `<<1>>[2]`, is reported by
both tools, and so is `<<1 \div 0>>[1]` in a state-level definition.

Observed with Manyan `v0.17.0` (soasme/manyan
`a9ca4f38028f4e8b4cc3f016167d071b55e3abe0`, 2026-11-24), built with GCC 13.3.0 by
`make manyan-build` on aarch64 Linux; corpus89-manyan ran an x86-64 build of the same
revision. The reference is TLC `bc1b506` (tlaplus/tlaplus
`bc1b506bdcad80bb518bf3776a9161c34ff0fc4c`) on OpenJDK 25.0.5.1. Apalache is not
involved. FuzzTLA `fa5b6c03`.

Fixed in [Manyan v0.18.0](https://github.com/soasme/manyan/releases/tag/v0.18.0), by
`11b727c`.

## Reproduction

`ConstTuple.tla`:

```tla
---- MODULE ConstTuple ----
EXTENDS Naturals, Sequences
VARIABLE x
Init == x = 0
Next == UNCHANGED x
Spec == Init /\ [][Next]_x
P == "B" \notin DOMAIN [t \in {"A"} |-> <<1 \div 0>>[1]]
====
```

`ConstTuple.cfg`:

```text
SPECIFICATION Spec
INVARIANT P
```

```sh
manyan -workers 1 -deadlock -config ConstTuple.cfg ConstTuple.tla
```

Manyan exits 0, with `Model checking completed. No error has been found.`
`java -cp tla2tools.jar tlc2.TLC -workers 1 -deadlock -config ConstTuple.cfg ConstTuple`
exits 75 with the error above.

### Variants

Each row is the body of `P ==` in the module above.

| Invariant | TLC | Manyan |
| --- | --- | --- |
| `"B" \notin DOMAIN [t \in {"A"} \|-> <<1 \div 0>>[1]]` | 75, `\div` by 0 | 0 |
| `"B" \notin DOMAIN [t \in {"A"} \|-> <<CHOOSE b \in {1} : FALSE>>[1]]` | 75, no `CHOOSE` witness | 0 |
| `"B" \notin DOMAIN [t \in {"A"} \|-> (CHOOSE b \in {<<1>>} : FALSE)[1]]` | 75, no `CHOOSE` witness | 0 |
| `"B" \notin DOMAIN [t \in {"A"} \|-> 1 \div 0]` | 75, `\div` by 0 | 75, `\div` by 0 |
| `"B" \notin DOMAIN [t \in {"A"} \|-> [i \in {1} \|-> 1 \div 0][1]]` | 75, `\div` by 0 | 75, `\div` by 0 |
| `"B" \notin DOMAIN [t \in {"A"} \|-> <<1>>[2]]` | 75, index 2 | 75, index 2 |
| `x = 0 /\ "B" \notin DOMAIN [t \in {"A"} \|-> <<1 \div 0>>[1]]` | 75, `\div` by 0 | 75, `\div` by 0 |
| `<<1 \div 0>>[1] = 1` | 75, `\div` by 0 | 75, `\div` by 0 |

## Expected behavior

Manyan should force the selected element when it fingerprints the definition, as it does
for the other bodies in the table, and report the error that TLC reports.

## Impact

The tuple element is a value that TLA+ does not specify, so Manyan's verdict is not wrong
by the semantics. It differs from TLC's, which Manyan otherwise reproduces, and in
corpus89-manyan, a 250,000-entry pbt campaign, it turned TLC's evaluation error into a
Manyan verdict in 2 entries. In `249ac0cb` and `fa984c7e` the invariant is
`VariantGetOrElse("Tag7", Variant("Tag6", (CHOOSE b \in S : FALSE)[k]), FALSE)`, where
`Variant` builds `[t \in {"Tag6"} |-> ...]`. TLC fails with status 75, and Manyan reports
that the invariant is violated by the initial state.

## Resolution

Manyan fixed this in
[`11b727c`](https://github.com/soasme/manyan/commit/11b727c0d3b87938891aaf5cea1e1ee1a5fe2b65),
"Restore EvalState on caught errors so keep_lazy does not leak", released in
[v0.18.0](https://github.com/soasme/manyan/releases/tag/v0.18.0) (soasme/manyan
`5522fa6b11b9e35ae60d46a31d0d33ce77df8ff9`, 2026-11-26). It is the first commit between
`v0.17.0` and `v0.18.0` that passes the reproduction; each commit was built with `make
manyan PGO_TOOLS=`.

Rechecked on 2026-11-26 with `v0.18.0`, built by `make manyan-build`, against the exit
statuses that TLC gave above: Manyan exits 75 on the three rows that it checked without
error, with TLC's `\div` and `CHOOSE` errors. FuzzTLA `dc773e71`.
