---
state: closed
labels: [manyan]
---

# Manyan evaluates a product of a variable under `<>` as a constant

## Summary

When a property applies `*` to a state variable inside `<>`, Manyan fails with exit status
255:

```text
: In evaluation, the identifier x is either undefined or not an operator.
```

TLC checks the same property. TLC evaluates constant subformulas of a temporal formula while
it builds the tableau. The message suggests that Manyan takes the product for a constant and
evaluates it then, when no state assigns `x`. `+`, `-`, `\div` and `%` do not trigger it,
and neither does `[]` alone.

Observed with Manyan `v0.17.0` (soasme/manyan
`a9ca4f38028f4e8b4cc3f016167d071b55e3abe0`, 2026-11-24), built with GCC 13.3.0
(`make manyan PGO_TOOLS=`). The reference is TLC `bc1b506` (tlaplus/tlaplus
`bc1b506bdcad80bb518bf3776a9161c34ff0fc4c`) on OpenJDK 25.0.5.1. Apalache is not
involved. FuzzTLA `390ea420`.

Fixed in [Manyan v0.18.0](https://github.com/soasme/manyan/releases/tag/v0.18.0), by
`aab57fd`.

## Reproduction

`Mult.tla`:

```tla
---- MODULE Mult ----
EXTENDS Naturals
VARIABLE x
Init == x = 0
Next == x < 2 /\ x' = x + 1
Spec == Init /\ [][Next]_x
P == <>(1 * x = 0)
====
```

`Mult.cfg`:

```text
SPECIFICATION Spec
PROPERTY P
```

```sh
manyan -workers 1 -deadlock -config Mult.cfg Mult.tla
```

Manyan exits 255:

```text
Error: TLC threw an unexpected exception.
This was probably caused by an error in the spec or model.
See the User Output or TLC Console for clues to what happened.
The exception was a java.lang.RuntimeException
: In evaluation, the identifier x is either undefined or not an operator.
line 7, col 13 to line 7, col 13 of module Mult
```

`java -cp tla2tools.jar tlc2.TLC -workers 1 -deadlock -config Mult.cfg Mult` exits 0,
with `Model checking completed. No error has been found.`

### Variants

Each row is the body of `P ==` in the module above, with `Integers` in place of `Naturals`.

| Property | TLC | Manyan |
| --- | --- | --- |
| `<>(1 * x = 0)` | 0 | 255, `x` undefined |
| `<>(x * 1 = 0)` | 0 | 255, `x` undefined |
| `<>(x * x = 0)` | 0 | 255, `x` undefined |
| `<>[](1 * x = 0)` | 13 | 255, `x` undefined |
| `[]<>(1 * x = 0)` | 13 | 255, `x` undefined |
| `[](1 * x = 0)` | 12 | 12 |
| `<>(x + 1 = 0)` | 13 | 13 |
| `<>(x - 1 = 0)` | 13 | 13 |
| `<>(x \div 1 = 0)` | 0 | 0 |
| `<>(x % 2 = 0)` | 0 | 0 |
| `1 * x = 0` | 0 | 0 |

## Expected behavior

`1 * x` is a state function, so Manyan should evaluate it in each state, as it does for
`x + 1`.

## Impact

In a 25,000-entry pbt campaign, 18 Manyan crashes had this message. TLC completed or failed
normally on 11 of them: it passed `1ce3f997`, found a property violation on two, and failed
with status 75 on eight. TLC crashed on the other seven for its own reasons, six of them
`tlc-010`. `script/triager.py` classifies the Manyan crashes by the message.

## Resolution

Manyan fixed this in
[`aab57fd`](https://github.com/soasme/manyan/commit/aab57fdbdc29bdbb8bc10bbb804855adeba9f948),
"Check level_in_bound() in to_tf() and tf_atom() when level is 0", released in
[v0.18.0](https://github.com/soasme/manyan/releases/tag/v0.18.0) (soasme/manyan
`5522fa6b11b9e35ae60d46a31d0d33ce77df8ff9`, 2026-11-26). It is the first commit between
`v0.17.0` and `v0.18.0` that passes the reproduction; each commit was built with `make
manyan PGO_TOOLS=`.

Rechecked on 2026-11-26 with `v0.18.0`, built by `make manyan-build`, against the exit
statuses that TLC gave above: The five rows that failed with `x` undefined give TLC's
exit statuses: 0, 0, 0, 13 and 13. FuzzTLA `dc773e71`.
