# Apalache reaches `0^0`; TLC does not

Observed share: 1.15% of aggregator deviations; TLC passed and Apalache failed.

The undefined expression occurs in a branch skipped by TLC but visited by
Apalache preprocessing.

## Representative MWE

```tla
---- MODULE ZeroPowerZeroApalacheFails ----
EXTENDS Integers
VARIABLE
\* @type: Int;
result
Init == result = IF FALSE THEN 0 ^ 0 ELSE 0
Next == UNCHANGED result
Inv == TRUE
====
```

TLC initializes `result` to zero. Apalache reports `0 ^ 0 is undefined`. Both
tools reject the expression when it is actually evaluated.
