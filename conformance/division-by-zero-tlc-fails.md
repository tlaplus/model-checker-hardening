# TLC reaches division by zero; Apalache does not

Observed share: 0.18% of aggregator deviations; TLC failed and Apalache passed.

Both tools reject evaluated division by zero. The corpus cases differ because
the TLA+ evaluator and Apalache's typed-IR rewrites reach different generated
subexpressions.

## Representative MWE

```tla
---- MODULE DivisionByZeroTlcFails ----
EXTENDS Integers
VARIABLE
\* @type: Int;
result
Init == result = 1 \div 0
Next == UNCHANGED result
Inv == TRUE
====
```

The module isolates the operation TLC reached. In the corpus it occurs below a
larger generated construct eliminated by Apalache's typed-IR path. Feeding the
isolated module to Apalache also fails; the corpus disagreement is evaluation
order, not a different mathematical value for `1 \div 0`.
