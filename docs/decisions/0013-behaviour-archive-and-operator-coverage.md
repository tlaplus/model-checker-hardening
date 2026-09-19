# 0013: Behaviour archive and operator-edge coverage

**Authors:** Igor Konnov and Claude

**Status:** Proposed

**Date:** 2026-09-20

## Context

[ADR 0010][adr-0010] selects parents by ranking each generation's admissible
entries by TLC's exploration metrics and keeping the best `select_fraction`.
corpus31 and corpus32 ran that loop with one configuration, for 200 generations
of 500 entries each: `select_fraction = 0.25`, `feedback_ratio = 0.5`,
`max_edits = 1` and the default operator weights. We exported both corpora
([ADR 0009][adr-0009]) and measured the loop:

| | corpus31 | corpus32 |
| --- | ---: | ---: |
| Checker-hours: mutants / PBT | 44.7 / 33.1 | 45.6 / 33.6 |
| Untriaged (`NEW`) deviations: mutants / PBT | 8 / 34 | 3 / 31 |
| Agreeing, `projectedDepth ≥ 2`: mutants / PBT | 2,211 / 19 | 1,311 / 14 |
| `04quality-pass` entries / distinct metric vectors | 15,682 / 304 | 15,992 / 256 |
| Selected entries with a new metric vector, generations 25–199 | 1.5% | 1.2% |
| PBT roots of the pool / share of the largest lineage | 144 / 33% | 109 / 75% |

- **Selection keeps behavioural clones.** 78–84% of the agreeing point-edit
  mutants (`random_byte`, `bitflip`, `parity_flip`) reproduce their parent's
  metric vector exactly. The gate keeps a fixed share of every generation, so
  the pool grows by about 80 entries per generation. Almost none of them adds a
  new behaviour.
- **Diversity collapses.** A PBT entry competes in one ranking with the mutants
  of the elite and rarely wins: after generation 25, only 1–12 PBT entries
  enter the pool in each window of 25 generations. In corpus32, the descendants
  of one generation-0 entry make up 75% of the pool.
- **Exploration is not what finds deviations.** Mutants take 57% of the
  checker time and produce 9–11% of the deviations that the triager does not
  match against a known class. Mutation does raise exploration, since mutants
  reach `projectedDepth ≥ 2` about 100 times as often as PBT. But the elite
  lineages keep exercising the same operators.
- **Per-operator coverage saturates.** We replayed the export's `expr` table in
  generation order over the agreeing entries. The features
  `(construct, log2 count)` number about 215, over 68 constructs, and the pool
  already covers 67 of those constructs. After generation 50, only about 12
  entries in 150 generations add such a feature. Pairs of constructs that occur
  in the same entry do not saturate. They give 2,172 features, and 57, 21 and
  11 entries add a new pair in generations 50–99, 100–149 and 150–199. A useful
  signal about operators must therefore relate constructs to each other, as
  edge coverage does in a coverage-guided fuzzer.

## Decision

The gate passes an admissible entry when it shows a behaviour or covers code
that the parent pool lacks. Rank order stays that of ADR 0010: it decides which
entries claim a scarce slot first.

### Behaviour cells

The *cell* of an admissible entry is its TLC verdict (`pass` or
`counterexample`) together with the components of the ADR 0010 key, each after
its bucket:

```
(verdict, projectedDepth, bucket(projectedStates), actionsDiscovering,
 bucket(maxStateNodes), bucket(maxCardinality), maxNesting)
```

`[mutator] cell_capacity` bounds how many pool entries one cell holds. `0`
disables the bound, which is the behaviour of ADR 0010.

### Operator-edge coverage

The *constructs* of an entry are those of the corpus database's `expr` table:
operator applications named by their `oper`, `LET-IN`, and literals named by
the kind of their value. The walk is the one that known-defect signatures
match against ([ADR 0006][adr-0006]): the definitions reachable from `Init`,
`Next`, `Inv`, `Spec`, `Prop` and `Liveness`. An *edge* is a construct together
with a construct that is its immediate argument, or the body of a `LET-IN`,
once labels are removed. A name is not a construct, so a definition's body
starts a new tree and has no parent edge.

The coverage features of an entry are:

- `(construct, bucket(n))` for each construct that occurs `n` times;
- `(parent → child, bucket(n))` for each edge that occurs `n` times.

`bucket` is AFL's hit-count bucket: 1, 2, 3, 4–7, 8–15, 16–31, 32–127 and ≥ 128.
The per-construct features cost nothing extra and cover a construct that
appears only at the root of a definition.

`[mutator] feature_coverage` enables the rule. The features derive from the input, like
`expr`, so the envelope does not change: the gate replays each input it needs
through the generator, at about 1.2 ms per entry.

### The gate

For generation g, let the pool be the `04quality-pass` entries of earlier
generations. The gate walks g's admissible entries in rank order, passes
included, and passes an entry when fewer than `ceil(select_fraction × |input|)`
entries of g have passed and either of these holds:

1. its cell holds fewer than `cell_capacity` entries of the pool and of the
   passes of g so far; or
2. `feature_coverage` is enabled and the entry has a feature that no entry of the pool
   or of the passes of g so far has.

The entry then joins the pool's cells and coverage. Every other entry of g fails.
An input that fails to replay has no features. It can still pass by rule 1.

`select_fraction` becomes an upper bound on the passes of one generation, not
their number.

**Idempotence.** The decision on an entry depends only on the pool, the passes
of g that rank above it, and the entry itself. The gate still commits every
pass before any fail. After an interruption, the committed passes are
therefore a prefix of the uninterrupted gate's passes. A rerun that walks the
committed passes and the pending entries together in rank order makes the same
decisions.

**Cost.** Each run reads the whole pool, as ADR 0010's gate already does. With
coverage enabled, it also replays the pool. Under a cell bound the pool stays
small, so the replay costs a few seconds per generation, against minutes of
checking.

### Parents

The mutator still draws parents uniformly from the pool. With at most
`cell_capacity` entries per cell, apart from those admitted for coverage, this
approximates a draw that is uniform over behaviours.

### Configuration

| Key | Default | Meaning |
| --- | ---: | --- |
| `cell_capacity` | 4 | Pool entries per behaviour cell; `0` disables the bound. |
| `feature_coverage` | `true` | Pass an entry that adds an operator-edge coverage feature. |

`cell_capacity = 0` with `feature_feature_coverage = false` reproduces ADR 0010's gate. The
defaults follow from the corpus31 and corpus32 measurements. They are not
calibrated.

### Corpus database

The export adds a table `exprEdge (entryId, parentName, childName,
occurrences)`, so the coverage of a corpus is a query, and raises the schema
version to 6.

### Replay on corpus31

Replaying corpus31's generations through the gate, with `select_fraction = 0.25`, shows how selective each rule is. The
replay is not counterfactual: under a different gate the mutants would have
different parents. With `cell_capacity = 0` and `feature_feature_coverage = false`, it
reproduces the recorded pool exactly.

| `cell_capacity`, `feature_coverage` | pool | cells | PBT entries | kept by coverage |
| --- | ---: | ---: | ---: | ---: |
| 0, `false` (ADR 0010) | 15,682 | 122 | 144 | – |
| 4, `false` | 682 | 223 | 178 | – |
| 4, `true` | 1,914 | 223 | 553 | 1,232 |

With both rules, coverage keeps 689, 272, 160 and 111 entries in generations
0–49, 50–99, 100–149 and 150–199, over 2,540 features. The edge signal does not
saturate.

## Alternatives considered

- **Lower `select_fraction`.** It shrinks the pool but keeps choosing the same
  cells, and it starves PBT entries even more.
- **A PBT quota in the gate.** It keeps roots flowing in, but a new PBT entry is
  valuable only when it adds behaviour or code, and the two rules above admit
  exactly those entries.
- **A cell-first parent draw.** Unnecessary once cells are bounded, since the
  uniform draw is then close to cell-uniform.
- **Per-construct coverage alone.** It saturates by generation 50 (Context).
- **Co-occurrence pairs.** They do not saturate, but they relate constructs that
  never interact. Edges are both finer and closer to what a checker evaluates
  together.
- **Dynamic coverage from TLC's `-coverage`.** It would count what TLC actually
  evaluated rather than what is reachable in the source. It needs a
  checker-side change and adds overhead to every TLC run. Deferred until static
  edge coverage has been measured.
- **Storing features in the envelope.** That would save the replay, but it
  stores derived data that changes with the walk. The export already replays
  inputs for the same reason.

## Consequences

- The pool stops growing linearly with the number of generations. The
  cell-bound pool of a 200-generation run holds hundreds of entries, not 16,000.
- A PBT entry that opens a cell or covers a new edge enters the pool
  regardless of rank, so new lineages keep arriving.
- The gate replays inputs, so it depends on the generator configuration as
  `export-db` does. A corpus whose operator library changed cannot compute
  coverage.
- `exprEdge` makes coverage-per-generation and coverage-per-operator queries
  possible. Calibrating `cell_capacity` and evaluating coverage need an A/B run:
  a baseline (`cell_capacity = 0`, `feature_feature_coverage = false`), cells alone, and cells
  with coverage. The measure is untriaged deviations per checker-hour.
- This revises the quality gate of ADR 0010. The mutator, its operators, and the
  ranking key are unchanged.

[adr-0006]: 0006-known-defect-signatures.md
[adr-0009]: 0009-corpus-database.md
[adr-0010]: 0010-mutation.md
