---
state: closed
labels: [manyan]
---

# Manyan applies an EXCEPT outside the domain when its result is applied at once

## Summary

`[f EXCEPT ![k] = v]` equals `f` when `k` is not in `DOMAIN f`. Manyan follows this rule
on its own: `[[i \in {1, 2} |-> 0] EXCEPT ![0] = 1] = [i \in {1, 2} |-> 0]` holds. But
Manyan fails when it applies the result directly:

```text
Error: In applying the function
<<0, 0>>,
the first argument is:
0
which is not in its domain.
```

TLC evaluates the same expression without error.

Observed with Manyan `v0.17.0` (soasme/manyan
`a9ca4f38028f4e8b4cc3f016167d071b55e3abe0`, 2026-11-24), built with GCC 13.3.0
(`make manyan PGO_TOOLS=`). The reference is TLC `bc1b506` (tlaplus/tlaplus
`bc1b506bdcad80bb518bf3776a9161c34ff0fc4c`) on OpenJDK 25.0.5.1. Apalache is not
involved. FuzzTLA `390ea420`.

Fixed in [Manyan v0.18.0](https://github.com/soasme/manyan/releases/tag/v0.18.0), by
`188c9ad`.

## Reproduction

`ExceptApply.tla`:

```tla
---- MODULE ExceptApply ----
EXTENDS Naturals
VARIABLE x
Init == x = 0
Next == UNCHANGED x
Spec == Init /\ [][Next]_x
Inv == [[i \in {1, 2} |-> 0] EXCEPT ![0] = 1][1] = 0
====
```

`ExceptApply.cfg`:

```text
SPECIFICATION Spec
INVARIANT Inv
```

```sh
manyan -workers 1 -deadlock -config ExceptApply.cfg ExceptApply.tla
```

Manyan exits 75 with the error above. TLC exits 0, with
`Model checking completed. No error has been found.`

### Variants

Each row is the body of `Inv ==` in the module above, with `Sequences` added for the tuple
rows.

| Invariant | TLC | Manyan |
| --- | --- | --- |
| `[[i \in {1, 2} \|-> 0] EXCEPT ![0] = 1][1] = 0` | 0 | 75, domain error |
| `[[i \in {1, 2} \|-> 0] EXCEPT ![x] = 1][1] = 0` | 0 | 75, domain error |
| `[[i \in {1, 2} \|-> 0] EXCEPT ![0] = 1] = [i \in {1, 2} \|-> 0]` | 0 | 0 |
| `[<<1, 2>> EXCEPT ![0] = 5][1] = 1` | 0 | 0 |
| `LET g == [<<1, 2>> EXCEPT ![0] = 5] IN g[1] = 1` | 0 | 0 |
| `[[a \|-> 1] EXCEPT !.b = 2].a = 1` | 0 | 0 |

## Expected behavior

`[[i \in {1, 2} |-> 0] EXCEPT ![0] = 1][1]` equals `[i \in {1, 2} |-> 0][1]`, which is
`0`. Manyan should evaluate it as it evaluates the unapplied EXCEPT.

## Impact

In a 10,000-entry pbt campaign, two entries disagreed with TLC this way: `60b255f4`, where
TLC passed, and `0e77fd84`, where TLC found a counterexample. No known-defect signature
quarantines these inputs. The error occurs only when the index is outside the domain at run
time. The shape `(FUN_APP (EXCEPT (FUN_CTOR ...) ...) _)` matched 833 entries, and almost
all of them are evaluated without error.

## Resolution

Manyan fixed this in
[`188c9ad`](https://github.com/soasme/manyan/commit/188c9adfa47f47fdba72880cc1629baf1dff8fe3),
"Fix EXCEPT @ scoping, lazy empty sets/V_LAMBDA, veq/filter/arg_pure, and generator
HO/LET/fairness", released in
[v0.18.0](https://github.com/soasme/manyan/releases/tag/v0.18.0) (soasme/manyan
`5522fa6b11b9e35ae60d46a31d0d33ce77df8ff9`, 2026-11-26). It is the first commit between
`v0.17.0` and `v0.18.0` that passes the reproduction; each commit was built with `make
manyan PGO_TOOLS=`.

Rechecked on 2026-11-26 with `v0.18.0`, built by `make manyan-build`, against the exit
statuses that TLC gave above: Manyan exits 0 on both rows that reported a domain error,
as TLC does. FuzzTLA `dc773e71`.
