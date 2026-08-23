# Apalache reaches division by zero; TLC does not

Observed share: 4.01% of aggregator deviations; TLC passed and Apalache failed.

Apalache validates or simplifies undefined arithmetic inside a branch that TLC
does not evaluate.

## Representative MWE

```tla
---- MODULE DivisionByZeroApalacheFails ----
EXTENDS Integers
VARIABLE
\* @type: Int;
result
Init == result = IF FALSE THEN 1 \div 0 ELSE 0
Next == UNCHANGED result
Inv == TRUE
====
```

TLC initializes `result` to zero. Apalache reports `Division by zero`.
