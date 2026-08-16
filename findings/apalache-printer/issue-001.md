# `PrettyWriter` uses incorrect precedence intervals for TLA+ operators

## Summary

Apalache's `PrettyWriter` can emit unparseable TLA+ when an expression contains
sequential composition (`\cdot`) or a set prefix (`UNION`, `SUBSET`, or
`DOMAIN`). Their IR precedence metadata does not match SANY's interval-valued
precedence table, so the generic parenthesization test omits required
parentheses.

Observed with `org.apalache-mc:tla-io_2.13:0.61.1-SNAPSHOT`.

## Minimal examples

For an equality whose right operand is a sequential composition, the writer
emits:

```tla
a = b \cdot c
```

SANY rejects this with a precedence conflict between `=` and `\cdot`. The IR
tree requires:

```tla
a = (b \cdot c)
```

For a Cartesian product whose left operand is unary union, the writer emits:

```tla
UNION S \X T
```

SANY rejects this with a precedence conflict between `UNION` and `\times`. The
IR tree requires:

```tla
(UNION S) \X T
```

## Root cause

The current IR and SANY precedences are:

| Printed operator | Apalache IR | SANY | Assessment |
|---|---:|---:|---|
| `\cdot` | `(13,13)` | `[50,140]` | The IR collapses an interval to a scalar. |
| `UNION`, `SUBSET` | `(8,8)` | `[100,130]` | The IR interval is incorrect. |
| `DOMAIN` | `(9,9)` | `[100,130]` | The IR interval is incorrect. |
| `\X` / `\times` | `(10,13)` | `[100,130]` | The intervals are aligned. |

SANY uses a ten-times numeric scale. On Apalache's scale, the first three rows
should be `(5,14)`, `(10,13)`, and `(10,13)`, respectively. The existing
`wrapWithParen` logic then sees the interval overlap and groups the operand.

## Scope and impact

An exhaustive replay of 536,656 generated inputs found 12,231 precedence
failures. This root cause accounts for 3,984 of them:

- 3,870 involve `\cdot`;
- 114 involve `UNION`, `SUBSET`, or `DOMAIN` adjacent to `\times`.

These cases form one precedence-metadata defect. The individual operator pairs
should be regression cases for the same fix, not separate issues.

## Expected behavior

`PrettyWriter` output should parse to the same operator tree represented by the
input IR. Align the IR metadata with SANY's interval table and add round-trip
tests for each affected operator in both left- and right-operand positions.

