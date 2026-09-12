# Membership test with `Int` or `Nat` as the element

Observed twice in corpus14 (<0.01% of its 62,487 aggregator deviations); TLC
failed, and Apalache completed once with a pass and once with a counterexample.

TLC decides `e \in S` for a materialized finite `S` by comparing `e` with each
element. It refuses the comparison when `e` is a predefined infinite set,
because it has no finite representation to compare against. The diagnostic
differs from the one recorded for
[a finite set containing `Nat`](finite-set-containing-infinite-set.md): there
TLC rejects a comparison while materializing the *containing* set, here it
rejects the membership test itself, and the message names the element rather
than the set.

Both corpus inputs reach it the same way, through a filter over a `SUBSET` of a
singleton: `Nat \in {s \in SUBSET {step} : ...}` and the same expression with
`Int`. The corpus inputs are
[`5805330c...f9cd.cbor`](../corpus14/03aggregator-fail/5805330cb769f299e478e014eaa628a4ba17b7606183d4ae0bfa9888d92af9cd.cbor)
and
[`6de625f1...f54f.cbor`](../corpus14/03aggregator-fail/6de625f176b7da8d0ef7448bebc832060415fdafc3b7f78cc451bbff79acf54f.cbor).

## Representative MWE

```tla
---- MODULE NatAsMembershipElement ----
EXTENDS Integers, Naturals
VARIABLE
\* @type: Int;
step
Init == step = 0
Next == step' = step + 1
\* @type: (() => Bool);
Inv == Nat \notin { s \in SUBSET {step} : TRUE }
Bound == step <= 4
====
```

TLC exits with status 75 and reports:

```text
Error: Attempted to check if the non-enumerable value
Nat
is element of
{{}, {0}}
```

The MWE isolates TLC's limitation. Apalache does not complete it: it reports
`rewriter error: SetInRule.powSetIn is not implemented for infinite type
InfSet[CellTFrom(Int)]` and exits 255. The observed Apalache completions apply
to the complete generated expressions, in which the membership test is not the
operation Apalache reaches.

Writing the right operand as a set literal instead of a filter produces TLC's
other message, `Attempted to check equality of the set {} with the value: Nat`,
which the [finite set containing `Nat`](finite-set-containing-infinite-set.md)
row already covers.
