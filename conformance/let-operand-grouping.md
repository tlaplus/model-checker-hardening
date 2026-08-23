# `LET` operand grouping changes the expression

Observed share: 0.09% of aggregator deviations; TLC failed on printed TLA+ while
Apalache checked the intended typed IR.

`PrettyWriter` omits parentheses around an embedded `LET`. The body following
`IN` absorbs the next operator, so TLC and Apalache receive different trees.

## Representative MWE

```tla
---- MODULE LetOperandGrouping ----
EXTENDS Integers
VARIABLE
\* @type: Bool;
result

Printed == 0 - LET Local == 0 IN 0 < 0
Intended == 0 - (LET Local == 0 IN 0) < 0

Init == result = Intended
Next == UNCHANGED result
Inv == TRUE
====
```

TLC applied to `Printed` subtracts a Boolean from an integer. The intended IR
and `Intended` are well typed. This is the defect filed as
[`apalache-printer/issue-007`](../findings/apalache-printer/issue-007.md).
