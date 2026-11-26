---
state: closed
labels: [manyan]
---

# Manyan requires a label in a later bound's set to take an earlier bound variable

## Summary

In a construct with several bounds, such as `[a \in S, b \in T |-> e]`, a label inside
`T` must, according to Manyan, take `a` as a formal parameter:

```text
Label lab must contain formal parameter `a'.
```

But `a` is not in scope in `T`. Manyan itself rejects a reference to `a` there, as SANY
does (`Unknown operator: a`), so the label cannot take `a` as a parameter either. SANY
accepts the label without parameters. The constructs affected are the function
constructor, set map, and `\A` and `\E` with several bounds. A label in the first bound's
set is accepted.

Observed with Manyan `v0.17.0` (soasme/manyan
`a9ca4f38028f4e8b4cc3f016167d071b55e3abe0`, 2026-11-24), built with GCC 13.3.0
(`make manyan PGO_TOOLS=`). The reference is SANY in TLC `7e64fd0`
(tlaplus/tlaplus `7e64fd0376ce8a3d9087fc267b917564df7e2c3a`). Apalache 0.63.0 is
not involved. FuzzTLA `f8e3696a`.

Fixed in [Manyan v0.18.0](https://github.com/soasme/manyan/releases/tag/v0.18.0), by
`b2fcb97`.

## Reproduction

```tla
---- MODULE LaterBound ----
EXTENDS Integers
P == [a \in {1}, b \in (lab :: {2}) |-> 1]
====
```

```sh
manyan -lint LaterBound
```

Manyan exits 150:

```text
Semantic errors:

*** Errors: 1

line 3, col 32 to line 3, col 34 of module LaterBound

Label lab must contain formal parameter `a'.
```

`java -cp tla2tools.jar tla2sany.SANY LaterBound.tla` (TLC `7e64fd0`) reports no
error.

### Variants

Each row is the body of `P ==` in a module that extends `Integers`.

| Expression | SANY | Manyan |
| --- | --- | --- |
| `[a \in {1}, b \in (lab :: {2}) \|-> 1]` | accepted | label needs `a` |
| `{1 : a \in {1}, b \in (lab :: {2})}` | accepted | label needs `a` |
| `\E a \in {1}, b \in (lab :: {2}) : TRUE` | accepted | label needs `a` |
| `\A a \in {1}, b \in (lab :: {2}) : TRUE` | accepted | label needs `a` |
| `[a \in (lab :: {1}), b \in {2} \|-> 1]` | accepted | accepted |
| `[a \in {1}, b \in {a} \|-> 1]` | `Unknown operator: a` | rejected |
| `[a \in {1} \|-> lab :: 1]` | label needs `a` | label needs `a` |

## Expected behavior

A label requires the bound variables in whose scope it lies. A label in a bound's set
lies outside the scope of every variable of that construct, so it needs none of them.

## Impact

FuzzTLA labels subexpressions of generated code and generates function constructors
and set maps with several bounds. In a probe of 430 corpus87 pbt entries, Manyan
rejected 18 (4%) with this error, and SANY accepted all 18
([ADR 0033](../../docs/decisions/0033-generic-checker.md#probe)). The known-defect
pattern language skips labels, so no signature quarantines these inputs.

## Resolution

Manyan fixed this in
[`b2fcb97`](https://github.com/soasme/manyan/commit/b2fcb974e8450f4cab71b1114bbf348f3429c27c),
"Resolve all bound sets before adding bound variables to label_bounds", released in
[v0.18.0](https://github.com/soasme/manyan/releases/tag/v0.18.0) (soasme/manyan
`5522fa6b11b9e35ae60d46a31d0d33ce77df8ff9`, 2026-11-26). It is the first commit between
`v0.17.0` and `v0.18.0` that passes the reproduction; each commit was built with `make
manyan PGO_TOOLS=`.

Rechecked on 2026-11-26 with `v0.18.0`, built by `make manyan-build`, against the exit
statuses that TLC gave above: `manyan -lint` exits 0 on the reproduction and on the
three other rows that SANY accepts and Manyan rejected. FuzzTLA `dc773e71`.
