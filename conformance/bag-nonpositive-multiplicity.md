# Bag with a non-positive multiplicity

Observed share: 0.50% of corpus43's aggregator deviations; TLC failed, and
Apalache passed (45) or reported a counterexample (164).

A bag maps each element to a positive multiplicity. The generator types a bag
as `a -> Int`, so a multiplicity can be 0 or negative. Such a function is not a
bag, and the Community Modules definition of `FoldBag`, which `SumBag` and
`ProductBag` call, is undefined for it: it evaluates `pow[B[x]]` for a function
`pow` with domain `Nat \ {0}`. TLC's Java override of `FoldBag` rejects the
value. Apalache rewires `SumBag(B)` as the sum of `y * B[y]` and
`ProductBag(B)` as the product of `y ^ B[y]` over `DOMAIN B`, which are defined
for every multiplicity.

## Representative MWE

```tla
---- MODULE BagWithNonpositiveMultiplicity ----
EXTENDS Integers, BagsExt
VARIABLE
\* @type: Int;
result
Init == result = SumBag([x \in {1, 2} |-> 0])
Next == UNCHANGED result
Inv == result = 0
====
```

With `CommunityModules.jar` (commit `9aae8ea`) on its class path, TLC
(commit `142d0ba`) reports

```text
Error: Applying FoldBag to the following value,
which is not an element of Nat:
0 (in: 1:>0)
```

and exits 75. The message names `Nat`, although 0 is in `Nat`; the override
requires a positive multiplicity. Apalache 0.62.2 (build `f0dec98`) checks the
invariant and exits 0. With the multiplicity `-1`, TLC reports the same error
for `-1`, and Apalache evaluates the sum to `-3`.

Portable models pass only functions into `Nat \ {0}` to the `BagsExt`
operators, for example by building them with `SetToBag` and `BagAdd`.
