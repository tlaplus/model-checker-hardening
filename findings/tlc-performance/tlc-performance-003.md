---
state: open
labels: [tlc]
---

# Building the liveness tableau deduplicates particles by linear search and takes minutes for a property of 13 temporal operators

## Summary

Before TLC explores a state, it builds the particle tableau of the negated
temporal property (`TBGraph`). It removes duplicate particles by linear search
in two places:

- `TBGraph.findOrCreateNode` compares every successor particle with every node
  built so far.
- `TBParVec.union`, which `TBPar.particleClosure` calls at every branch,
  compares every particle of one list with every particle of the other.

Each comparison is set equality by mutual containment (`TBPar.equals`), a
quadratic loop over the structural equality of formulas. The cost therefore
grows with the number of tableau edges times the number of nodes, and not with
the size of the tableau alone.

For the property `Prop` below, which has 13 temporal operators, the tableau has
3,400 nodes and 228,448 edges. TLC needs 203 s (`7e64fd0`) and 206 s
(`bc1b506`) to build it, and then checks the property against the 6 states of
the specification in under a second. Replacing the two linear searches with
hash lookups builds a byte-identical tableau in 8.3 s.

FuzzTLA's campaigns generate and rewrite properties of this size, and their TLC
runs end at the time limit without a verdict. In corpus88, 229 of the 230 TLC
timeouts were building the tableau. Rerun with the two lookups hashed, 223 of
the 229 finish within 300 s, and 182 within the corpus's limit of 30 s.

Observed with TLC `7e64fd0` (2026-02-23) and `bc1b506` (tla2tools
`1.8.0-20261119.181232-127`), FuzzTLA `fa5b6c03`, and Apalache 0.63.0
(`67b8ec69`, not involved), on OpenJDK 25.0.5.1 for Linux aarch64.
`TBGraph`, `TBPar` and `TBParVec` are the same in both TLC versions. `TBGraph`
and `TBPar` last changed in tlaplus `1b8546882` (2021-04-20), and `TBParVec`
earlier.

## Reproduction

`M.tla`:

```tla
---- MODULE M ----
EXTENDS Integers
VARIABLE x
Init == x = 0
Next == (x < 5 /\ x' = x + 1) \/ UNCHANGED x
Spec == Init /\ [][Next]_x
P == x >= 0
G == []P \/ (P /\ <>~P)
NotG == <>~P /\ (~P \/ []P)
R == []G \/ [][]G \/ (G /\ <>NotG)
Prop == <>R
====
```

`M.cfg`:

```text
SPECIFICATION Spec
PROPERTY Prop
```

```sh
java -Xmx2g -cp tla2tools.jar tlc2.TLC -workers 1 -deadlock -noGenerateSpecTE \
  -config M.cfg M
```

`P` is an invariant, so `G` and `R` hold, and so does `Prop`. TLC reports no
error after building the tableau; the run's log shows
`Finished checking temporal properties in 00s`. The property is written in
positive form: no temporal subformula is negated, so the normalization of
upstream commit `4260e4712` (which fixed [tlc-014](../TLC/tlc-014.md)) does not
change it, and both versions build the same tableau.

Each time is one run, one after another on an otherwise idle machine, with one
worker and a 2 GB heap, from the start of the JVM to its exit. The tableau is
exported with `-Dtlc2.tool.liveness.Liveness.tableauExportPath=M.dot`.

| `PROPERTY` | Temporal operators | Tableau nodes | Tableau edges | `7e64fd0` | `bc1b506` | `bc1b506`, `findOrCreateNode` hashed | both lookups hashed |
|---|---:|---:|---:|---:|---:|---:|---:|
| `R` | 12 | 680 | 4,939 | 0.6 s | 0.6 s | | |
| `Prop == <>R` | 13 | 3,400 | 228,448 | 203.0 s | 206.0 s | 23.4 s | 8.3 s (`7e64fd0`: 8.2 s) |

The last two columns put patched copies of `TBGraph` and `TBParVec` before
`tla2tools.jar` on the class path. The patches index the particles by a hash
that is consistent with `TBPar.equals`: the sum of the distinct structural
hashes of the member formulas, with `LNState` and `LNAction` hashed by their
tags. A lookup then compares only the particles in one bucket. For `Prop`, the
seven exported tableaux, patched or not and of either version, are
byte-identical.

## Cause

From `tlatools/org.lamport.tlatools/src/tlc2/tool/liveness` at `bc1b506`:

1. `TBGraph.<init>` (`TBGraph.java:59-68`) computes, for every node, the
   particle closure of its implied successors and looks up each resulting
   particle with `findOrCreateNode`.
2. `findOrCreateNode` (`TBGraph.java:80-90`) scans all nodes created so far and
   compares with `TBPar.equals`. For `Prop` this is 228,448 lookups in a list
   that grows to 3,400 nodes.
3. `TBPar.equals` (`TBPar.java:46-48`) is `this.contains(par) &&
   par.contains(this)`. `contains` calls `member` for each formula, and `member`
   scans the other particle with `LiveExprNode.equals`, which recurses over
   the whole formula.
4. `TBPar.particleClosureBeta` (`TBPar.java:192-196`) splits on every
   disjunction and eventuality and merges the two halves with
   `TBParVec.union` (`TBParVec.java:31-42`). That method calls
   `ps.contains(this.parAt(i))` for each particle, again a linear scan with
   `TBPar.equals`.

Thread dumps taken every 5 s during the unpatched `bc1b506` run show the
`main` thread in `findOrCreateNode` in 37 of 40 samples, below `TBPar.member`
and the `equals` methods of `LNConj`, `LNDisj`, `LNAll` and `LNEven`, and in
`TBParVec.union` in the other 3. With only `findOrCreateNode` hashed,
`TBParVec.union` takes 3 of the 4 samples.

The size of the tableau is not the problem: once the lookups are hashed, its
3,400 nodes and 228,448 edges are built in seconds. The remaining 8 s were not
profiled. `particleClosure` computes the closure of every node's successors
afresh, branching on each disjunction and eventuality, and 6 of the corpus runs
below still exceed 300 s with both lookups hashed.

## Corpus evidence

corpus88 is an `mt` corpus with `temporal_rules` and the checkers TLC
`7e64fd0` (one worker) and Apalache 0.63.0. Its TLC stage has a 30-second limit
per run. The triager labels 230 TLC crashes `TIMEOUT`. Since
[ADR 0021](../../docs/decisions/0021-timeout-diagnostics.md), a timeout stores
the stacks of TLC's threads:

| Frame of the `main` thread | Entries |
|---|---:|
| `TBGraph.findOrCreateNode`, below `TBGraph.<init>` | 185 |
| `TBPar.particleClosure`, below `TBGraph.<init>` | 44 |
| no thread stacks stored (`1a07c012`) | 1 |

All 229 are property runs (ADR 0027 §4): 119 check `SpecC |= PropC` and 110
check `Spec |= Prop`. TLC has not explored a state in any of them.

Each of the 229 runs was repeated from the command line on `7e64fd0`, with the
two patched classes, one worker, `-Xmx512m` and a 300 s limit:
- 223 finish: 182 within 30 s and 41 between 30 and 300 s, with a median of
  5.5 s;
- 108 pass, 111 report a violation, and 4 report an evaluation error;
- 6 still exceed 300 s.

corpus87, a `pbt` corpus without rewrites and with the same checkers, has 3 TLC
timeouts. Two of them, `0609942b` and `796fae54`, are in `TBGraph.<init>`, so
generated properties reach this as well. The triager labels such timeouts
`tlc-performance-003` by their `TBGraph.<init>` frame.

On `bc1b506`, which normalizes negated temporal subformulas before building
the tableau, 38 more property runs of corpus88 exceed 60 s. 15 of them do not
finish within 600 s; the 23 that finish take 57 to 585 s.

[ADR 0030](../../docs/decisions/0030-temporal-operator-budget.md) reports the
same place for 995 of the 996 TLC timeouts of corpus80, and bounds the rewrite
of a property to 16 temporal operators. That bound is a workaround in FuzzTLA,
not a fix: `Prop` has 13, and the corpus88 timeouts are within it.

## Expected behavior

The time to build the tableau is close to linear in the number of its nodes
and edges times the particle size. A lookup of a particle among the existing
nodes, and the union of two particle lists, use a hash of the particle that is
consistent with `TBPar.equals`, for example the one described above.

## Impact

A property with a dozen temporal operators can keep TLC busy for minutes before
it explores the first state, while the tableau has only a few thousand nodes.
The time does not depend on the specification, so the delay is the same for a
specification of six states. Users who conjoin a few liveness properties, or
who write them with nested `[]` and `<>`, wait without output: TLC prints
`Implied-temporal checking--satisfiability problem has 1 branches.` and then
nothing until it computes the initial states.
Automated workflows that limit TLC's run time record a timeout instead of a
verdict.
