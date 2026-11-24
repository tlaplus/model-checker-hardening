---
state: open
labels: [manyan]
---

# Manyan rejects a Boolean literal or `STRING` as an action subscript

## Summary

Manyan's parser rejects an action subscript or fairness subscript that is one of the
keywords `FALSE`, `TRUE`, `STRING` or `BOOLEAN`:

```text
***Parse Error***
Encountered "FALSE": expected a subscript at line 5, column 26 in Subscript.tla
```

SANY accepts these subscripts, and TLC checks such modules. The forms affected are
`[A]_e`, `<<A>>_e`, `[][A]_e`, `WF_e(A)` and `SF_e(A)`. Manyan accepts the same
subscripts in parentheses, such as `[A]_(FALSE)`, and it accepts identifiers, tuples,
sets and records.

Observed with Manyan `v0.17.0` (soasme/manyan
`a9ca4f38028f4e8b4cc3f016167d071b55e3abe0`, 2026-11-24), built with GCC 13.3.0
(`make manyan PGO_TOOLS=`). The reference is SANY in TLC `7e64fd0`
(tlaplus/tlaplus `7e64fd0376ce8a3d9087fc267b917564df7e2c3a`). Apalache 0.63.0 is
not involved. FuzzTLA `f8e3696a`.

## Reproduction

```tla
---- MODULE Subscript ----
VARIABLE x
Init == x = 0
Next == x' = x
Spec == Init /\ [][Next]_FALSE
====
```

```sh
manyan -config Subscript.cfg Subscript   # SPECIFICATION Spec
```

Manyan exits 150 with the parse error above. TLC `7e64fd0` checks the module and
finds no error (1 distinct state, exit 0).

### Variants

Each row is the body of `P ==` in a module that declares `VARIABLE x`. SANY
accepts every row.

| Expression | Manyan |
| --- | --- |
| `[x' = x]_FALSE`, `[x' = x]_TRUE` | rejected |
| `<<x' = x>>_FALSE`, `ENABLED <<x' = x>>_FALSE` | rejected |
| `[][x' = x]_BOOLEAN`, `<<x' = x>>_BOOLEAN` | rejected |
| `WF_FALSE(x' = x)`, `SF_TRUE(x' = x)`, `WF_STRING(x' = x)` | rejected |
| `[x' = x]_Nat`, `WF_Nat(x' = x)` | accepted |
| `[x' = x]_(FALSE)`, `[x' = x]_<<x>>`, `[x' = x]_{x}` | accepted |

Both SANY and Manyan reject a number or a string literal as a subscript, as in
`[x' = x]_1` and `[x' = x]_"s"`.

## Expected behavior

Manyan accepts every subscript that SANY accepts, as its README claims compatibility
with TLC.

## Impact

FuzzTLA generates action properties and fairness conditions over arbitrary state
functions, and it renders a Boolean literal subscript without parentheses. In
a probe of 430 corpus87 pbt entries, 64 (15%) contain such a subscript, mostly
`WF_FALSE` and `SF_FALSE`. Manyan rejected all 64 as parse errors, and TLC gave them
ordinary verdicts ([ADR 0033](../../docs/decisions/0033-generic-checker.md#probe)).
The signature `manyan-literal-subscript` in
[`signatures/manyan-defects.toml`](../../signatures/manyan-defects.toml) quarantines
these inputs in a corpus that runs Manyan.
