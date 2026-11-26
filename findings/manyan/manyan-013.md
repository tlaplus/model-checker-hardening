---
state: closed
labels: [manyan]
---

# Formatting a function-domain error takes Manyan exponential time and memory in the nesting of failing lazy functions

## Summary

When Manyan reports that an argument is not in a function's domain, it prints the
function. To print a function whose values are not yet computed, Manyan computes them
(`pretty`, `print_value`, `try_realize`, `realize_lambda`, `force`). If those values apply
failing functions in turn, printing repeats one level down. The time and the memory are
exponential in the nesting depth. At depth 12, Manyan takes 31 s and 19.6 GB before it
prints the error.

TLC has the same exponential time,
[`tlc-performance-002`](../tlc-performance/tlc-performance-002.md), but its memory stays
small. Both tools print the same message, in which the inner functions appear as
`<expression ...>`.

Observed with Manyan `v0.17.0` (soasme/manyan
`a9ca4f38028f4e8b4cc3f016167d071b55e3abe0`, 2026-11-24), built with GCC 13.3.0 by
`make manyan-build` on aarch64 Linux; corpus89-manyan ran an x86-64 build of the same
revision. The reference is TLC `bc1b506` (tlaplus/tlaplus
`bc1b506bdcad80bb518bf3776a9161c34ff0fc4c`) on OpenJDK 25.0.5.1 with
`-XX:+UseParallelGC` and the default heap. Apalache is not involved. FuzzTLA `fa5b6c03`.

Fixed in [Manyan v0.18.0](https://github.com/soasme/manyan/releases/tag/v0.18.0), by
`07dab1a`.

## Reproduction

`MessageCascade.tla`, the module of `tlc-performance-002` with the constant inlined:

```tla
---- MODULE MessageCascade ----
EXTENDS Naturals
VARIABLE x
RECURSIVE G(_)
G(k) == IF k = 0 THEN [s \in {1} |-> 0][2]
        ELSE [s \in {1, 2, 3} |-> [t \in {1} |-> G(k - 1)]][4]
Init == x = 0
Next == x' = G(10)
Spec == Init /\ [][Next]_x
====
```

`MessageCascade.cfg`:

```text
SPECIFICATION Spec
```

```sh
manyan -workers 1 -deadlock -config MessageCascade.cfg MessageCascade.tla
```

Both tools report the error, TLC with exit status 75 and Manyan with 255, as in
[`manyan-005`](manyan-005.md):

```text
Error: In applying the function
<<[t \in {1} |-> <expression line 6, col 50 to line 6, col 57 of module MessageCascade>], ...>>,
the first argument is:
4
which is not in its domain.
```

Wall time and peak resident memory, measured with `/usr/bin/time`, with `G(N)` in place
of `G(10)`:

| `N` | Manyan time | Manyan memory | TLC time |
|---:|---:|---:|---:|
| 4 | 0.00 s | 3 MB | 0.37 s |
| 6 | 0.01 s | 11 MB | 0.34 s |
| 8 | 0.17 s | 141 MB | 0.56 s |
| 10 | 2.8 s | 2.2 GB | 2.0 s |
| 12 | 31 s | 19.6 GB | 15 s |

Manyan's memory grows about 16 times per two levels of nesting.

## Expected behavior

Printing a function whose values fail should not compute more than one level of them,
and should not hold the values it has computed. Printing an unevaluated value as
`<expression ...>`, as both tools already do for the innermost level, bounds the work.

## Impact

In corpus89-manyan, a 250,000-entry pbt campaign, 5 of Manyan's 10 timeouts at 30 s were
this defect; their stacks are in Manyan's printer. TLC failed on each of them within
4 s with a function-domain error (4 entries, such as `0ef07d13`) or a `CHOOSE` without a
witness (`1a4d7860`). Rerun without the limit, `d0d7abb1` reached 18.7 GB in 40 s. The
generator nests functions built by folds of `LAMBDA`s, whose values Manyan computes
while printing.

## Resolution

Manyan fixed this in
[`07dab1a`](https://github.com/soasme/manyan/commit/07dab1a113b60ce3abc8fcbcb6c46e8163a03bc6),
"Skip error formatting inside try_realize() to prevent exponential cascade", released in
[v0.18.0](https://github.com/soasme/manyan/releases/tag/v0.18.0) (soasme/manyan
`5522fa6b11b9e35ae60d46a31d0d33ce77df8ff9`, 2026-11-26). It is the first commit between
`v0.17.0` and `v0.18.0` that passes the reproduction; each commit was built with `make
manyan PGO_TOOLS=`.

Rechecked on 2026-11-26 with `v0.18.0`, built by `make manyan-build`, against the exit
statuses that TLC gave above: Manyan prints the error for `G(12)` and `G(14)` in under
0.01 s with 2 MB of resident memory. It now exits 75, as TLC does, since
[`manyan-005`](manyan-005.md) is fixed. FuzzTLA `dc773e71`.
