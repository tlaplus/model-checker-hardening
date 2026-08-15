# `PrettyWriter` does not consistently group prefix, prime, and negative operands

## Summary

Apalache's `PrettyWriter` has expression-specific rendering paths that bypass
the normal precedence wrapper. The resulting text is ambiguous or lexically
invalid even where the IR and SANY precedence numbers agree.

Observed with `org.apalache-mc:tla-io_2.13:0.61.1-SNAPSHOT`.

## Examples

The IR expression `UNCHANGED(PRIME(x))` is rendered as:

```tla
UNCHANGED x'
```

SANY reports a precedence conflict between `UNCHANGED` and prime. The complete
operand must be delimited:

```tla
UNCHANGED (x')
```

A negative `TlaInt` used as the right operand of exponentiation is rendered as:

```tla
2 ^ -1
```

SANY reports a precedence conflict between `^` and prefix minus. The intended
tree requires:

```tla
2 ^ (-1)
```

The same literal path can render unary minus applied to `TlaInt(-106)` as
`--106`. SANY treats the second hyphen as the start of a comment. The
tree-preserving form is `-(-106)`.

Prefix action and temporal operators exhibit the same missing grouping around
comparison and membership expressions. For example, when equality is the
operand of `UNCHANGED`, the output must be `UNCHANGED (x = FALSE)`, not
`UNCHANGED x = FALSE`.

## Root cause

`PrettyWriter.exToDoc` handles these expressions outside the uniform binary
operator path:

- the prime case appends `'` but does not wrap the complete prime expression
  relative to its parent;
- special cases for unary operands omit parentheses based on the operand's
  shape rather than the complete operator intervals;
- `ValEx(TlaInt(value))` writes `value.toString` without representing the
  leading minus as a precedence-bearing prefix operator.

As a result, nested prefix/postfix expressions and signed integer values can
escape the parent-precedence check.

## Scope and impact

An exhaustive replay found 8,247 failures in this group:

- 6,879 prefix action or temporal operators followed by prime;
- 969 prefix action or temporal operators adjacent to comparison or membership;
- 399 negative values following `^`, `*`, or `\div`.

Together with the precedence-metadata group in `issue-001.md`, these cases
account for all 12,231 audited precedence failures.

## Expected behavior

Every rendered operation, including prime and signed integer values, should be
grouped against its parent through one precedence-aware path. Add SANY
round-trip tests for the examples above and for `ENABLED`, `UNCHANGED`, `<>`,
and `[]` in each affected context.

