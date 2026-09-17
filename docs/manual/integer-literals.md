# Integer literals

> **Status:** Implemented.
> [ADR 0012](../decisions/0012-integer-literals.md) records the design and the
> measurements behind it.

Generated integers default to `1` rather than `0`, and literals mix small values,
arithmetic boundary values such as `2^31 − 1`, and wide byte payloads.

## 1. Configuration

```toml
[generator]
max_integer_bytes = 16
# Closed integer terminal, and centre of small integer literals.
integer_base = 1
# Input bytes move a small integer literal within base ± spread.
integer_literal_spread = 4
# How integer literals decode: "wide" byte payloads, "small" values around integer_base,
# or "boundary" values such as 2^31 - 1 besides both.
integer_literals = "boundary"
```

| Key | Meaning |
| --- | --- |
| `integer_base` | Value of a starved integer expression, and centre of small literals. |
| `integer_literal_spread` | Small literals lie in `integer_base ± spread`, `0..127`. |
| `integer_literals` | `"wide"`: every literal is a payload of up to `max_integer_bytes`. `"small"`: half small, half wide. `"boundary"`: half small, a quarter boundary values, a quarter wide. |

## 2. Boundary values

`-1, 0, 1, 2, 15, 16, 255, 256, 32767, 32768, 65536, 46340, 46341, 2^30,
2^31 − 1, −2^31, 2^31, −2^31 − 1, 2^63 − 1`.

The last three lie outside TLC's signed 32-bit range. The shipped signature
`integer-literal-outside-tlc-range` quarantines them when they occur as literals
([known-defect signatures](known-defect-signatures.md)); overflow computed by
arithmetic is checked.

Changing any of these keys reinterprets stored inputs: a corpus must keep the
values it was created with.
