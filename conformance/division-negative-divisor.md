# Division with a negative divisor

Observed in corpus6: TLC found a counterexample while Apalache passed.

TLC implements `\div` with floor semantics. Apalache 0.62.2 truncates the
quotient toward zero when the divisor is negative. The difference changes the
truth value of an invariant and is an Apalache soundness defect, recorded as
[`apalache-bmc-008`](../findings/apalache-bmc/apalache-bmc-008.md).

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
