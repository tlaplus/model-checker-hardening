---
state: open
labels: [apalache]
---

# Integer division truncates toward zero for a negative divisor

## Summary

Apalache 0.62.2 (build `f0dec98`) evaluates integer division with a negative
divisor by truncating toward zero. TLC evaluates the same TLA+ expression with
floor division. Consequently, Apalache can verify an invariant that TLC
correctly refutes.

## Reproduction

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

Run Apalache with:

```sh
java -Xmx1g -jar target/apalache.jar check \
  --init=Init --next=Next --inv=Inv --length=5 NegativeDivisor.tla
```

Apalache reports `NoError`. TLC reaches `step = 1`, where `1 \div (-17201)`
is `-1`, and reports an invariant violation. The corpus reproducer is
[`0a79bcf...`](../../corpus6/03aggregator-fail/0a79bcf94b8f0d3ac9fd5b0640ccfabfdd05ec0a06a3dd945729bc2090be3ef8.cbor).

## Expected behavior

Apalache should implement TLA+ integer division consistently with TLC for
negative divisors and report the invariant violation at the first successor
state.

## Impact

Models that divide by a negative integer can receive a false verification
result from Apalache. FuzzTLA records this as a TLC-counterexample/
Apalache-pass conformance failure.
