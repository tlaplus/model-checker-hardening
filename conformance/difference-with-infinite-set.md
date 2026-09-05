# Set difference with an infinite left operand

Observed 47 times in corpus3 (0.01% of its 435,265 aggregator deviations); TLC
failed and Apalache passed.

TLC implements set difference by enumerating its left operand. It therefore
cannot evaluate expressions such as `Nat \ {0}`, although the result is defined
by TLA+ set semantics. Apalache can retain the integer set symbolically in the
observed generated expressions.

## Representative MWE

```tla
---- MODULE DifferenceFromNat ----
EXTENDS Naturals
VARIABLE values
Init == values = Nat \ {0}
Next == UNCHANGED values
Inv == TRUE
====
```

TLC exits with status 75 and reports `Attempted to enumerate S \ T when S: Nat
is not enumerable.` This is a TLC finite-enumeration limit, not a checker
soundness defect.
