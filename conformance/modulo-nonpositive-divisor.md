# Modulo with a nonpositive divisor

Observed share: 0.29% of aggregator deviations; TLC failed and Apalache passed.

TLC restricts the divisor of `%` to a positive integer. Apalache's symbolic
integer encoding accepts some negative-divisor forms. This differs from the
separate zero-divisor cases that Apalache explicitly classifies as input errors.

## Representative MWE

```tla
---- MODULE ModuloNonpositiveDivisor ----
EXTENDS Integers
VARIABLE
\* @type: Int;
result
Init == result = 1 % (-1)
Next == UNCHANGED result
Inv == TRUE
====
```

TLC reports that the divisor must be a positive number. Portable models avoid
relying on `%` for nonpositive divisors.
