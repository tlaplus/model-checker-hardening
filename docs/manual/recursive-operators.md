# Recursive operators

> **Status:** Implemented.
> [ADR 0015](../decisions/0015-diff-linked-libraries.md) records the design and the probe
> behind it.

A recursion campaign compares TLC's evaluation of recursive definitions with Apalache's
evaluation of equivalent non-recursive definitions. Every library operator comes as a
pair of modules:

- the TLC module uses `RECURSIVE`, `LET RECURSIVE`, mutual recursion or recursive
  functions;
- the Apalache module uses folds or closed forms.

The aggregator compares their verdicts.

## 1. Setting up a corpus

```sh
./bin/fuzztla init --corpus corpus-rec --library libraries/recursion.toml
./bin/fuzztla run --how=pbt --corpus corpus-rec
```

The library ships with fuzztla, so nothing is downloaded. `init` copies
`libraries/recursion/` into `<corpus>/tla/recursion/`, and the corpus keeps that copy.

## 2. Linkage

```toml
[generator]
classpath = ["recursion"]
custom_operators = [
  { module = "RecursionApalache", link = "diff", tlc_module = "RecursionTLC",
    operators = ["SetSum", "SetSumLetRec", "SeqReverse"] },
]
```

| `link` | Apalache input | Parser and TLC input |
| --- | --- | --- |
| `"diff"` | the definitions of `module`, typechecked as root | `INSTANCE <tlc_module>`, resolved on `generator.classpath` |

`module` provides the signatures and the mangled names; richness scoring and
known-defect matching use its definitions. fuzztla never imports `tlc_module`: only SANY
and TLC read it. The TLC source declares, for example:

```tla
Custom52…I == INSTANCE RecursionTLC
Custom52…N53…(CustomP1) == Custom52…I!SetSum(CustomP1)
```

Library preparation parses these declarations once and stops if `tlc_module`, an
operator, or a matching arity is missing. `print --spec` shows the TLC source, and
`print --apalache-ir` the JSON Apalache reads. To replay a TLC input outside fuzztla, put
`<corpus>/tla/recursion/` on TLC's class path.

## 3. Writing a pair

A disagreement must mean a defect. Each pair therefore follows these rules:

1. **Same interface.** Both modules define each selected operator with the same arity.
   - The Apalache module passes library validation: first-order, supported syntax, no
     recursion.
   - The TLC module is constant-level.
   - Apalache 0.62.2 generalizes the type of a parameter that only `LET` definitions
     constrain: `F(n) == LET k == n + 1 IN k` gets `(a) => Int`
     ([apalache-typechecker-001](../../findings/apalache-typechecker/apalache-typechecker-001.md)).
     The generator then passes any value to `F`, and Apalache computes arbitrary values
     or crashes in its rewriter. Give such operators an explicit `@type`. `RecursionLibraryIntegrationTest` pins which
     exports are polymorphic.
2. **Equal and total.** Both sides agree on every argument of the Apalache signature.
   Define edge cases explicitly on both sides: empty sets and sequences, and negative
   integers. Never evaluate `Head(<<>>)` or a `CHOOSE` without a witness.
3. **Order-insensitive.** A recursion that picks set elements with `CHOOSE` combines
   them commutatively and associatively. Otherwise the pair reproduces
   [order-sensitive set folds](../../conformance/order-sensitive-set-fold.md).
4. **Bounded cost.**
   - Recurse on generated collections, whose size is bounded
     ([collection size](collection-size.md)).
   - Clamp integer arguments of integer-driven recursion to a small constant on both
     sides.
   - On the Apalache side, avoid `a..b` with nonconstant bounds
     ([conformance](../../conformance/nonconstant-integer-range.md)).
5. **One name per mechanism.** Export each TLC variant under its own name, and in the
   Apalache module define it as an alias of the shared definition:

   ```tla
   \* RecursionTLC
   RECURSIVE SetSum(_)
   SetSum(S) == IF S = {} THEN 0 ELSE LET x == CHOOSE x \in S : TRUE IN x + SetSum(S \ {x})
   SetSumLetRec(S) ==
     LET RECURSIVE Sum(_)
         Sum(T) == IF T = {} THEN 0 ELSE LET x == CHOOSE x \in T : TRUE IN x + Sum(T \ {x})
     IN Sum(S)

   \* RecursionApalache
   SetSum(S) == LET Plus(a, b) == a + b IN ApaFoldSet(Plus, 0, S)
   SetSumLetRec(S) == SetSum(S)
   ```

Before a campaign, add each new operator to the pairwise self-test,
`src/test/resources/recursion/RecursionPairs.tla`. This module instantiates both modules
and asserts `A!Op(x) = T!Op(x)` in `ASSUME` over small domains, and `make test` runs it
under TLC.

## 4. Operators

`libraries/recursion.toml` selects 39 operators from
`libraries/recursion/RecursionTLC.tla` and `RecursionApalache.tla`. A suffix names TLC's
recursion mechanism: none for a top-level `RECURSIVE` operator, `LetRec` for
`LET RECURSIVE`, `Fun` for a recursive function definition, and `HO` for a higher-order
`RECURSIVE` helper. Operators without a suffix may use more than one mechanism, as the
table shows.

| Group | Operators | TLC | Apalache |
| --- | --- | --- | --- |
| Sets | `SetSum`, `SetSumLetRec`, `SetSumFun`, `SetSumHO` | the four mechanisms, over `CHOOSE` | `ApaFoldSet` |
| | `SetCount`, `SetUnionAll`, `SetPowerset`, `SetMaxWith(S, d)`, `SetAllPositive`, `SetMapDouble`, `SetFilterPositive` | `RECURSIVE` over `CHOOSE` | `Cardinality`, `UNION`, `SUBSET`, fold, `\A`, map, filter |
| | `SetToSortedSeq` | `RECURSIVE` on the unique minimum | fold of an insertion |
| | `SetTriangles` | `LET RECURSIVE` inside a set map | closed form inside a set map |
| Sequences | `SeqSum`, `SeqSumLetRec`, `SeqSumFun` | `Head`/`Tail`, tail-recursive accumulator, function on `0..Len(s)` | `ApaFoldSeqLeft` |
| | `SeqReverse`, `SeqToSet`, `SeqFlatten`, `SeqMapDouble`, `SeqMapDoubleHO`, `SeqFilterPositive`, `SeqCountOf(s, x)`, `SeqIsSorted`, `SeqInsertionSort` | `RECURSIVE`; `HO` via `MapSeq(F(_), s)` | `ApaFoldSeqLeft` |
| | `SeqEvenLength` | mutual recursion | `Len(s) % 2 = 0` |
| Functions | `FunSumValues`, `FunIncrementAll` | `LET RECURSIVE` over `DOMAIN`, the latter through `EXCEPT` with `@` | fold, function constructor |
| | `FunTriangles` | `LET RECURSIVE` inside a function constructor | closed form |
| Relations | `TransitiveClosure`, `Reachable(R, x)` | fixed point, frontier search | Warshall fold over the nodes |
| Integers | `IntTriangle` (≤ 30), `IntPow2` (≤ 20), `IntFactorial` (≤ 12), `IntFib` (≤ 12), `IntFibFun` (≤ 12) | `LET RECURSIVE`, `RECURSIVE`, recursive functions, double recursion | closed forms, folds over constant ranges |
| | `IntDigitSum`, `IntGcd(a, b)`, `IntIsEven` | recursion on `\div 10`, Euclid, mutual recursion on `n % 64` | fold over `0..9`, 48 Euclid steps, `n % 2 = 0` |

The bound in parentheses is the clamp. The first argument is clamped to `0..bound`, with
the same clamp on both sides. The Apalache folds over integer ranges apply one step
independently of the element, so their value does not depend on the order in which the
elements are visited.

## 5. Triage

A disagreement on a diff-linked call has one of three causes:

- the pair is not equivalent;
- TLC evaluates the recursion wrongly;
- Apalache evaluates the fold or closed form wrongly.

To tell them apart, add the counterexample's arguments to the pairwise self-test.

- **TLC finds the two definitions equal.** The operator pair agrees under TLC, so look
  at Apalache's evaluation of the fold or closed form, or at the context of the call.
- **TLC finds them unequal.** Either the pair is not equivalent or TLC evaluates the
  recursion wrongly. Derive the expected value from the definitions to decide:
  - If the library is at fault, fix the pair and initialize a new corpus.
  - Otherwise, reduce the TLC side to an MWE.

Findings cite the fuzztla commit, which versions the library, besides the TLC and
Apalache commits.

Two 10-minute campaigns (about 320 aggregated entries each) found no disagreement caused
by a library pair:

- **Disagreements.** Every disagreement matched an existing conformance or findings
  class, for example `CHOOSE` without a witness, `ENABLED`, or fairness.
- **Apalache timeouts.** About 9% of the entries that reached Apalache timed out at the
  default 30 s. The timeouts track the number of library calls per input, not a single
  operator. Consider a larger `timeout_sec` for the Apalache stage of a recursion
  corpus.
