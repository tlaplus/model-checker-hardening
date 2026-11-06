# 0030: A budget of temporal operators per rewritten body

**Authors:** Igor Konnov and Claude

**Status:** Implemented

**Date:** 2026-11-06

**Implementation.** `gen.rewrite.RewriteLimits` gains `maximumTemporalOperators`, read
from `[metamorphic] max_rewrite_temporal_operators`, and `gen.rewrite.Rewriter` checks it
next to `maximumGrowth`. Replayed with the default budget, the 150 sampled corpus80 timeouts of
the Context rewrite to properties of 5 to 16 temporal operators, median 15, counted on the
IR; every one of them still applies a temporal rule.

## Context

[ADR 0027][adr-0027] rewrites a module's property and fairness conditions with temporal
rules, and TLC checks the rewritten property in a run of its own. [ADR 0028][adr-0028]
raised the weights of `[]`, `<>` and `~>` and expected more of these runs to time out in
TLC's tableau construction.

We measured corpus80, an `mt` corpus with `temporal_rules` and the checkers TLC and
TLC-n (FuzzTLA `ecc8346a`, TLC `309e489`, Apalache 0.62.3, not run), after its run of
382,595 generator attempts:

- **Every TLC timeout is a property run.** The triager labels 996 TLC and 1,078 TLC-n
  crashes `TIMEOUT`. All are property runs, and 995 of the 996 TLC timeouts have their
  thread in `tlc2.tool.liveness.TBGraph.<init>`: TLC is building the tableau and has not
  explored a state. At `timeout_sec = 300`, they take 299,000 of the 592,000 seconds of
  the TLC stage.
- **The rewrite, not the base, times out.** In a random sample of 150 TLC timeouts, the
  timed-out run checked the rewritten side every time. The base properties have a median
  of 2 temporal operators (90th percentile 7); their rewrites have a median of 40 (10th
  percentile 26, minimum 14).
- **Two rules make the copies.** `EventuallyWeaken` (`F` to `F /\ <>F`) and
  `AlwaysOrBreak` (`F` to `[]F \/ (F /\ <>~F)`) copy their operand and apply to any
  formula. They are 697 of the 924 temporal rule applications in the sample.

The existing bound does not stop this. A rule applies while the body stays within
`max_rewrite_growth` times its original size plus the node budget of one fresh operand,
`max_nodes` (128). For a property of 10 nodes, the fresh budget dominates: the body may
reach 148 nodes, enough for dozens of nested temporal operators.

The number of temporal operators is a proxy, not the tableau's size. Of the
rewritten properties in `04quality-pass`, which TLC decided, 28.0% have more than 16
temporal operators. The table gives the share above a limit of the 150 timed-out
rewrites, of the 3,713 properties in `04quality-pass`, and of the 2,775 properties in a
random 5,000 entries of `04quality-fail`, taking the larger side of each entry. The
operators are counted on the printed formulas, which approximates the count defined
below; the Context's other counts are made the same way:

| Limit | Timed-out rewrites above | Kept above | Rejected above |
| ---: | ---: | ---: | ---: |
| 8 | 100.0% | 44.7% | 35.6% |
| 12 | 100.0% | 33.3% | 26.2% |
| 16 | 98.7% | 28.0% | 22.5% |
| 24 | 92.7% | 19.7% | 16.4% |
| 32 | 74.7% | 13.5% | 12.2% |

## Decision

A *temporal operator* is a node of temporal level: `[]`, `<>`, `~>`, `-+->`, `WF_v`,
`SF_v`, `\EE` or `\AA` (`FormulaLevels.TEMPORAL`). `[][A]_v` counts once, for its `[]`.

`[metamorphic]` gains one limit:

```toml
# Temporal operators a rewritten body may reach; a rule that would add more does not apply.
max_rewrite_temporal_operators = 16
```

It bounds each rewritable body as `max_rewrite_growth` does, and the walk checks both at
the same place:

- A rule applies at a node only if its replacement adds no temporal operator, or if the
  body's temporal operators, after the replacement, are at most the limit.
- The replacement's count is estimated without bytes, as its size is: each parameter
  counts the temporal operators of its binding, and a fresh parameter none.
- A base above the limit keeps it. Rules that add no temporal operator still apply to
  it, so its rewrite tests the base rules.

The limit is 16. It is above 98.7% of the sampled timeouts, and ADR 0028 found 16 or
more temporal operators in each of the 12 corpus78 timeouts it inspected. An entry above
the limit is not discarded: its rewrite stops adding temporal operators and keeps the
rules applied so far.

## Alternatives considered

- **Lower weights for `EventuallyWeaken` and `AlwaysOrBreak`.** They would still stack,
  only less often, and the weight is a corpus setting that a rule library may override.
  A budget bounds any rule that copies a temporal operand, including future ones.
- **Shorter `timeout_sec` for property runs.** It saves time but keeps the inputs: the
  property runs still decide nothing.
- **A tableau-size estimate.** TLC's tableau grows with the distinct subformulas and
  their disjunctive normal forms, which a byte-free estimate cannot track cheaply. The
  count is exact on the formula and needs no model of TLC.
- **A known-defect signature.** A signature quarantines the whole module, its relation
  run included, and the slow tableau is a cost of TLC's algorithm, not a defect.

## Consequences

- New `mt` corpora with temporal rules spend less TLC time in property runs that never
  explore a state. Rewritten properties nest fewer copies of their operands.
- The limit applies to every rewritable body. `Init`, `Next`, `Inv` and the operator
  definitions contain no operator of temporal level, so they rewrite as before.
- An existing corpus whose `config.toml` lacks the key gets the default, so its entries
  may replay to a different rewrite than the one its checkers ran. Re-initialize a
  corpus to triage it with this build, or add `max_rewrite_temporal_operators` with a
  large value to keep its rewrites.
- This extends the rewrite limits of [ADR 0016][adr-0016] §7.

[adr-0016]: 0016-metamorphic-testing.md
[adr-0027]: 0027-temporal-metamorphic-testing.md
[adr-0028]: 0028-temporal-property-quality.md
