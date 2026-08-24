# `PrettyWriter` lets `LET` bodies absorb surrounding operators

## Summary

Apalache's `PrettyWriter` omits parentheses when a `LET` expression is used as
an operand. Since the body following `IN` extends to the right, a following
operator becomes part of the `LET` body and the printed expression no longer
represents the input IR tree.

Observed in three aggregator failures with
`org.apalache-mc:tla-io_2.13:0.61.1-SNAPSHOT`.

## Minimal example

For the IR tree whose comparison is outside the subtraction,
`PrettyWriter` emits:

```tla
0 - LET Local == 0 IN 0 < 0
```

SANY parses this as:

```tla
0 - (LET Local == 0 IN (0 < 0))
```

The intended tree requires:

```tla
0 - (LET Local == 0 IN 0) < 0
```

The corpus also contains set-inclusion/conjunction and
tuple-equality/implication variants. In every case the operator following the
`LET` is absorbed into its body, producing a different but syntactically valid
module.

## Expected behavior and impact

`PrettyWriter` must delimit a `LET` expression whenever it is embedded as an
operand whose surrounding syntax can extend across `IN`. Add round-trip tests
that compare the parsed tree, not merely SANY acceptance.

This is semantic source corruption. Downstream TLC failures describe the
misprinted tree—for example, arithmetic applied to a Boolean—rather than the
typed IR supplied to the writer.
