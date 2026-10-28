# Configuration

Each corpus has a `config.toml`, written by `fuzztla init`. The file is commented, and
this page lists every key by table. Paths in the file are relative to the file itself.

You may leave out a checker table (`[workflow.tlc]`, `[workflow.tlcn]`,
`[workflow.apalache]`) or any of its keys, as well as `[metamorphic]`, `[graph]`,
`checkers` and the `max_entries` key of a stage table. Anything left out takes the default
shown below. The other keys are required. Settings that decide how entries decode
and are judged must not change once the corpus holds entries: `technique`,
`checkers`, the `[generator]` and `[graph]` tables, the rules and the libraries.
FuzzTLA does not detect such an edit
([ADR 0025](../decisions/0025-self-contained-corpora.md) §4). To change them,
initialize a new corpus.

## `[generator]`: what is generated

| Key | Default | Meaning |
|---|---|---|
| `kind` | `"module"` | `"module"` generates whole modules with variables, actions and properties. `"expr"` generates one expression and wraps it in a fixed single-state module. `"graph"` generates a module over a control graph, whose verdict the graph predicts; `technique = "gbt"` requires it, and `init --how=gbt` sets it ([Graph-based testing](graph-based-testing.md)). |
| `max_type_depth` | `3` | Maximum nesting of generated types, such as a set of functions to sequences. |
| `max_expression_depth` | `32` | Maximum depth of an expression tree. |
| `max_nodes` | `128` | Maximum nodes of an expression tree. |
| `max_collection_size` | `8` | Maximum elements of a set, sequence, tuple or record literal. |
| `collection_base_size`, `collection_size_spread` | `3`, `4` | Typical collection size and how far input moves it ([Collection sizes](collection-size.md)). |
| `max_value_atoms` | `64` | Maximum scalars in one collection value, over all nesting levels. |
| `max_string_bytes` | `32` | Maximum length of a string literal. |
| `max_integer_bytes`, `integer_base`, `integer_literal_spread`, `integer_literals` | `16`, `1`, `4`, `"boundary"` | How integer literals are drawn ([Integer literals](integer-literals.md)). |
| `max_variables` | `3` | Maximum state variables of a module. |
| `max_auxiliary_operators` | `2` | Maximum state-free operator definitions a module may use. |
| `max_action_operators` | `2` | Maximum action definitions the next-state relation may use. |
| `max_actions` | `3` | Maximum disjuncts of `Next`. |
| `max_action_parameters` | `2` | Maximum `\E` parameters of one action. |
| `max_action_depth` | `3` | Maximum nesting of `\/`, `/\` and `IF` within one disjunct of `Next`; `0` keeps each disjunct a flat conjunction. |
| `max_steps` | `5` | Steps from an initial state. A step counter guards `Next`, so the state space is finite. |
| `max_fairness` | `2` | Maximum weak and strong fairness conditions ([Actions and temporal properties](temporal-properties.md)). |
| `ignore` | `["unbound", "exotic"]` | Categories of expressions not to generate (below). |
| `weights` | `{ name = 8, enum_set = 16 }` | Relative frequency of expression forms (below). |
| `classpath`, `custom_operators` | `[]` | Operator libraries to generate calls to ([Custom operators](custom-operators.md)). |

### Excluding categories

Every expression form belongs to one category. Listing a category in `ignore`
excludes its forms and everything that depends on them. For example, ignoring `set`
also excludes bounded quantifiers and functions, because their domains are sets.
`ignore = []` enables everything.

`action`, `temporal`, `unbound`, `exotic`, `control`, `label`, `operator`,
`quantifier`, `bool_logic`, `arithmetic`, `set`, `finite_set`, `universe`,
`sequence`, `function`, `fold`, `tuple`, `record`, `variant`, `model`.

`unbound` covers unbounded quantifiers and `CHOOSE` (`\A x : P`, `\E x : P`,
`CHOOSE x : P`), which neither checker can evaluate in general. `exotic` covers
operators that one or both checkers do not support: `\cdot`, `-+->`, and temporal `\EE`
and `\AA`. Both are ignored by default. The `core` category provides literals and fallbacks and cannot be
ignored.

### Weighting forms

`weights` maps the lowercase name of an expression form, such as `plus`, `fold_set`
or `temporal_forall`, to a weight. A form with weight *N* is chosen *N* times as often
as a form of weight one. Unlisted forms have weight one. Custom operators are weighted
as `"Module!Operator"`.

## `[workflow]`: campaign size, technique and checkers

| Key | Default | Meaning |
|---|---|---|
| `max_entries` | `1000` | Unique entries after which the campaign stops, across all directories. |
| `technique` | `"pbt"` | `"pbt"`, `"mt"` or `"gbt"`, written by `init --how`. Every `run` follows it. Keep it once the corpus holds entries. |
| `checkers` | `["tlc", "apalache"]` | Checkers to run, a subset of `tlc`, `tlcn` and `apalache`. With one checker, nothing is compared. Keep it once the corpus holds entries. |

### `[workflow.inputs]`

| Key | Default | Meaning |
|---|---|---|
| `max_entries` | `workflow.max_entries` | Maximum inputs waiting for the parser. |
| `known_defects` | the shipped database | [Known-defect signature](known-defect-signatures.md) files. Matching candidates are quarantined instead of checked. `[]` checks every candidate. |
| `known_defect_samples` | `100` | Quarantined entries kept per signature. Further matches are only counted. |

### `[workflow.parser]`, `[workflow.tlc]`, `[workflow.tlcn]`, `[workflow.apalache]`

| Key | Default | Meaning |
|---|---|---|
| `max_entries` | `workflow.max_entries` | Maximum entries in the stage's result directories. |
| `timeout_sec` | `30` | Time limit per input. A slower run is a `crash` with a [timeout diagnostic](timeout-diagnostics.md). |
| `max_heap_mb` | `512`, Apalache `1024` | Heap of each checker JVM. Not for the parser. |
| `workers` | TLC `1`, TLC-n `4`, Apalache half the processors | For TLC and TLC-n: TLC worker threads per input. For Apalache: concurrent Apalache processes. Must not exceed `--max-cpus`. |

## `[pbt]`: random inputs

Property-based testing generates inputs from random bytes. Random modules tend to
have few and small collections, so inputs are sorted into *richness cohorts*. A
cohort admits only modules whose collection literals are large enough, which
balances simple and rich inputs.

| Key | Default | Meaning |
|---|---|---|
| `max_input_bytes` | `10240` | Maximum length of a random input. Lengths are drawn from logarithmic buckets 0–3, 4–7, 8–15, and so on. |
| `richness_cohorts` | `10` | Number of cohorts. Cohort 0 admits every module. |
| `richness_nesting_base` | `2.0` | Weight of each level of collection nesting in the richness score. |
| `richness_threshold_base` | `1.5` | Cohort *c* > 0 requires a score of at least `richness_threshold_base^(c-1)`. |

If a cohort cannot be filled after 10,000 candidates, the run stops and reports the
best score it saw. Lower `richness_cohorts` or `richness_threshold_base`, or ignore
fewer categories.

## `[mutator]`: generations and mutation

`generation_size`, `select_fraction`, `feedback_ratio`, `max_edits`, `weights`,
`shallow_patterns`, `cell_capacity` and `feature_coverage` control how each
generation is selected and mutated. They are described in
[Generations and mutation](mutation.md#2-configuration).

## `[metamorphic]`: rewrites

`rules`, `weights`, `max_rewrites`, `max_rewrite_depth`, `max_rewrite_growth`,
`base_corpus` and `adopt_ratio` are used only by an `mt` corpus. They are described in
[Metamorphic testing](metamorphic-testing.md#1-setting-up-a-corpus).

## `[graph]`: control graphs

| Key | Default | Meaning |
|---|---|---|
| `min_graph_nodes` | `20` | Minimum nodes of the control graph of a `graph` input. The graph has a spine `0 -> 1 -> ... -> N-1`, so it is up to N - 1 steps deep. |
| `max_graph_nodes` | `32` | Maximum nodes. Node 0 is initial. |
| `max_extra_successors` | `2` | Maximum successors of one node besides its spine successor: back edges, self-loops and forward skips of at most two nodes. |
| `max_steps` | `max_graph_nodes` | Cap on the step bound. Each input draws its own bound between half its node count and its node count minus one. |
| `max_guards` | `2` | Maximum tautological guards of one edge, when `rules` is set. |
| `rules` | absent | Rule module of the edge guards, as `{ module = "Rewrites", classpath = ["../libraries/rewrites"] }`. Absent, edges have no guards. |
| `weights` | `{}` | Selection slots per guard rule, overriding its `@weight` annotation; 0 disables a rule. |

The keys apply to `graph` inputs only ([Graph-based testing](graph-based-testing.md)).
Their data variables and expressions use the `[generator]` keys; `[generator]
max_steps` does not apply to them. Setting or changing `rules` changes how every
`graph` input decodes, so a corpus records the rule module in `.rewrite-library` and
refuses a different one.
