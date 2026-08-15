# 0002: PBT collection-richness score

**Authors:** Igor Konnov and OpenAI Codex

**Status:** Accepted

**Date:** 2026-08-15

## Context

The IR decoder deliberately produces closed terminal expressions when its input
is exhausted. Composite terminal expressions contain empty collections. Random
short inputs therefore overrepresent empty sets, sequences, tuples, and records,
even though the byte decoder itself is behaving as designed.

Property-based testing libraries generally expose size as a generation or search
dimension instead of globally excluding small examples. QuickCheck passes an
explicit [size parameter][] to generators. Hedgehog combines generated values
with [ranges that shrink][]. Hypothesis supports collection size bounds and uses
[`target`][] to guide search towards high-valued observations. jqwik and Proptest
likewise expose [collection size constraints][] and [size ranges][]. These
approaches retain small baseline cases while deliberately exploring larger ones.

## Decision

The workflow applies stratified rejection sampling after decoding a candidate
input. It does not change the IR decoder.

For an expression, define the collection-richness score as

`sum(literal_size * nesting_base^collection_level)`.

The scored literals are explicit set enumerations, sequence and tuple literals,
and record literals. Their sizes are respectively the number of operands,
elements, and fields. The root collection level is zero. The level
increases only while descending through another scored collection literal;
ordinary AST nodes do not increase it. The score is syntactic and does not
evaluate the expression.

The `[pbt]` configuration contains three mandatory controls:

- `richness_cohorts`, default 10;
- `richness_nesting_base`, default 2.0; and
- `richness_threshold_base`, default 1.5.

For each target corpus entry, the input stage selects a cohort uniformly from
`0 .. richness_cohorts - 1`. Cohort 0 has threshold 0. Cohort `c > 0` has
threshold `richness_threshold_base^(c - 1)`. The default thresholds yield the
effective integer cutoffs `0, 1, 2, 3, 4, 6, 8, 12, 18, 26`.

Generator workers dynamically claim target entries. The run seed
deterministically produces one seed per worker, and each worker derives separate
cohort and candidate streams from its seed. The selected cohort remains fixed
across generator rejections, richness rejections, and duplicate inputs until a
unique input is admitted. A target that cannot be admitted within 10,000
candidate attempts stops the workflow and reports its worker, worker seed,
cohort, threshold, attempt count, and best observed score.

Each admitted PBT envelope stores the admission result as
`"gen": {"cohort": C, "richness": R}`. Later stages preserve this field. The raw
input bytes remain the corpus identity.

## Consequences

The corpus retains unconstrained small examples through cohort 0 while reserving
equal expected capacity for progressively richer expressions. Richness rejection
increases generation cost, and an infeasible threshold now fails explicitly
instead of spinning indefinitely. The stored score explains admission but is not
a promise that future scorer versions will reproduce it. This policy improves
literal diversity; it is not coverage-guided search, mutation, or shrinking.

[size parameter]: https://hackage.haskell.org/package/QuickCheck/docs/Test-QuickCheck-Gen.html#v:sized
[ranges that shrink]: https://hackage.haskell.org/package/hedgehog/docs/Hedgehog-Range.html
[`target`]: https://hypothesis.readthedocs.io/en/latest/reference/api.html#hypothesis.target
[collection size constraints]: https://jqwik.net/docs/current/user-guide.html#list-arbitrary
[size ranges]: https://proptest-rs.github.io/proptest/proptest/collection/fn.vec.html
