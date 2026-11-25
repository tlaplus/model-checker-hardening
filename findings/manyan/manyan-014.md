---
state: open
labels: [manyan]
---

# Manyan's tableau for nested `~>` grows exponentially where TLC's stays small

## Summary

For a property that nests leads-to on its left side,
`((((x = 1) ~> (x = 2)) ~> FALSE) ~> FALSE) ...`, Manyan's tableau construction takes
time and memory exponential in the nesting depth. At depth 7 Manyan needs 27 s and
10 GB, and at depth 8 it runs out of memory. TLC builds a tableau of one branch and
checks the same properties in under half a second.

Manyan builds the tableau in `expand` and `tnode_expand`. `tnode_add` finds an existing
node by a linear search over all nodes, comparing their formula lists with `tfl_same`, as
TLC did in [`tlc-performance-003`](../tlc-performance/tlc-performance-003.md). The
measurements below do not separate that search from the number of nodes.

Observed with Manyan `v0.17.0` (soasme/manyan
`a9ca4f38028f4e8b4cc3f016167d071b55e3abe0`, 2026-11-24), built with GCC 13.3.0 by
`make manyan-build` on aarch64 Linux; corpus89-manyan ran an x86-64 build of the same
revision. The reference is TLC `bc1b506` (tlaplus/tlaplus
`bc1b506bdcad80bb518bf3776a9161c34ff0fc4c`) on OpenJDK 25.0.5.1. Apalache is not
involved. FuzzTLA `fa5b6c03`.

## Reproduction

`NestedLeadsTo.tla`, at depth 7:

```tla
---- MODULE NestedLeadsTo ----
EXTENDS Naturals
VARIABLE x
Init == x = 0
Next == x < 3 /\ x' = x + 1
Spec == Init /\ [][Next]_x
Prop == ((((((((x = 1) ~> (x = 2)) ~> FALSE) ~> FALSE) ~> FALSE) ~> FALSE)
          ~> FALSE) ~> FALSE) ~> FALSE
====
```

`NestedLeadsTo.cfg`:

```text
SPECIFICATION Spec
PROPERTY Prop
```

```sh
manyan -workers 1 -deadlock -config NestedLeadsTo.cfg NestedLeadsTo.tla
```

Both tools report that `Prop` is violated. Wall time and peak resident memory, measured
with `/usr/bin/time`, for depth `k`, the number of `~> FALSE`:

| `k` | Manyan time | Manyan memory | TLC time |
|---:|---:|---:|---:|
| 4 | 0.00 s | 3 MB | 0.24 s |
| 5 | 0.06 s | 54 MB | 0.25 s |
| 6 | 0.13 s | 99 MB | 0.25 s |
| 7 | 27 s | 10.4 GB | 0.36 s |
| 8 | out of memory after 25 s | 9.2 GB, with a 20 GB address-space limit | |

Other right sides are cheaper but also grow: at depth 6 with `~> (x = 0)`, Manyan takes
2.3 s and 290 MB, and TLC 0.42 s. With `~> TRUE`, Manyan simplifies the property and
answers at once.

## Expected behavior

Manyan should build a tableau of a size comparable to TLC's. `F ~> FALSE` is equivalent
to `[]~F`, and TLC's tableau for the property has one branch.

## Impact

In corpus89-manyan, a 250,000-entry pbt campaign, Manyan timed out after 30 s on
`3d215d97`, which TLC passed in 1.3 s. Its property is
`<>(<>(<>(((([]([]([](<>((<>(A ~> B) ~> FALSE) ~> FALSE)) ~> FALSE)) ~> FALSE) ~> FALSE) ~> FALSE) ~> FALSE)) ~> FALSE)`.
With `A == x = 1` and `B == x = 2` in the module above, Manyan exceeds 60 s and 3 GB, and
TLC reports a violation in 0.4 s. The generator writes `~> FALSE` often, because `FALSE`
is a terminal of every Boolean position.
