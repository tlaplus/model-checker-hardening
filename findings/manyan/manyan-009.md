---
state: open
labels: [manyan]
---

# Manyan rejects `[]WF_v(A)` as a temporal formula of the wrong form

## Summary

Manyan rejects a property of the form `[]WF_v(A)`:

```text
Error: Temporal formulas containing actions must be of forms <>[]A or []<>A.
```

TLC accepts the same property and checks it. TLC has this message too, but reports it only
for other forms, such as `[]SF_v(A)` and `<>WF_v(A)`, which Manyan rejects as well.
`WF_v(A)` is `[]<>~(ENABLED <<A>>_v) \/ []<>(<<A>>_v)`, and `[][]<>F` is `[]<>F`, so TLC can
check `[]WF_v(A)`.

Observed with Manyan `v0.17.0` (soasme/manyan
`a9ca4f38028f4e8b4cc3f016167d071b55e3abe0`, 2026-11-24), built with GCC 13.3.0
(`make manyan PGO_TOOLS=`). The reference is TLC `bc1b506` (tlaplus/tlaplus
`bc1b506bdcad80bb518bf3776a9161c34ff0fc4c`) on OpenJDK 25.0.5.1. Apalache is not
involved. FuzzTLA `390ea420`.

## Reproduction

`Wf.tla`:

```tla
---- MODULE Wf ----
EXTENDS Naturals
VARIABLE x
Init == x = 0
Next == x < 2 /\ x' = x + 1
Spec == Init /\ [][Next]_x
P == []WF_x(FALSE)
====
```

`Wf.cfg`:

```text
SPECIFICATION Spec
PROPERTY P
```

```sh
manyan -workers 1 -deadlock -config Wf.cfg Wf.tla
```

Manyan exits 255 with the error above.
`java -cp tla2tools.jar tlc2.TLC -workers 1 -deadlock -config Wf.cfg Wf` exits 0, with
`Model checking completed. No error has been found.`

### Variants

Each row is the body of `P ==` in the module above.

| Property | TLC | Manyan |
| --- | --- | --- |
| `[]WF_x(FALSE)` | 0 | 255, wrong form |
| `[]WF_x(x = 5)` | 0 | 255, wrong form |
| `WF_x(FALSE)` | 0 | 0 |
| `[]SF_x(FALSE)` | 255, wrong form | 255, wrong form |
| `<>WF_x(FALSE)` | 255, wrong form | 255, wrong form |
| `[]<>(<<FALSE>>_x)` | 13 | 13 |

## Expected behavior

Manyan should accept the forms that TLC accepts, and check `[]WF_v(A)` as `WF_v(A)`.

## Impact

In a 25,000-entry pbt campaign, 10 Manyan crashes had this message. On five of them TLC did
not crash: it passed `86fcfc53` and failed with status 75 on four. TLC crashed on the other
five. The generated properties nest fairness under `[]`, as in
`[](WF_(<<...>>)(ENABLED FALSE))`.

`script/triager.py` has no rule for this message. TLC prints the same message for the forms
it rejects, so the message alone does not show which tool is at fault.
