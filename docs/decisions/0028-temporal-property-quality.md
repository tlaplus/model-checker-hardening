# 0028: Temporal properties that read the state

**Authors:** Igor Konnov and Claude

**Status:** Proposed

**Date:** 2026-11-03

## Context

[ADR 0007][adr-0007] gives a generated module an optional temporal property, and
[ADR 0027][adr-0027] checks rewritten properties against their originals. Both are
only as useful as the properties the generator draws. We measured them in corpus78, an
`mt` corpus with `temporal_rules` (FuzzTLA `42db61d5`, TLC `dc6a944`, Apalache
0.62.3), by replaying a random sample of 1,500 entries of `04quality-pass` and 1,500 of
`04quality-fail` and walking the IR of each property:

| Property before rewriting | Share |
| --- | ---: |
| `TRUE` or `FALSE` | 45.9% |
| Reads no state variable | 24.6% |
| Reads the state, no temporal operator | 22.0% |
| A temporal operator over the state | 7.5% |

- **Few properties are temporal.** With `PROPERTY P`, TLC checks a state predicate `P`
  in the initial states only. Of the temporal ones, 106 of 224 are `[][A]_v` and 55
  are `~>`. `<>P` occurs 8 times; `[]<>P` and `<>[]P` do not occur.
- **The temporal rules rewrite trivial bases.** 15.7% of the sample applied a temporal
  rule. In 74% of them the base property read no state or had no temporal operator.
  `EventuallyWeaken` and `AlwaysOrBreak` made up 1,080 of 1,386 applications: they add
  temporal operators to any formula, so they rewrite constants as readily as
  liveness properties. The rules that act on temporal structure, such as
  `InfOftenIdem` or `WFDef`, rarely or never matched.
- **The gate does not prefer them.** The quality gate ([ADR 0013][adr-0013]) ranks by
  TLC's exploration metrics, which do not measure the property. Its coverage features
  count `GLOBALLY` over `FALSE` like `GLOBALLY` over a state variable. Temporal
  properties over the state make up 9.2% of `04quality-pass` and 5.7% of
  `04quality-fail`: the pool keeps them somewhat more often, but only through the
  other features and metrics.

Every temporal form has selection weight 1. A property formula is a Boolean drawn at
the temporal level, where the five temporal forms compete with every other Boolean
form and with the forms of any type, such as `IF` and `CASE`. We decoded
4,000 random module inputs (`pbt`, corpus78's generator settings) per weight setting
of `always`, `eventually` and `leads_to`, and measured the share of inputs whose
property applies a temporal operator to the state, the share that nests one temporal
operator under another, and the share that the shipped known-defect signatures
quarantine. About 48% of inputs draw a property at all.

| Weight | Temporal over state | Nested | Quarantined |
| ---: | ---: | ---: | ---: |
| 1 | 11.8% | 0.6% | 28.1% |
| 2 | 13.1% | 1.7% | 28.6% |
| 4 | 16.3% | 2.8% | 28.8% |
| 8 | 20.3% | 7.6% | 28.9% |
| 16 | 25.9% | 14.8% | 29.7% |
| 32 | 31.2% | 22.0% | 31.1% |

At weight 16, state predicates without a temporal operator fall from 756 to 315 of
4,000 inputs. Raising `weak_fair` and `strong_fair` to 4 as well added 1.2 points of
temporal properties and 2 points of quarantine.

## Decision

### 1. Weights

The default selection weights become:

```toml
weights = { name = 8, always = 16, eventually = 16, leads_to = 16, enum_set = 16 }
```

A weight is global, but these forms apply only at the temporal level, so they change
only property formulas: those of `Prop` and, in a graph module with
`[graph] properties`, its formula after the fairness conditions. `Init`, `Next`, `Inv`
and the actions of fairness conditions decode as before. A corpus records its weights,
so existing corpora are unaffected.

### 2. Temporal shapes

A *temporal operator* is one of `[]`, `<>`, `~>`, `WF_v`, `SF_v`, `[A]_v`, `<<A>>_v`
and `ENABLED`, named in Apalache's IR as `GLOBALLY`, `EVENTUALLY`, `LEADS_TO`,
`WEAK_FAIRNESS`, `STRONG_FAIRNESS`, `STUTTER`, `NO_STUTTER` and `ENABLED`. An
occurrence *reads the state* when its subexpression mentions a state variable, directly
or through the definitions it applies. Every state variable counts, `step` and `pc`
included.

The walk starts at the property and fairness definitions: `Prop` and `Fairness`, and
in a module with property runs also `PropC` and `FairnessC`. It follows definitions
and treats labels as [ADR 0006][adr-0006]'s walk does. A *temporal shape* is a pair
`(enclosing, operator)` for an occurrence of a temporal operator that reads the state.
`enclosing` is the nearest enclosing temporal operator that reads the state, or
`PROPERTY` at the top. `[](<>(x > 0))` has the shapes `PROPERTY → GLOBALLY` and
`GLOBALLY → EVENTUALLY`; `[](<>TRUE)` has none.

### 3. The gate

The gate of ADR 0013 rewards an entry for its temporal shapes twice.

- **Coverage.** An entry has a third kind of coverage feature,
  `(temporal shape, bucket(n))` for a shape that occurs `n` times, with ADR 0013's
  bucket. An entry whose property adds a shape or a bucket passes by ADR 0013's rule 2.
- **Ranking.** The ranking of `pbt` and `mt` corpora (`Ranking.DEPTH`) gains a
  component: the number of distinct temporal shapes, by power of two. It comes third,
  after `projectedDepth` and `projectedStates`:

  ```
  (projectedDepth, bucket(projectedStates), bucket(temporalShapes),
   actionsDiscovering, bucket(maxStateNodes), bucket(maxCardinality), maxNesting)
  ```

  Like every component, it is part of the behaviour cell, so each bucket of temporal
  shapes has `cell_capacity` places of its own. `projectedDepth` is at most
  `max_steps`, and `projectedStates` is bucketed, so ties at the first two components
  are common and the third decides often. Exploration still comes first: an entry
  that changes the state more deeply is not passed over for a richer property.

The ranking of `gbt` corpora ([ADR 0024][adr-0024]) is unchanged: `gbt` does not check
properties.

The shapes derive from the input, as the coverage features do. The gate therefore
replays every admissible entry to rank it, and the pool to form its cells, whether or
not `feature_coverage` is enabled.

## Alternatives considered

- **A shallow pattern for constant properties.** It would discard entries whose
  property is trivial but whose exploration is deep. The property is optional, so its
  absence must not exclude an entry.
- **Only data variables.** Excluding `step` and `pc`, as the projected metrics do
  ([ADR 0024][adr-0024]), would ignore 68% of corpus78's state-dependent properties,
  which read only `step`. A formula over `step` still exercises TLC's tableau and
  liveness checking.
- **The shape count as the first component.** The pool would fill with rich properties
  over shallow exploration: at weight 16, a quarter of the inputs has a temporal
  shape, more than `select_fraction` keeps.
- **Raising the weight of the property marker.** The marker is one Boolean of the
  byte encoding, not a weighted form. Changing it would change how every module input
  decodes, for the 50% of inputs without a property.
- **Weight 32.** It adds 5 points of temporal properties but 7 of nested ones. Nesting
  is what makes TLC's tableau construction slow: the 97 TLC timeouts of corpus78 are
  in `TBGraph`, and each of the 12 we inspected had 16 or more temporal operators.

## Consequences

- New corpora draw more temporal properties, and more nested ones. TLC's property runs
  take longer, and more of them may time out in tableau construction.
- Slightly more inputs match known-defect signatures for temporal formulas TLC
  cannot check (about 1.6 points at weight 16).
- The gate replays the pool and the admissible entries in every generation. In an `mt`
  corpus a replay costs about 90 ms, so a pool of 4,000 entries costs about 6 minutes
  of gate time per generation. ADR 0013 already pays this when `feature_coverage` is
  enabled, which is the default.
- The cells of existing corpora change: their pools are re-cut by the new component
  when a run continues.
- This revises the ranking of [ADR 0010][adr-0010] and the gate of ADR 0013 for
  `pbt` and `mt` corpora, and the default weights of the
  [generator architecture][generators].

[adr-0006]: 0006-known-defect-signatures.md
[adr-0007]: 0007-levels-and-temporal-properties.md
[adr-0010]: 0010-mutation.md
[adr-0013]: 0013-behaviour-archive-and-operator-coverage.md
[adr-0024]: 0024-graph-action-coverage.md
[adr-0027]: 0027-temporal-metamorphic-testing.md
[generators]: ../architecture/ir-generators.md
