---
state: closed
labels: [manyan]
---

# Manyan exits 255 for a domain error in the next-state relation where TLC exits 75

## Summary

Manyan exits 255 when the next-state relation applies a function constructor
`[r \in S |-> e]` outside its domain. TLC exits 75 for the same error. Both tools print the
same message. Exit status 255 marks a crash in TLC's conventions, so an evaluation error that
TLC reports normally looks like a Manyan crash.

Manyan exits 75, like TLC, in these cases:
- the same application in `Init` or in an invariant;
- an out-of-domain record field;
- an out-of-domain tuple index.

Observed with Manyan `v0.17.0` (soasme/manyan
`a9ca4f38028f4e8b4cc3f016167d071b55e3abe0`, 2026-11-24), built with GCC 13.3.0
(`make manyan PGO_TOOLS=`). The reference is TLC `bc1b506` (tlaplus/tlaplus
`bc1b506bdcad80bb518bf3776a9161c34ff0fc4c`) on OpenJDK 25.0.5.1. Apalache is not
involved. FuzzTLA `390ea420`.

Fixed in [Manyan v0.18.0](https://github.com/soasme/manyan/releases/tag/v0.18.0), by
`8b58d06`.

## Reproduction

`NextDomain.tla`:

```tla
---- MODULE NextDomain ----
EXTENDS Naturals
VARIABLE x
Init == x = 0
Next == x' = [r \in {5, 6} |-> 1][7]
Spec == Init /\ [][Next]_x
====
```

`NextDomain.cfg`:

```text
SPECIFICATION Spec
```

```sh
manyan -workers 1 -deadlock -config NextDomain.cfg NextDomain.tla
```

Manyan exits 255:

```text
Finished computing initial states: 1 distinct state generated.
Error: In applying the function
(5 :> 1 @@ 6 :> 1),
the first argument is:
7
which is not in its domain.

Error: The behavior up to this point is:
State 1: <Initial predicate>
x = 0
```

`java -cp tla2tools.jar tlc2.TLC -workers 1 -deadlock -config NextDomain.cfg NextDomain`
reports the same domain error inside `TLC threw an unexpected exception` and exits 75.

### Variants

Each row is the body of `Next ==` in the module above, unless the row says otherwise.

| Expression | TLC | Manyan |
| --- | --- | --- |
| `x' = [r \in {5, 6} \|-> 1][7]` | 75 | 255 |
| `x' = [r \in 1..2 \|-> 1][3]` | 75 | 255 |
| `x' = [r \in {"a"} \|-> 1]["c"]` | 75 | 255 |
| `x' = [r \in {TRUE} \|-> 1][FALSE]` | 75 | 255 |
| `x' = [r \in {{1}} \|-> 1][{2}]` | 75 | 255 |
| `x' = [a \|-> 1, b \|-> 2]["c"]` | 75 | 75 |
| `x' = <<1>>[2]` | 75 | 75 |
| `Init == x = [r \in {"a", "b"} \|-> 1]["c"]`, `Next == UNCHANGED x` | 75 | 75 |

## Expected behavior

Manyan should exit 75 for this error, as TLC does.

## Impact

In a 10,000-entry pbt campaign, 86 of Manyan's 274 crashes were this error on entries where
TLC failed with status 75. An example is `00852dca`. The generic checker stage records them
as crashes, so they look like 86 new Manyan crashes.

No known-defect signature quarantines these inputs, because the error depends on run-time
values. `script/triager.py` classifies the crashes instead. It matches an exit 255 whose
first error is a domain error, printed right after the initial states, with no
`TLC threw an unexpected exception` wrapper. In the campaign this rule picked out 87 crashes.
TLC crashed on one of them as well.

## Resolution

Manyan fixed this in
[`8b58d06`](https://github.com/soasme/manyan/commit/8b58d064e1211520fc23e5dcbee3836005bf4fee),
"Use fail() instead of fail_module() in lambda_outside()", released in
[v0.18.0](https://github.com/soasme/manyan/releases/tag/v0.18.0) (soasme/manyan
`5522fa6b11b9e35ae60d46a31d0d33ce77df8ff9`, 2026-11-26). It is the first commit between
`v0.17.0` and `v0.18.0` that passes the reproduction; each commit was built with `make
manyan PGO_TOOLS=`.

Rechecked on 2026-11-26 with `v0.18.0`, built by `make manyan-build`, against the exit
statuses that TLC gave above: Manyan exits 75 on the five rows that exited 255, inside
TLC's unexpected-exception wrapper, as TLC does. FuzzTLA `dc773e71`.
