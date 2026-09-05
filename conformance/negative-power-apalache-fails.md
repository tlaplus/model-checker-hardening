# Apalache reaches a negative power; TLC does not

Observed in corpus6: four Apalache crash-classified results reported `Negative
power` while TLC passed. A negative exponent is outside the operator domain;
the difference is evaluation order, not a checker defect.

## Representative MWE

```tla
---- MODULE NegativePowerHidden ----
EXTENDS Integers
VARIABLE
\* @type: Int;
x

Init == x = IF FALSE THEN 0 ^ (-1) ELSE 0
Next == UNCHANGED x
Inv == TRUE
====
```

TLC evaluates the selected `ELSE` branch and verifies the module. Apalache's
constant simplifier reports `Negative power at 0 ^ -1` before checking states
and exits with status 255. This is analogous to the documented
[division-by-zero evaluation-order difference](division-by-zero-apalache-fails.md).
