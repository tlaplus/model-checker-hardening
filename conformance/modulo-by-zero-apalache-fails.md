# Apalache reaches modulo by zero; TLC does not

Observed share: 4.04% of aggregator deviations; TLC passed and Apalache failed.

TLC short-circuits unreachable branches. Apalache's preprocessing visits the
undefined arithmetic expression before the surrounding branch is eliminated.

## Representative MWE

```tla
---- MODULE ModuloByZeroApalacheFails ----
EXTENDS Integers
VARIABLE
\* @type: Int;
result
Init == result = IF FALSE THEN 1 % 0 ELSE 0
Next == UNCHANGED result
Inv == TRUE
====
```

TLC initializes `result` to zero. Apalache reports `Mod by zero`. This is an
evaluation-order difference; evaluated modulo by zero is undefined for both.
