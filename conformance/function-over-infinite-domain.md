# Function construction over an infinite domain

Observed once in corpus4 (0.02% of its 4,282 aggregator deviations); TLC failed
and Apalache passed.

TLC cannot construct a function whose domain is `Nat`, because it tries to
compute the domain's cardinality while evaluating the function constructor.
The expression is defined by TLA+ semantics; the failure is a finite-value
representation limit rather than a malformed specification.

The corpus input is
[`7e2a1a43...75dd8.cbor`](../corpus4/03aggregator-fail/7e2a1a43ce1d07176b88f1c0413db7365168b8fdc6ea06a36a36418474475dd8.cbor).

## Representative MWE

```tla
---- MODULE FunctionOverNat ----
EXTENDS Naturals
VARIABLE f
Init == f = [i \in Nat |-> 0]
Next == UNCHANGED f
Inv == TRUE
====
```

TLC reports error 2103: `Attempted to compute the number of elements in the
overridden value Nat.`

The MWE isolates TLC's limitation. The observed Apalache result applies to the
complete generated expression and does not claim general support for storing
infinite-domain functions.
