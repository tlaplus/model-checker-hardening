# 0010: Generational mutation

**Authors:** Igor Konnov and Claude

**Status:** Implemented; generation scheduling revised 2026-09-21

**Date:** 2026-09-16

## Context

The pipeline of [fuzzing-workflows.md §1.1][workflows] reserves a quality gate
(`04quality-pass`, `04quality-fail`) and a mutator that feeds `00-inputs`. Both
are unimplemented, and §1.3 and §1.4 say "Good quality gates
are to be found". [ADR 0008][adr-0008] supplies the exploration metrics a gate
needs but proposes no gate. This ADR proposes the gate, the mutator and the loop
that connects them.

Random generation rarely produces a module whose non-step variables change. In
corpus24 (103,537 entries), 34,890 entries agree in `03aggregator-pass`, but only
164 of them have TLC `projectedStates ≥ 2` and a verdict other than fail/fail.
Roughly 1% of the agreeing entries exercise a transition relation.

### Experiment

We mutated those 164 entries (T) and, as a control, 164 entries drawn uniformly
from the remaining agreeing non-fail entries of `03aggregator-pass` (C). Each
parent received 64 mutants, each mutant 1–4 stacked byte edits from eight
operators. The 20,878 mutants were injected into two fresh corpora and processed
by the ordinary workflow with corpus24's generator configuration and its 30 s
checker timeouts. The known-defect filter was disabled. The generator rejected no
mutant, and every mutant parsed. Timeouts were negligible (5 and 4 stage runs at
the limit). Tiers below are those of the metrics of
ADR 0008: **A** is `projectedDepth = 5`, **B** is `projectedStates ≥ 2`, and
neither counts an entry that fails or disagrees.

| | mutants | agree, non-fail | disagree | A | B | new A+B per checker-hour |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| corpus24, PBT only | 101,028 | 16.3% | 61.9% | 49 | 115 | 1.2 |
| T, elite parents | 10,486 | 35.3% | 47.7% | 517 | 1,388 | 130 / 9.4 |
| C, control parents | 10,392 | 40.0% | 44.0% | 1 | 10 | 1.5 |

Mutating selected parents yields A+B entries at roughly 100 times the PBT rate
when children are counted by distinct decoded module (884 of T's 1,905 A+B
children differ from their parent), and roughly 8 times the PBT rate when they
are counted by distinct exploration metrics (64 children). Mutating unselected
parents performs like PBT. Four further measurements drive the decision:

- **Clones dominate.** Half of T's agreeing children (49.7%) reproduce their
  parent's metric vector exactly, and 9.7% of all mutants decode to their
  parent's module verbatim, the edit having landed in bytes the decoder never
  reads.
- **Small edits survive.** One edit per mutant leaves 55.1% of T's children
  agreeing and non-failing, 41.6% in A or B; four edits leave 22.8% and 4.1%.
- **Operators differ sharply.** Per-operator share of T's mutants reaching A or
  B: `random_byte` 24.1%, `bitflip` 23.3%, `interesting` 23.0%, `copy` 15.4%,
  `erase` 3.5%, `insert` 2.9%, `duplicate` 2.1%, `splice` 1.3%. Among the
  children that survive, splice is the least clone-prone: 5.0% reproduce the
  parent's metric vector, against 57.4% for `random_byte`.
- **Improvement beyond the parent is rare but real.** 0.7% of T's agreeing
  children strictly dominate their parent's metric vector, 27 metric vectors
  occur that corpus24 never produced, and child 68b9062eb335 (parent
  f8d0eea82ac8) reaches `projectedStates = 9`, above corpus24's maximum of 6,
  by changing `var0' \in { step, var0 }` into `var0' \in { step, (-step) }`.
- **Mutation does not raise the disagreement rate.** T disagrees less often than
  PBT (47.7% against 61.9%), mostly TLC `spec_eval` failure against an Apalache
  counterexample or pass. Those disagreements are untriaged.

## Decision

### Generations

A run proceeds in generations. Generation 0 is PBT as it exists today. A
generation settles when every entry it admitted has a terminal stage outcome. The quality
gate then selects from that generation, the mutator derives inputs from the
selection, and the inputs stage admits the next generation from two sources: the
mutator's output and fresh PBT candidates. A corpus entry records its generation,
so a generation is a property of the entry, not of the run that produced it.

The workflow runs one stage graph per invocation. Once generation g fills its
quota, the input stage admits the PBT suffix of g+1 while the checker tail of g
remains active. The quality gate runs when every entry of g has a terminal
outcome: parser failure or crash, aggregation, or completed checker branches
with a crash. Only then may the input stage admit g+1's mutant prefix. Stage
queues and CPU-budget requests prioritize older generations. At most one
generation is admitted ahead of the oldest ungated generation. A checker crash remains in its checker
directory, as before.

Startup recovery reconstructs the oldest incomplete generation and unfinished
entries, including an already admitted PBT suffix of the next generation. The
coordinator reserves target ranges before submitting them, and durable stage
transitions signal completion. It does not inventory the whole corpus at each
generation boundary. The gate is idempotent on restart (below). The workflow
requires `gen.generation` on every entry of a stage directory; an entry without it, such as one written before this
ADR, stops the run at startup. The envelope codec still reads such entries, so
`fuzztla print` and `fuzztla export-db` keep working on older corpora.

### Quality gate: `04quality-pass`, `04quality-fail`

The gate consumes `03aggregator-pass` and is the selection step. Its input for
generation g is every entry of generation g that reached `03aggregator-pass`,
including entries an interrupted gate already moved on. An entry is
*admissible* when its TLC verdict is not `fail`, TLC recorded exploration
metrics for it, and it matches none of the enabled shallow patterns of [ADR 0008][adr-0008] (vacuous pass, initial-state
violation, early failure, no discovering action, counter-only progress). The
gate ranks the admissible entries by the key

```
(projectedDepth, bucket(projectedStates), actionsDiscovering,
 bucket(maxStateNodes), bucket(maxCardinality), maxNesting)
```

compared lexicographically, higher first, ties broken by smaller `inputBytes`
and then by digest, so the order is total. It keeps the best
`k = ceil(select_fraction × |input|)` admissible entries in `04quality-pass`.
The remainder, admissible or not, moves to `04quality-fail`. A count that TLC
did not measure ranks as 0.

`quality` is a `CorpusStage` like the aggregator: it has no input directory of
its own, no configuration table, and is bounded only by `workflow.max_entries`.
It records `stages.quality` with a `pass` or `fail` verdict and the usual
timestamps; neither the key nor the matched pattern is stored, because both
derive from stored metrics. Each move is an ordinary stage transition, so
startup recovery finishes a move whose metadata was committed.

**Idempotence.** The gate commits every pass in rank order before any fail.
After an interruption, the entries left in `03aggregator-pass` are exactly the
lower-ranked tail, so a rerun that keeps `k − |passed|` more entries reaches the
placement the uninterrupted gate would have reached.

[ADR 0008 §"TLC metrics"][adr-0008] defines every field and how TLC's observers
compute it. In short: `projectedDepth` and `projectedStates` count state change
after removing the generated step counter, so they measure the spec's own
variables rather than the counter; `actionsDiscovering` counts the disjuncts of
`Next`, by source location, that produced a new state; and `maxStateNodes`,
`maxCardinality` and `maxNesting` describe the size, width and nesting of the
largest state value.

The three unbounded counts are ranked by logarithmic bucket,
`bucket(v) = floor(log2(v + 1))`. A bucket keeps a difference between 3 and 6
projected states, which share bucket 2, from outranking a real difference in the
next key, while an order-of-magnitude
difference still dominates. `projectedDepth` and `actionsDiscovering` are bounded
by `generator.max_steps` and the disjunct count, and `maxNesting` by
`generator.max_type_depth`, so they are compared directly.

The key is lexicographic rather than a weighted score: the metrics have no common
unit, and the experiment ranks depth of real state change above every other
signal. Ranking within a generation, rather than against an all-time elite, keeps
selection pressure independent of corpus age; the mutator's parent pool is the
union of `04quality-pass` over all generations, so good parents are not
discarded. `select_fraction` and the shallow-pattern list are configuration, not
constants, and need calibration against a full corpus before generation 1 of any
long run.

### Mutator

The mutator draws parents from `04quality-pass`, applies byte edits and writes
the results into `00-inputs`. It is a byte-level mutator: the generator is a
deterministic decoder of arbitrary bytes ([ir-generators §1][ir-generators]), so
every byte array is a valid input, and no mutant needs repair.

The mutator is not a stage. Like PBT, it produces corpus entries rather than
recording verdicts on them, which is why [ADR 0004][adr-0004] gives the input
stage no `CorpusStage`. It is a second candidate source of the input stage: the
stage's workers claim target entries as before, and a target is filled either
from PBT or from mutation. There is no `05mutator-pass` directory and no
mutator occupancy limit; `workflow.inputs.max_entries` bounds both sources. The
parent pool is the `04quality-pass` entries of the kind the run generates
(`generator.kind`), read once per generation.

The operators live in the leaf package `io.github.tlaplus.hardening.mutation`:
the corpus stores their names, the configuration weights them, and the workflow
applies them. Each `MutationOperator` constant carries its name, its default
weight and its edit.

**Operators.** Each operates on the parent's byte array. Weights follow the
experiment: point edits high, block edits that shift later sections low.

| Operator | What it does | Default weight |
| --- | --- | ---: |
| `random_byte` | Replaces one byte at a uniformly chosen offset with a uniform byte. | 8 |
| `bitflip` | Flips one bit at a uniformly chosen offset. | 8 |
| `parity_flip` | Flips the low bit of one byte, toggling a continuation marker of the decoder's variable-length encoding: the collection it introduces gains or loses an element. | 8 |
| `copy` | Copies a block of up to 32 bytes from one offset of the input over another, keeping the length. | 4 |
| `duplicate` | Reinserts a block of up to 32 bytes directly after itself, growing the input. | 1 |
| `insert` | Inserts up to 8 uniform bytes at an offset. | 1 |
| `erase` | Deletes up to 16 bytes at an offset. | 1 |
| `splice` | Concatenates a prefix of the parent with a suffix of a second parent drawn from `04quality-pass`, cut at independent offsets. | 1 |

An operator applied to an empty array returns it unchanged; the result is then
rejected as a clone.

A mutant's length is capped at `pbt.max_input_bytes`. `copy` preserves the byte
offsets of every later section and so preserves their decoding; `duplicate`,
`insert`, `erase` and `splice` shift them, which is why their survival rate is
between a third and a tenth of the point edits'.

There is deliberately no operator for byte values that are boundary cases in
programming, such as `0x7F` or `0x80`. Every structural choice is decoded by
`Draw.drawIndex` or `Draw.drawLong`, which reduce the bytes they read modulo a
count that differs per call site, so only a byte's residue carries meaning and
such a value is an ordinary random byte. The experiment's `interesting` operator,
which drew one of seven boundary values or flipped the parity, matched plain
`random_byte` (23.0% against 24.1% reaching A or B). What its measurement does
support is the parity flip, kept above as `parity_flip`, because `Draw.drawBoolean`
reads exactly the low bit.

- **Edit count.** A mutant receives `1 + G` stacked edits, where `G` is
  geometric with parameter ½, capped at `max_edits`. The default `max_edits = 1`
  gives one edit per mutant. The result is truncated to `pbt.max_input_bytes`.
- **Clones.** A candidate is a clone when its rendered TLA<sup>+</sup> module
  equals its parent's. The parent's rendering is computed once per generation and
  cached.
- **Metadata.** A mutant keeps its parent's `cohort` and stores its own measured
  `richness`; its admission threshold is 0.
- **Admission is the PBT admission.** A mutant enters `00-inputs` through the
  same path as a PBT candidate: a generator rejection discards it, a byte array
  that duplicates an existing entry discards it, and a match against the
  known-defect signatures of [ADR 0006][adr-0006] quarantines it in
  `00-known-defects`. The mutator adds one rule of its own, clone rejection: a
  candidate whose decoded module equals its parent's is discarded, which the
  experiment shows to be 9.7% of mutants. The richness cohort schedule of
  [ADR 0002][adr-0002] stays PBT-only; it exists to spread randomly generated
  inputs over collection sizes, and a mutant inherits its parent's richness.
- **Fill to target.** The mutator draws parent, operators and offsets until the
  generation's mutant quota is met, retrying after every rejection, as the input
  stage retries a rejected PBT candidate. Mutating and rejecting are cheap
  against checking: the experiment's 20,878 candidates decoded in 22 s of wall
  time, against 14.3 checker-hours of TLC and Apalache. A quota that cannot be
  filled within a bounded number of attempts stops the workflow with a
  diagnostic naming the parent pool size and the rejection counts, as an
  unfillable richness cohort does.
- **Provenance.** Every admitted entry records `gen.generation`. A mutated
  entry also records `gen.parent`, its parent's digest, and `gen.operators`, the
  operators applied in order; the two appear together or not at all. ADR 0009's
  export stores `generation` and `parent` as `entry` columns and the operators
  as `mutationOperator` rows, so the yield of a generation and of each operator
  is a query. The run statistics count clone rejections as
  `generator.clones`.

### Feedback ratio

`[mutator] feedback_ratio` fixes the share of each generation admitted from the
mutator; the rest comes from PBT, under the unchanged richness-cohort policy of
[ADR 0002][adr-0002]. The ratio is configuration because the right value depends
on the corpus: mutation exploits the current elite, PBT supplies the diversity
that mutation cannot invent. A generation whose selection is empty is filled from
PBT alone, which makes generation 0 a special case of the same rule.

A generation of `size` entries holds `round(feedback_ratio × size)` mutants when
the parent pool is nonempty. Admission counts what the generation already holds:
a run admits `min(generation_size − admitted, max_entries − total)` more entries,
and of those only the mutants the generation still lacks, so a resumed
generation keeps the configured split. The corpus inventory counts entries and
mutants per generation for this purpose.

Configuration lives in a top-level `[mutator]` table, like `[pbt]`:

| Key | Default | Meaning |
| --- | ---: | --- |
| `generation_size` | 1000 | Entries admitted per generation. |
| `select_fraction` | 0.05 | Share of the gate input kept in `04quality-pass`. |
| `feedback_ratio` | 0.5 | Share of a generation admitted from the mutator. |
| `max_edits` | 1 | Cap on stacked edits per mutant. |
| `weights` | table above | Operator weights; their sum must be positive. |
| `shallow_patterns` | all five | ADR 0008 patterns that make an entry inadmissible. |

Neither the gate nor the mutator has a `[workflow.*]` table.

### Determinism

Each generation derives its seed from the run seed and the generation number,
and each target entry derives its seed from the generation seed and its
ordinal. A target draws its claim (for PBT, the richness cohort) and its
candidates from its own stream, so what it admits does not depend on which
worker claims it. A run therefore replays given the same seed, configuration
and starting corpus, whatever `--max-cpus`, with one exception: when two targets
draw identical bytes, scheduling decides which stores them.

This revises [ADR 0002][adr-0002], which seeded each worker. With one candidate
source per run, worker streams replayed in practice. With two sources the
ordinal decides the source, so the number of targets a worker fills from each
source varied with scheduling: two runs with the same seed and `--max-cpus`
selected the same generation-0 parents but shared only 9 of 40 PBT entries of
generation 1. Mutant targets occupy the prefix and PBT targets the suffix of
each generation's ordinal range. On restart, admitted entries reserve their
source's portion of that range. An older corpus does not store target ordinals,
so its exact target streams cannot always be reconstructed after interruption;
already admitted entries remain unchanged.

## Alternatives considered

- **Mutate every agreeing entry, without a gate.** The control group settles
  this: parents that explore nothing produce children that explore nothing, at
  PBT's yield and at PBT's cost.
- **An all-time elite ranked against every generation.** Concentrates the parent
  pool on the entries of the first lucky generation and needs an ageing rule to
  stay diverse. Per-generation ranking with a cumulative parent pool achieves the
  same without one.
- **Coverage-guided selection, as libFuzzer does.** The checkers run as separate
  JVM processes and are not instrumented; exploration metrics are the available
  feedback signal.
- **Type-directed literal values, guided by a draw trace.** Byte values do carry
  meaning in one place: `IntegerExprGenFactory.integerLiteral` reads a
  two's-complement payload through `BasicGenerators.byteArray`, and strings draw
  their bytes the same way, so `0x00`, `0xFF` and `0x80` are 0, −1 and the
  minimum there. A mutator cannot tell those bytes from choice bytes by looking
  at the array. It could if `Draw` recorded the span each draw consumed and the
  operation that consumed it: the mutator would then pick a span and edit it in
  its own terms, and splice at a subexpression boundary rather than a random
  offset. This is untested, and the experiment cannot separate the parity flip
  from the boundary values because it logged only the operator name. Deferred,
  with the draw trace as its prerequisite.
- **IR-level mutation, editing the decoded module.** Would bypass the byte
  encoding and its clone problem, but needs a second generator that maintains
  the type-safety and closedness invariants `IrGenerators` already guarantees.
  Deferred, not rejected.
- **Drop splice.** Its survival rate is the lowest of the eight operators. It is
  kept at low weight because it is the only recombining operator and its
  survivors differ most from their parents.
- **The mutator as a stage with `05mutator-pass`.** A directory between the
  mutator and `00-inputs` would need its own recovery, inventory and occupancy
  rules, and it would hold entries that no stage has judged. Admission already
  gives the mutator everything the directory would.
- **One graph per generation.** The initial implementation used this simpler
  boundary. It left CPUs idle during long checker tails and required a complete
  corpus scan before every next generation. The 2026-09-21 revision replaced it
  with one invocation-long graph and bounded PBT lookahead.
- **Weighted numeric score in the gate.** Needs units and calibration for six
  metrics that share none.

## Consequences

- **Implementation touches five areas.** The `quality` stage with its
  `CorpusStage` constant and directories; a mutation candidate source in the
  input stage and the `mutation` package; envelope fields for provenance; the
  `[mutator]` config table; and ADR 0009 columns (schema version 5).
  Implementing this ADR revises [fuzzing-workflows.md][workflows], which is
  updated in the same change.
- **Existing corpora.** The new directories are required, and the workflow
  requires `gen.generation`. A corpus written before this ADR can still be
  printed and exported after creating `04quality-pass` and `04quality-fail`, but
  it cannot continue a run. Per repository policy, nothing migrates it.
- **Agreeing entries move.** Once gated, an agreeing entry lives in
  `04quality-*` instead of `03aggregator-pass`. Queries select agreement by the
  aggregator's stage verdict rather than by directory.
- **Clones are the main risk to corpus value.** Without the module-identity check
  a generation fills with entries that differ in bytes and agree in behavior;
  the occupancy limits would then be spent on duplicates.
- **Triage load grows.** Nearly half of all mutants disagree between the
  checkers. The triager's signatures decide how much of that is already known,
  and the known-defect filter stays enabled in production runs, unlike in the
  experiment.
- **The gate rewards aliases of the step counter.** In corpus28 (100
  generations, `select_fraction = 0.25`), every `04quality-pass` entry from
  generation 10 on descends from one PBT root whose `Next` assigns `step` into a
  record field; 13,725 of 13,753 selected entries share `projectedDepth = 5`,
  `projectedStates = 5` and `actionsDiscovering = 1`. `projectedStates` removes
  the `step` variable but not copies of it. The generator therefore no longer
  offers `step` to action operators and `Next`
  ([ir-generators §9.1][ir-generators]). Diversity of the parent pool remains
  open: only 4 PBT entries were ever selected.
- **The gate's thresholds are unvalidated.** `select_fraction`,
  `feedback_ratio`, the weights and `max_edits` have defaults from one
  experiment on one corpus, at one generation. Calibration over several
  generations is future work, and the mutator's provenance columns exist to make
  it a query.

[workflows]: ../architecture/fuzzing-workflows.md
[ir-generators]: ../architecture/ir-generators.md
[adr-0002]: 0002-pbt-richness-score.md
[adr-0004]: 0004-stage-identity.md
[adr-0006]: 0006-known-defect-signatures.md
[adr-0008]: 0008-exploration-metrics.md
