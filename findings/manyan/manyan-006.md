---
state: closed
labels: [manyan]
---

# Manyan aborts in a set union with a function it has not yet built

## Summary

Manyan aborts with SIGABRT, exit status 134, and prints no error. This happens when it forms
`S \cup T` from two sets of functions where one function has not been built yet: Manyan
still holds it as its definition, for example the result of `[b \in {TRUE} |-> ...][TRUE]`.
The abort happens in `cup_merge`. `elem_kind` classifies the unbuilt function by its value
kind, `V_LAZY`, rather than as a function. So `kinds_differ` reports elements that do not
compare, and `cup_merge` calls `cup_error`. `cup_error` compares the elements to raise TLC's
error, but they do compare, so it reaches `abort()`.

TLC evaluates the union normally.

Observed with Manyan `v0.17.0` (soasme/manyan
`a9ca4f38028f4e8b4cc3f016167d071b55e3abe0`, 2026-11-24), built with GCC 13.3.0
(`make manyan PGO_TOOLS=`). The reference is TLC `bc1b506` (tlaplus/tlaplus
`bc1b506bdcad80bb518bf3776a9161c34ff0fc4c`) on OpenJDK 25.0.5.1. Apalache is not
involved. FuzzTLA `390ea420`.

Fixed in [Manyan v0.18.0](https://github.com/soasme/manyan/releases/tag/v0.18.0), by
`fec8f48`.

## Reproduction

`UnionAbort.tla`:

```tla
---- MODULE UnionAbort ----
EXTENDS Naturals
VARIABLE x
Init == x = 0
Next == UNCHANGED x
Spec == Init /\ [][Next]_x
Inv == (CHOOSE f \in {[b \in {TRUE} |-> [r \in {1, 2} |-> 0]][TRUE]} \cup {<<0>>} : FALSE)[x] = 0
====
```

`UnionAbort.cfg`:

```text
SPECIFICATION Spec
INVARIANT Inv
```

```sh
manyan -workers 1 -deadlock -config UnionAbort.cfg UnionAbort.tla
```

Manyan exits 134 and prints only its version line. A build with `-g -O0` stops here:

```text
#3  __GI_abort () at ./stdlib/abort.c:79
#4  cup_error (a=..., b=...) at manyan.c:1940
#5  cup_merge (s=..., b=...) at manyan.c:1991
```

`java -cp tla2tools.jar tlc2.TLC -workers 1 -deadlock -config UnionAbort.cfg UnionAbort`
exits 75:

```text
Error: Attempted to compute the value of an expression of form
CHOOSE x \in S: P, but no element of S satisfied P.
```

### Variants

Each row is the body of `Inv ==` in the module above.

| Invariant | TLC | Manyan |
| --- | --- | --- |
| `(CHOOSE f \in {[b \in {TRUE} \|-> [r \in {1, 2} \|-> 0]][TRUE]} \cup {<<0>>} : FALSE)[x] = 0` | 75 | 134 |
| `(CHOOSE f \in {[b \in {TRUE} \|-> [r \in {1, 2} \|-> 0]][TRUE]} \cup {<<0>>} : FALSE) = 0` | 75 | 75 |
| `(CHOOSE f \in {[b \in {TRUE} \|-> <<0, 0>>][TRUE]} \cup {<<0>>} : FALSE)[x] = 0` | 75 | 75 |
| `x \in {[b \in {TRUE} \|-> [r \in {1, 2} \|-> 0]][TRUE]} \cup {<<0>>}` | 75 | 75 |

The abort needs the function to stay unbuilt until the union is formed. In the cases above,
that happens only when the result of `CHOOSE` is applied to a state variable.

## Expected behavior

Manyan should check whether the elements compare using the functions' values, building a
function it holds as its definition where necessary. Here it should then report TLC's
`CHOOSE` error.

## Impact

In a 10,000-entry pbt campaign, Manyan aborted on three entries: `58a9e9e7`, `a967023c` and
`e73dd9dd`. All three abort in `cup_merge`. TLC failed with status 75 on the first two and
passed `e73dd9dd`, so on that entry Manyan aborts on a specification without error. No
known-defect signature quarantines these inputs, because the abort depends on when Manyan
builds a function. `script/triager.py` classifies a Manyan exit 134 as this finding.

## Resolution

Manyan fixed this in
[`fec8f48`](https://github.com/soasme/manyan/commit/fec8f4866b2c374862274f4dea3fc4569b24dcd5),
"Classify V_LAMBDA as V_FCN in elem_kind() for set union merging", released in
[v0.18.0](https://github.com/soasme/manyan/releases/tag/v0.18.0) (soasme/manyan
`5522fa6b11b9e35ae60d46a31d0d33ce77df8ff9`, 2026-11-26). It is the first commit between
`v0.17.0` and `v0.18.0` that passes the reproduction; each commit was built with `make
manyan PGO_TOOLS=`.

Rechecked on 2026-11-26 with `v0.18.0`, built by `make manyan-build`, against the exit
statuses that TLC gave above: Manyan exits 75 on the reproduction with TLC's `CHOOSE`
error instead of aborting. FuzzTLA `dc773e71`.
