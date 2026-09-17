# 0011: Collection base size

**Authors:** Igor Konnov and Claude

**Status:** Accepted

**Date:** 2026-09-17

## Context

Generated specifications rarely hold a non-empty collection in a state. This ADR
proposes a configured *base size* for value collections: exhausted input decodes a
collection of the base size, and input bytes move the size below or above it. It
revises the collection encoding of [ir-generators.md §4][byte-decoding] and the
closed terminals of [§6][termination].

### Measurements

corpus28 (module kind, 100,000 entries, fuzztla `f167084`, before
[ADR 0010][adr-0010]'s step-scope fix) contains 50,489 PBT entries with a TLC run. By richness
cohort ([ADR 0002][adr-0002]):

| Cohort | Mean syntactic richness | TLC fails in `Init` | ≥ 1 initial state | `maxCardinality ≥ 1` | `maxCardinality ≥ 3` |
| ---: | ---: | ---: | ---: | ---: | ---: |
| 0 | 487 | 57.4% | 41.0% | 7.3% | 0.7% |
| 5 | 949 | 78.3% | 24.8% | 10.9% | 1.8% |
| 9 | 1,253 | 80.8% | 23.7% | 11.3% | 2.6% |

The generator does not lack collection literals: every cohort scores in the
hundreds. The literals do not survive evaluation. Richer cohorts fail in `Init`
more often and reach larger states only marginally more often. The most frequent TLC failure
details of PBT entries are function application outside the domain (7,666),
index 0 of a tuple (4,925), `Head` of an empty sequence (4,824) and a `CASE`
without a true arm (4,299).

In 200 modules of a post-fix 1,000-entry run, the rendered TLA<sup>+</sup>
contains 5,164 empty sets `{}`, of which 1,950 are empty function domains
`[x \in {} |-> …]`, and 3,798 empty sequences or tuples `<<>>`, against roughly
1,200 non-empty set forms and 2,400 non-empty sequence and tuple literals. The ten
highest-ranked agreeing corpus28 modules that do not copy `step` all assign a
constant such as `{}`, `<<>>` or `""` in `Next`.

### Causes

Two decoder rules make collections empty or small:

1. **Closed terminals are empty** ([ir-generators §6][termination]). A starved or
   exhausted request of a set, sequence or function type yields `{}`, `<<>>` or
   `[x \in {} |-> t]`, and `Int` yields `0`. Terminals are the most common leaf,
   and every partial operator applied to one fails: `Head`, `f[x]`,
   `CHOOSE x \in {}`, sequence index `0`. Folds, filters and maps over a terminal
   yield empty results that propagate into the state.
2. **Continuation markers make random sizes geometric**
   ([ir-generators §4][byte-decoding]). `BasicGenerators.listOf` reads one
   Boolean before each optional element. A random byte continues with probability
   ½, so optional elements number 0, 1, 2, … with probability ½, ¼, ⅛, …, and
   exhausted input stops at the minimum.

### Prior art

The IR decoder follows Hypothesis's choice-sequence design. Hypothesis 6.155
(`hypothesis/internal/conjecture/utils.py`, `class many`) also sizes collections
by continuation Booleans, but draws each with a probability `p_continue` computed
by `_calc_p_continue` from a target `average_size`, by default
`min(max(2·min_size, min_size + 5), (min_size + max_size) / 2)`; for a maximum of
8 that is 4. Our markers read the low bit of a byte, which fixes the probability
at ½ and the mean of the optional elements at about 1. The decoder kept Hypothesis's
mechanism without its size calibration.

Hypothesis orders every choice by a complexity index whose 0 is the simplest
value, and shrinks towards it; for a collection that is the empty one. Its
integers encode that index as a zigzag around a `shrink_towards` value
(`hypothesis/internal/conjecture/choice.py`, `zigzag_index`): indices 0, 1, 2,
3, 4 denote `t, t+1, t−1, t+2, t−2`. The zigzag orders values by distance for the
shrinker; adjacent indices can still be far apart (index 4 is `t−2`, index 5 is
`t+3`). Hypothesis draws from an inexhaustible random
source, so its simplest values matter for shrinking, not for the distribution of
generated data. Our decoder falls back to simplest values whenever the input or a
budget runs out, so there they dominate the corpus.

QuickCheck's `sized` passes an explicit size parameter to generators. Neither
library centres collection sizes on a base that exhausted input decodes to. That
part of this proposal has no direct precedent.

## Decision

### Base-size draw

`BasicGenerators` gains a size draw with a base `b`, a spread `s`, and bounds
`[min, max]`:

```
index  = Draw.drawIndex(2·s + 1, 1)
offset = ((index + s) mod (2·s + 1)) − s
size   = clamp(b + offset, min, max)
```

- Exhausted input reads 0 and yields `clamp(b)`.
- A random byte yields a size approximately uniform in `[b − s, b + s]` before
  clamping. A signed byte offset would instead pile half of the mass on the
  bounds after clamping.
- Flipping the low bit of the size byte changes `index` by one and so the size
  by one, except where the offset wraps from `+s` to `−s`. `parity_flip` and
  `bitflip` on a low bit ([ADR 0010][adr-0010]) therefore nudge a size instead of
  truncating a collection. Hypothesis's zigzag would give the same exhaustion
  default, but a low-bit flip there moves between `−k` and `+(k+1)`, so this ADR
  rotates the index instead.
- `2·s + 1 ≤ 256`, so the draw is one byte.

The size is read before the elements. This deliberately revises the rule of
[§4][byte-decoding] that avoids a dedicated size byte "whose mutation could add or
remove many elements at once": the spread bounds that change to `2·s`, and the
rotated offset keeps low-bit edits at ±1. Changing a size still reframes the bytes of every
later element, as toggling a continuation marker does today.

### Where it applies

The draw replaces continuation markers only where the count is the number of
elements of a *value*:

| Site | Today | Minimum |
| --- | --- | ---: |
| Set enumeration `{e1, …, en}` (`SetExprGenFactory`) | `operands`, 1 + markers | 1 |
| Sequence literal `<<e1, …, en>>` (`SequenceExprGenFactory`) | `operands`, 1 + markers | 1 |
| Closed terminal of a set, sequence or function type | empty | 0 |

Structural lists keep continuation markers: conjunction and disjunction operands,
`CASE` arms, `LET` definitions, `EXCEPT` updates, set-map sources, action guards,
`Next` disjuncts, operator lists, and the fields of generated tuple and record
types. A base size there enlarges the module rather than its data, and multiplies
the node budget spent per construct. `BasicGenerators.byteArray` (string and
integer payloads) is unchanged.

### Base-size terminals

A closed terminal of a collection type becomes a byte-free literal of base size.
Terminals stay byte-free for the reason given in [§6][termination]: they are the
exhaustion fallback, where every read decodes as zero.

A terminal needs distinct elements, or a set collapses. Define the byte-free
*k-th value* `value(T, k)` for `k ≥ 1`:

| Type | `value(T, k)` |
| --- | --- |
| `Int` | `k` |
| `Str` | `"k"` |
| `Bool` | `k` odd |
| uninterpreted `C` | `"valuek_OF_C"` |
| tuple, record | componentwise `value(Ti, k)` |
| `Set(E)` | `{value(E, k)}` |
| `Seq(E)` | `<<value(E, k)>>` |
| `A -> R` | `[x \in {value(A, k)} \|-> value(R, k)]` |
| variant | first tag applied to `value(field, k)` |

With `n = clamp(b)`, the closed terminals are:

| Type | Closed terminal |
| --- | --- |
| `Set(E)` | `{value(E, 1), …, value(E, n)}`, or `{}` when `n = 0` |
| `Seq(E)` | `<<value(E, 1), …, value(E, n)>>` |
| `A -> R` | `[x \in {value(A, 1), …, value(A, n)} \|-> terminal(R)]` |

The values of `Bool`, and of variants and tuples built only from `Bool`, repeat,
so their sets are capped at their number of distinct values; the literal still
typechecks. Terminal rotation over visible bindings ([§6][termination]) is
unchanged: a visible binding of the requested type still takes precedence, and
components of composite terminals still rotate.

The closed terminals of scalar types are unchanged. `Int` stays `0`, although
`0` is never a valid sequence index; changing it is a separate decision whose
effect on arithmetic, ranges and `EXCEPT` indices needs its own measurement.

### Atom budget

Nested base sizes multiply: with `b = 10` and `max_type_depth = 3`, a
`Set(Seq(Set(Int)))` terminal holds 1,000 integers. A value-atom budget bounds
this. A collection literal or terminal generated under budget `a` has size at
most `a`, and each of its `n` elements is generated under budget `⌊a / n⌋`. The
top-level budget is `max_value_atoms`. The budget is scoped on
`GenerationContext`, like the expression-request counter, and costs no bytes.

`max_nodes` remains the bound on explicit expressions. A base-size literal spends
more requests, so later operands starve into terminals sooner; those terminals
now have base size, but calibration of `max_nodes` is part of the evaluation.

### Configuration

Three `[generator]` keys:

| Key | Default | Meaning |
| --- | ---: | --- |
| `collection_base_size` | 3 | Size that exhausted input decodes to. Must be in `0..max_collection_size`. |
| `collection_size_spread` | 4 | Bytes move a size within base ± spread. Must be in `0..127`. |
| `max_value_atoms` | 64 | Atom budget of one collection literal or terminal. Must be positive. |

The default `collection_base_size = 3` comes from the evaluation below.

### Base 0 is not the current decoder

With `collection_base_size = 0`, terminals are empty and literal sizes cluster at
their minimum, as today, but the encoding differs:

- A literal's size is `max(1, offset)`: 1 with probability about
  `(s + 2) / (2s + 1)`, and uniform above, instead of `1 + Geometric(½)`.
- The size byte precedes the elements, where continuation markers interleave
  with them, so the same bytes decode to a different module.

This ADR keeps one encoding. Per repository policy, stored inputs are not
migrated; corpora written before the change do not replay.

## Evaluation plan

Before accepting, run 1,000-entry PBT corpora (no mutation) with
`collection_base_size ∈ {0, 3, 6}` and one seed, and compare over agreeing and
all entries:

- share of TLC failures in `Init`, and the four failure details above;
- share with `maxCardinality ≥ 1`, `≥ 3`, and the distribution of
  `maxStateNodes` and `saturated`;
- share with `projectedStates ≥ 2`;
- TLC and Apalache time and timeouts;
- known-issue matches of the triager, by issue.

Accept if a positive base raises `maxCardinality ≥ 3` substantially without
raising timeouts to a comparable degree. The chosen base then becomes a dimension
of the exploration versus exploitation experiment
(`script/mutation-experiment/`).

## Evaluation results

Three `module` corpora of 1,000 PBT entries each, seed 11, `feedback_ratio = 0`,
default configuration otherwise, 30 s checker timeouts. fuzztla: this change on
top of `54ee477`; TLC: tla2tools `142d0ba`; Apalache: release 0.62.2. Shares are
of the 1,000 entries, excluding `00-known-defects`.

| Measure | Base 0 | Base 3 | Base 6 |
| --- | ---: | ---: | ---: |
| Quarantined known defects | 335 | 289 | 305 |
| Parser failures | 0 | 0 | 0 |
| ≥ 1 initial state | 26.5% | 40.2% | 40.2% |
| TLC fails in `Init` | 78.7% | 74.3% | 73.4% |
| `maxCardinality ≥ 1` | 11.2% | 19.4% | 18.8% |
| `maxCardinality ≥ 3` | 2.2% | 5.8% | 5.4% |
| `maxStateNodes ≥ 10` | 1.2% | 6.1% | 6.3% |
| `projectedStates ≥ 2` | 0.1% | 3.1% | 3.4% |
| Checkers agree | 33.2% | 32.9% | 33.3% |
| Mean evaluated nodes | 384 | 652 | 797 |
| Mean TLC / Apalache time (ms) | 357 / 672 | 475 / 1,648 | 460 / 2,077 |
| Apalache crashes (of which timeouts) | 3 (0) | 45 (35) | 61 (52) |

- A positive base doubles to triples every size measure and raises state change
  from 1 to 31 entries. Base 6 adds nothing over base 3: sizes are bounded by
  `max_value_atoms` and by how rarely values survive `Init`.
- `head-of-empty-sequence` disagreements fall from 71 to 8, and
  `tail-of-empty-sequence` from 14 to 2. `function-application-outside-domain`,
  `choose-without-witness` and `case-without-matching-arm` stay flat: they come
  from explicit empty forms, integer terminal `0` and generated predicates, which
  this ADR does not change. Tuple index 0 remains a top TLC failure.
- The cost is Apalache time. Mean time rises 2.5 to 3 times, and Apalache timeouts
  rise from 0 to 35 and 52. Known crash classes (`apalache-bmc-001`,
  `apalache-cli-001`) stay at a handful; TLC is unaffected.

**Default:** `collection_base_size = 3`. Changing the closed `Int`
terminal from `0` to `1` is the next candidate, measured the same way.

## Alternatives considered

- **Calibrate continuation markers to an `average_size`, as Hypothesis does.**
  Requires a Boolean draw with a probability other than ½, that is, a byte
  compared against a threshold instead of its low bit. It raises random sizes but
  leaves exhausted input and terminals empty, which is where the measurements
  locate most empty values.
- **Only non-empty terminals, without a size draw.** Fixes `Init` failures on
  terminals but leaves explicit literals at a mean size below 2. Covered by this
  ADR with `collection_size_spread = 0` for terminals only, and cheaper to test
  as that setting than as a separate design.
- **Keep continuation markers when the base is 0.** Reproduces the current
  corpus exactly, at the cost of two encodings of one concept.
- **Weight collection-producing forms higher.** `weights` already favours
  `enum_set`. More literals do not help while their evaluation fails on empty
  terminals; cohorts 5–9 show this.
- **Richer richness cohorts ([ADR 0002][adr-0002]).** The score is syntactic, and
  the table above shows that syntax does not reach the state.
- **Rank the quality gate on state size.** Complementary: it selects large values
  once they exist. It does not create them.

## Consequences

- **Every stored input is reinterpreted.** Set and sequence literals change
  their byte layout, and every terminal of a collection type changes.
- **Architecture revision.** [ir-generators.md][ir-generators] §4 (continuation
  markers only for structural lists, the size draw for value collections), §6
  (base-size terminals, the atom budget, the limits table) are updated
  with the implementation.
- **Known-issue mix shifts.** Disagreements rooted in empty terminals, such as
  `head-of-empty-sequence`, `function-application-outside-domain` and
  `choose-without-witness`, should become rarer; disagreements that need
  large values, such as `SUBSET` and function sets, more frequent. Triage
  signatures and known-defect sample counts need a new baseline.
- **Checker cost rises.** `SUBSET S` and `[S -> T]` grow exponentially in the size
  of `S`. The atom budget bounds literals, not the values computed from them, so
  timeouts at the 30 s limit are the main cost to watch.
- **Mutation.** `parity_flip` on a size byte changes a value's size by a small
  amount instead of toggling one element; on a structural marker it behaves as
  before. [ADR 0010][adr-0010]'s operator table remains accurate for structural
  lists only.
- **Richness cohorts.** Scores rise with the base; the cohort thresholds of
  [ADR 0002][adr-0002] may need recalibration or may become redundant.
- **Shrinking.** All-zero bytes no longer denote the simplest module. The project
  has no byte-level shrinker; one would need to reach smaller sizes through
  non-zero size bytes.

[adr-0002]: 0002-pbt-richness-score.md
[adr-0010]: 0010-mutation.md
[ir-generators]: ../architecture/ir-generators.md
[byte-decoding]: ../architecture/ir-generators.md#4-byte-decoding
[termination]: ../architecture/ir-generators.md#6-termination-and-resource-limits
