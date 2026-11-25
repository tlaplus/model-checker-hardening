# Division with a negative divisor

Observed in corpus6: TLC found a counterexample while Apalache passed.

TLC implements `\div` with floor semantics. Apalache encodes it as SMT-LIB's
Euclidean `div`, whose remainder is never negative. For a negative divisor the
two differ whenever the division is inexact: `1 \div (-2)` is -1 in TLC and 0
in Apalache, and `(-7) \div (-2)` is 3 in TLC and 4 in Apalache. The standard
module defines `\div` only for a positive divisor. The difference changes the
truth value of an invariant and is recorded as
[`apalache-bmc-008`](../findings/apalache-bmc/apalache-bmc-008.md).
A constant dividend and divisor are folded by truncation instead,
[`apalache-optimizer-002`](../findings/apalache-optimizer/apalache-optimizer-002.md).

## Representative MWE

```tla
---- MODULE NegativeDivisor ----
EXTENDS Integers
VARIABLES
\* @type: Int;
x,
\* @type: Int;
step

Init == x = 0 /\ step = 0
Next == x' = x /\ step' = step + 1
Inv == step \div (-17201) = x
Bound == step <= 5
====
```

At `step = 1`, TLC evaluates `1 \div (-17201)` to `-1` and refutes `Inv`.
Apalache instead evaluates it to `0` and verifies the invariant through the
bound.
