---
state: open
labels: [apalache]
---

# A function over an infinite domain gets an empty `DOMAIN`

## Summary

Apalache accepts the function constructor `[x \in Nat |-> e]`, reports no
warning, and then answers queries about it as if its domain were empty. It
proves `DOMAIN [i \in Nat |-> 0] = {}`, proves the false invariant
`step \notin DOMAIN [i \in Nat |-> 0]`, and reports a counterexample for the
true invariant `step \in DOMAIN [i \in Nat |-> 0]`. The same happens with `Int`.
A finite domain behaves correctly, and `step \in Nat` on its own is answered
correctly, so the defect is specific to the domain of a function constructed
over an infinite set.

This is a soundness defect in the direction that matters: Apalache misses a real
invariant violation without reporting any limitation.

Observed with Apalache 0.62.2, build `f0dec98`. The `corpus9` run contains one
aggregator deviation with this cause: TLC reports the violation and Apalache
passes. See the [`04bfaff6...` input](../../corpus9/03aggregator-fail/04bfaff64bfbb7c61d4e4ee5912454ae9d9cfb8af5e8d86040eff74efb09b687.cbor),
whose invariant reduces to `step \notin DOMAIN [arg1 \in Nat |-> ...]`.

## Reproduction

Save this module as `DomainOverNat.tla`:

```tla
---- MODULE DomainOverNat ----
EXTENDS Integers

VARIABLE
\* @type: Int;
step

Init == step = 0
Next == step' = step + 1
Inv == step \notin DOMAIN [i \in Nat |-> 0]
Bound == step <= 5
====
```

Run:

```sh
java -Xmx1g -jar target/apalache.jar check \
  --init=Init --next=Next --inv=Inv --length=5 --no-deadlock DomainOverNat.tla
```

Apalache reports:

```text
The outcome is: NoError
EXITCODE: OK
```

TLC, given the same module with `INIT Init`, `NEXT Next`, `INVARIANT Inv`,
`CONSTRAINT Bound`, reports the violation at the initial state, where
`step = 0` and `0 \in Nat`:

```text
Error: Invariant Inv is violated by the initial state:
TLC error code 2107 mapped to exit status 12
```

Three further invariants isolate the cause, each with `--length=5`:

| Invariant | TLC | Apalache |
|---|---|---|
| `DOMAIN [i \in Nat \|-> 0] = {}` | `Attempted to compare overridden value Nat with non-overridden value:` | proves it |
| `Cardinality(DOMAIN [i \in Nat \|-> 0]) = 0` | `Attempted to compute cardinality of the value` | proves it |
| `step \notin DOMAIN [i \in {0, 1} \|-> 0]` | violated | counterexample |
| `step \notin Nat` | violated | counterexample |

Apalache therefore builds the constructor with an empty domain rather than
rejecting it, and both checkers agree once the domain is finite.

## Expected behavior

Reject a function constructed over an infinite domain through Apalache's
ordinary unsupported-input mechanism, as it already rejects `Seq(S)`, a set map
over an infinite set, and integer ranges with nonconstant bounds. Silently
substituting the empty function makes every downstream answer about the
function unsound.

## Impact

A specification that constructs a function over `Nat` or `Int` can be reported
as verified while an invariant over its domain is false. Nothing in the output
indicates that a construct was approximated, so the result is indistinguishable
from a sound proof. TLC rejects the same expression as a representation limit
(see [function-over-infinite-domain](../../conformance/function-over-infinite-domain.md)),
so the two checkers disagree with no diagnostic on either side to explain why.
