---
state: open
labels: [apalache]
---

# An action formula under `~>` crashes the bounded checker

## Summary

When `<<A>>_e` or `[A]_e` occurs on either side of `~>` in a temporal property,
`apalache-mc check --temporal` does not check the property. With a subscript `e`
that is not a variable, such as `x + 1` or `f[1]`, the bounded checker reports
`rewriter error: Prime operator is only implemented for variables`. With a
variable or a tuple of variables as the subscript, it reports `Illegal assignment
inside an assignment-free expression`. Both exit with status 255. The same
action formulas are checked under `[]` and `<>`, and in the equivalent
`[](P => Q)` that `P ~> Q` abbreviates.

Observed with Apalache 0.63.0, the corpus85 `tools/apalache.jar`; TLC2 version
2026.11.15.002146 (commit `bc1b506`); FuzzTLA `1a813728`.

## Reproduction

`FuzzInput.tla`:

```tla
---- MODULE FuzzInput ----
EXTENDS Integers
VARIABLES
  \* @type: Int;
  x,
  \* @type: Int;
  y
\* @type: <<Int, Int>>;
vars == <<x, y>>
Init == x = 0 /\ y = 0
Next == x' = (x + 1) % 3 /\ y' = y
Spec == Init /\ [][Next]_vars /\ WF_vars(Next)
Prop == (x = 0) ~> []<><<x' /= x>>_(x + 1)
====
```

```sh
apalache-mc check --init=Init --next=Next --temporal=Prop --length=4 FuzzInput.tla
```

```text
PASS #13: BoundedChecker                                          I@12:43:24.421
<unknown>: rewriter error: Prime operator is only implemented for variables E@12:43:24.839
EXITCODE: ERROR (255)
```

Replacing `Prop`, with the same command, and with `SPECIFICATION Spec` and
`PROPERTY Prop` for TLC:

| `Prop` | Apalache | TLC |
| --- | --- | --- |
| `(x = 0) ~> []<><<x' /= x>>_(x + 1)` | exit 255, prime | must be of forms |
| `(x = 0) ~> [][x' /= x]_(x + 1)` | exit 255, prime | must be of forms |
| `([]<><<x' /= x>>_(x + 1)) ~> (x = 2)` | exit 255, prime | must be of forms |
| `<>(<<TRUE>>_(1 + 1)) ~> FALSE` | exit 255, prime | must be of forms |
| `(x = 0) ~> []<><<x' /= x>>_vars` | exit 255, illegal assignment | must be of forms |
| `[]((x = 0) => []<><<x' /= x>>_(x + 1))` | `NoError` | must be of forms |
| `[]((x = 0) => []<><<x' /= x>>_vars)` | `NoError` | must be of forms |
| `[]<><<x' /= x>>_(x + 1)` | `NoError` | no error |
| `<><<x' /= x>>_(x + 1)` | `NoError` | must be of forms |
| `(x = 0) ~> (x = 2)` | `NoError` | no error |

With the falsifiable `<<x' = 5>>_(x + 1)` in place of `<<x' /= x>>_(x + 1)`,
Apalache reports a counterexample for the `[](P => Q)` form and still exits with
the prime error for the `~>` form.

TLC accepts an action in a temporal formula only as `<>[]A` or `[]<>A` after
normalization, and rejects every `~>` row; that limitation is recorded in
[tlc-temporal-formula-limits](../../conformance/tlc-temporal-formula-limits.md).
The comparison is therefore between Apalache's verdicts on equivalent formulas:
`P ~> Q` is defined as `[](P => <>Q)`, and `<>[]<>A` equals `[]<>A`.

The subscript expands to `e' /= e` in `<<A>>_e` and to `e' = e` in `[A]_e`. The
illegal-assignment message for a variable subscript is the one that
[apalache-assignments-001](../apalache-assignments/apalache-assignments-001.md)
reports for `<<A>>_v` used as a guard, which suggests that the `~>` encoding
copies the expansion into a position the assignment analysis treats as
assignment-free; the non-variable subscript reaches the bounded checker with the
prime still applied to `x + 1`. The Apalache source was not inspected.

## Corpus evidence

The `pbt` `module` corpus85, run without known-defect quarantine, has nine
Apalache crashes with the prime error: `14d81c02`, `2c4631d1`, `6f80c427`,
`74c05a0f`, `812c74bf`, `c073caaa`, `c32beeef`, `dba6faab` and `f5704cc3`. Each
property applies `~>` to a formula containing `<<A>>_e` under `<>` or `[A]_e`
under `[]`, with a subscript such as a `CASE`, an `IF`, a `CHOOSE` or a function
application. `6f80c427` reproduces from its Apalache JSON IR; replacing the
subscript with `FALSE` or dropping `~> FALSE` makes Apalache report a
counterexample, and replacing it with the variable `step` gives the illegal
assignment. The reproduction above was written from that shape.

The triager files the illegal-assignment variant under apalache-assignments-001,
so the corpus count of this finding covers only the prime error.

## Expected behavior

Apalache checks `P ~> Q` as it checks `[](P => <>Q)`, for any state-level
subscript of an action formula in `P` or `Q`.

## Impact

Leads-to properties whose operands mention an action formula, including the
common `<<A>>_vars`, cannot be checked. The failure is reported as an internal
error or as an assignment error that points at no source location.
