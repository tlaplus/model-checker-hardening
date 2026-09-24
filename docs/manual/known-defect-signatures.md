# Known-defect signatures

> **Status:** Implemented. [ADR 0006](../decisions/0006-known-defect-signatures.md)
> records the design and its rationale.

A known-defect signature describes a shape of generated TLA+ IR that a tool is
already known to reject. Examples are a literal-zero divisor, which Apalache
reports as `Mod by zero`, and the set `STRING`, which Apalache does not support.
When a signature database is configured, the input stage checks every candidate
against it. Matching candidates go to a quarantine directory instead of the
parser, so SANY, TLC and Apalache spend no time rediscovering filed defects.
A corpus created by `fuzztla init` consults the [shipped database](#7-shipped-database).
Without a database, every candidate is admitted as before.

## 1. Enabling signatures

Two keys in the corpus's `config.toml` control the feature:

```toml
[workflow.inputs]
# Known-defect signature databases, relative to this config file.
known_defects = ["../signatures/known-defects.toml"]
# Quarantined entries kept per signature in 00-known-defects.
known_defect_samples = 100
```

- `known_defects` lists database files. Relative paths resolve against the
  directory that contains `config.toml`. Several databases are concatenated in
  the listed order. The empty list turns the feature off.

  `fuzztla init` lists the repository's `signatures/known-defects.toml`. For a
  corpus inside the repository the path is relative; the example above is what
  it writes for a corpus in the repository root. For a corpus elsewhere the
  path is absolute. If `init` cannot find the database, it writes the empty
  list and says so on standard error.
- `known_defect_samples` caps how many matching candidates are stored per
  signature. Further matches are counted but not stored.

Both keys are required, like every other configuration key. A corpus created
before the feature existed needs them added to its `config.toml`.

`fuzztla run` reads the databases once, at startup. An invalid database stops
the run before the corpus is locked. Changing a database between runs affects
only later admissions; stored entries are not re-checked.

## 2. Database file

A database is a TOML file with one `[[signature]]` table per signature:

```toml
[[signature]]
id = "modulo-by-literal-zero"
references = ["../conformance/modulo-by-zero-apalache-fails.md"]
description = "Apalache preprocessing reports Mod by zero for any literal-zero modulus."
match = ['(MOD _ 0)']

[[signature]]
id = "zero-power-zero"
references = ["../conformance/zero-power-zero-apalache-fails.md"]
description = "Apalache preprocessing reports 0 ^ 0 as undefined."
match = ['(POW 0 0)']
```

| Key | Meaning |
| --- | --- |
| `id` | Lowercase letters, digits and hyphens. Unique across all configured databases. Recorded in quarantined entries and in statistics, so keep it stable. |
| `references` | Non-empty list of the finding or conformance documents that record the defect, relative to the database file. |
| `description` | One sentence: what the tool does with inputs of this shape. |
| `match` | Non-empty list of patterns. The signature matches when any of them matches. |

All four keys are required, and unknown keys are errors. Write patterns as TOML
literal strings (single quotes), so that `"` and `\` inside them need no
escaping.

## 3. Pattern language

### 3.1. Vocabulary

Patterns use the names of Apalache's typed IR, exactly as `fuzztla print
--apalache-ir` prints them. That output is the reference for writing a pattern.
This IR node:

```json
{"type": "Int", "kind": "OperEx", "oper": "MOD", "args": [
  {"type": "Int", "kind": "NameEx", "name": "step"},
  {"type": "Int", "kind": "ValEx", "value": {"kind": "TlaInt", "value": 0}}]}
```

is matched by `(MOD step 0)`, and by the more general `(MOD _ 0)`.

Operator names are case-sensitive. Standard-module operators carry their module
prefix, as in `Sequences!Seq`, `Sequences!Head`, `FiniteSets!Cardinality` and
`Apalache!ApaFoldSet`. Other operators are named by their IR name, not by their
TLA+ notation. Examples: `SET_ENUM` for `{a, b}`, `TUPLE` for `<<a, b>>`, which
includes the empty sequence `<<>>`, `FUN_APP` for `f[x]`, `SET_FILTER` for
`{x \in S : p}`, `CHOOSE3` for bounded `CHOOSE`. An application of a
user-defined operator is `OPER_APP`, with the operator's name as its first
argument.

### 3.2. Syntax

| Pattern | Matches | Example |
| --- | --- | --- |
| `_` | Any expression. | `(MOD _ 0)` |
| `?x` | Any expression. All occurrences of the same metavariable in one alternative must match equal expressions. | `(EQ ?x ?x)` |
| integer | An integer literal with this value. | `0`, `-3` |
| `"text"` | A string literal with this value. | `(EQ _ "a")` |
| `TRUE`, `FALSE` | A Boolean literal. | `(AND FALSE ...)` |
| `STRING`, `Int`, `Nat`, `BOOLEAN` | The predefined set; the IR prints `TlaStrSet`, `TlaIntSet`, `TlaNatSet`, `TlaBoolSet`. | `STRING` |
| identifier | A name with exactly this spelling. | `(MOD step 0)` |
| `(OPER p1 … pn)` | An application of `OPER` to exactly `n` arguments that match `p1 … pn` in order. | `(Sequences!Seq _)` |
| `(OPER p1 … pk ...)` | An application of `OPER` to at least `k` arguments, the first `k` matching `p1 … pk`. | `(SET_ENUM 0 ...)` |
| `(: p "T")` | An expression that matches `p` and whose type matches `T`. | `(: _ "Set(Str)")` |
| `(.. p)` | An expression that matches `p`, or that has a subexpression matching `p` at any depth. | `(EQUIV (.. (GLOBALLY _)) _)` |
| `(& p1 … pn)` | An expression that matches every `p1 … pn`. | `(& (CASE ...) (.. (GLOBALLY _)))` |

An identifier consists of letters, digits, `_`, `!` and `$`, and does not
start with a digit. In head position it names an operator, which must exist;
anywhere else it names a variable, parameter or definition. The words `_`,
`TRUE`, `FALSE`, `STRING`, `Int`, `Nat` and `BOOLEAN` are reserved, and `..`
and `&` are reserved as heads.

`(.. p)` searches operator arguments and the declarations and body of a `LET`,
skipping labels. It does not follow a name to the definition it refers to: a
temporal operator inside a referenced definition is matched where that
definition is walked, not through the reference. Combine `&` with `..` to
constrain an operator whose relevant argument position varies, such as a `CASE`
arm.

### 3.3. Types

The type in `(: p "T")` uses Apalache's type syntax, the same syntax as the
`type` fields printed by `print --apalache-ir`. Examples: `Int`, `Str`,
`Set(Seq(Int))`, `(Int -> Bool)`, `<<Int, Str>>`, `{ f: Int }`,
`Tag(Int) | Other(Str)`.

Lowercase type variables such as `a` and `b` act as wildcards:
- `"Set(a)"` matches any set type.
- `"(a -> a)"` matches functions whose domain and range have equal element
  types.
- A variable binds consistently within one alternative.
- A row ending in a type variable, as in `{ f: Int, a }` or `Tag(Int) | a`,
  matches records or variants that have the listed fields and any others.

Example: `(FiniteSets!Cardinality (: _ "Set(Seq(a))"))` matches the
cardinality of any set of sequences.

### 3.4. Matching rules

- **Evaluated code.** A pattern is tried at every subexpression of the code the
  tools evaluate:
  - `Init`, `Next`, `Inv`, `Spec`, `Prop` and `Liveness`;
  - every top-level definition they reference, such as generated operators and
    linked custom-library definitions;
  - every `LET` definition and lambda inside that code, referenced or not.

  A top-level definition that nothing references is skipped. A pattern is never
  anchored to the root.
- **Alternatives.** The alternatives in `match` are independent. Bindings do
  not carry from one alternative to another.
- **Metavariables.** Two occurrences of `?x` must match structurally equal
  expressions. Type annotations are not compared; use a type constraint for that.
  Inside `(.. p)`, the first subexpression in pre-order that matches `p`
  determines the bindings; the parts of `(& p1 … pn)` bind from left to right.
- **Arity.** An application matches only its exact argument count unless the
  pattern ends in `...`.
- **Labels.** Labels are skipped, just as `print --apalache-ir` omits them. A
  pattern cannot target a label.
- **Determinism.** Whether an entry matches depends only on its bytes, the
  corpus configuration and the database.

## 4. Writing a signature

1. **Start from a known defect.** Use a row of a triage report produced by
   [`script/triager.py`](../../script/triager.py), a document in `findings/`, or
   one in `conformance/`. Note the diagnostic the tool reports.
2. **Look at the IR.** Print a representative entry and find the construct the
   diagnostic names:

   ```sh
   bin/fuzztla print --corpus corpus12 --apalache-ir corpus12/03aggregator-fail/<sha256>.cbor
   ```

3. **Write the narrowest pattern** that captures a shape the tool rejects
   wherever it occurs. Keep the operands the defect depends on, and use `_` only
   for the rest. For example, write `(MOD _ 0)`, not `(MOD _ _)`.
4. **Check it** on entries that show the diagnostic, which must match, and on
   entries that do not, which should not:

   ```sh
   bin/fuzztla print --corpus corpus12 --known-defects signatures/known-defects.toml <entry>
   ```

   The report lists each matching signature, its references, and the
   subexpression it matched, rendered as TLA+.
5. **Document it.** Point `references` at the finding or conformance document,
   and write a one-sentence `description`.

Measure a signature before relying on it. It quarantines every input whose
evaluated code contains its shape. But whether a tool actually reaches the shape
can depend on the tool's evaluation order, so some quarantined inputs would have
passed. On a corpus sample, check two things:
- most entries it matches carry the referenced diagnostic;
- it catches most entries that carry that diagnostic.

A shape that rarely fails is not a signature. An example is `Head` of a sequence
that is empty only at run time. When the referenced defect is fixed, remove or
narrow the signature.

## 5. What happens on a match

Each check runs after a candidate has passed the richness threshold and the
request-frame limit. A matching candidate is not admitted to `00-inputs`:

- **Primary signature.** The first matching signature, in database order, is
  the candidate's primary signature.
- **Storage.** While fewer than `known_defect_samples` entries of that primary
  signature are stored, the candidate is written to
  `00-known-defects/<sha256>.cbor`. Otherwise it is only counted.
- **Recorded signatures.** A quarantined entry records every matching
  signature, primary first:

  ```cbor
  {
      "kind": "module",
      "input": h'0123af',
      "gen": {
        "cohort": 3,
        "richness": 4.0,
        "knownDefects": ["modulo-by-literal-zero", "string-set"]
      }
  }
  ```

- **No stage processing.** No stage processes `00-known-defects`, and its
  entries do not count towards `max_entries`. Generating a quarantined input
  again counts as a duplicate.
- **Inspection.** Inspect a quarantined entry like any other:
  `fuzztla print --corpus DIR --spec 00-known-defects/<sha256>.cbor`.
- **Cleanup.** You may delete the directory between runs. The per-signature
  caps then start again from zero.
- **Counts.** The run table reports the total number of known-defect
  rejections. `.workflow-stats.cbor` accumulates the counts across runs, keyed
  by primary signature:

  ```cbor
  "generator": {
    "attempts": 1500,
    "knownDefects": {"modulo-by-literal-zero": 120, "string-set": 45},
    ...
  }
  ```

## 6. Troubleshooting

- **The run stops with a database error.** The message names the file, the
  signature id, and the column within the pattern. Typical causes:
  - an unknown operator name (check the spelling against `print --apalache-ir`);
  - a type string Apalache cannot parse;
  - an id used twice across databases;
  - an empty `match` list;
  - an unknown key.
- **A signature never matches.** Common causes:
  - the argument count differs, which a trailing `...` fixes;
  - the pattern follows TLA+ notation rather than the printed IR, for example a
    lambda appears as a `LET` definition;
  - the pattern wraps a label, and labels are skipped.

  Use `print --known-defects` on an entry that should match.
- **"could not generate corpus entry … within 10000 attempts".** A signature is
  too broad and rejects nearly every candidate. The message reports how many of
  the target's attempts were known-defect rejections.

## 7. Shipped database

`signatures/known-defects.toml` holds signatures for the Apalache limitations
that dominated corpus12's checking time. They were measured on the 98,676
corpus12 entries that have an Apalache result:
- **Recall** is the share of entries failing with the referenced diagnostic
  that the signature matches.
- **Precision** is the share of its matches that Apalache failed on.

Together, the signatures match 41.5% of the entries, which took 67.9% of
corpus12's Apalache time.

| Id | Match | Defect | Recall | Precision |
| --- | --- | --- | ---: | ---: |
| `modulo-by-literal-zero` | `(MOD _ 0)` | [Apalache reaches modulo by zero](../../conformance/modulo-by-zero-apalache-fails.md) | 91.2% | 79.5% |
| `division-by-literal-zero` | `(DIV _ 0)` | [Apalache reaches division by zero](../../conformance/division-by-zero-apalache-fails.md) | 90.8% | 79.4% |
| `sequence-set` | `(Sequences!Seq _)` | [Apalache does not support `Seq(S)`](../../conformance/sequence-set-unsupported.md) | 100% | 82.5% |
| `string-set` | `STRING` | [Apalache does not support `STRING`](../../conformance/string-set-unsupported.md) | 100% | 82.0% |
| `zero-power-zero` | `(POW 0 0)` | [Apalache reaches `0 ^ 0`](../../conformance/zero-power-zero-apalache-fails.md) | 79.9% | 78.9% |
| `integer-literal-outside-tlc-range` | `2147483648`, `-2147483649`, `9223372036854775807` | [TLC rejects integers outside 32 bits](../../conformance/integer-outside-tlc-range.md) | not measured | not measured |

`integer-literal-outside-tlc-range` matches the out-of-range values of the
boundary literal table ([integer literals](integer-literals.md)). It was added
without a precision measurement: a quarantined entry is not checked, and TLC
rejects such a literal whenever it evaluates it. It matched 27 of 1,165
candidates in the ADR 0012 boundary corpus.

The database also holds signatures for temporal formulas that TLC cannot check
([TLC temporal formula limits](../../conformance/tlc-temporal-formula-limits.md)).
Here `T` stands for any temporal operator: `GLOBALLY`, `EVENTUALLY`, `LEADS_TO`,
`WEAK_FAIRNESS`, `STRONG_FAIRNESS`, and the exotic `GUARANTEES`, `TEMPORAL_EXISTS`
and `TEMPORAL_FORALL`; each such signature lists one alternative per operator.
They were measured on a 1600-module smoke corpus generated with every category
but `exotic` enabled. Recall is the share of TLC crashes with the diagnostic
that the temporal signatures together match; precision is the share of a
signature's matches on which TLC crashed with it.

| Id | Match | TLC diagnostic | Recall | Precision |
| --- | --- | --- | ---: | ---: |
| `tlc-temporal-equivalence` | `(& (EQUIV ...) (.. (T ...)))` | cannot handle | 13 of 13, together | 6 of 6 |
| `tlc-temporal-case` | `(& (CASE ...) (.. (T ...)))`, and `CASE_OTHER` | cannot handle | | 1 of 1 |
| `tlc-temporal-unbounded-quantifier` | `(& (FORALL2 ...) (.. (T ...)))`, and `EXISTS2` | cannot handle | | 7 of 7 |
| `tlc-eventually-action` | `(EVENTUALLY (NO_STUTTER ...))` | must be of forms | 16 of 16, together | 16 of 17 |
| `tlc-always-action-under-temporal` | `[][A]_v` under `EVENTUALLY`, `LEADS_TO` or `GLOBALLY` | must be of forms | | 2 of 2 |
| `tlc-always-action-under-connective` | `[][A]_v` under `NOT`, `IMPLIES` or `OR` | must be of forms | 3 of 3 in corpus20, together | 3 of 4 |
| `tlc-fairness-under-eventuality` | `WEAK_FAIRNESS` or `STRONG_FAIRNESS` under `EVENTUALLY` or `LEADS_TO` | must be of forms | 3 of 3 in corpus20, together | 3 of 4 |

`tlc-eventually-action` also matches `[]<><<A>>_v`, which TLC checks; its
seventeenth match failed an evaluation before TLC reached the property.
`tlc-always-action-under-connective` also matches `<>[][A]_v` under a connective,
which TLC checks. The last two signatures were measured on corpus20, where the
earlier ones already quarantined their shapes, and on the smoke corpus; their
fourth matches failed an evaluation before TLC reached the property.

### 7.1. Recall-first database

`signatures/all-defects.toml` is for building a corpus fast. It quarantines every
candidate whose code matches a defect recorded in `findings/` or `conformance/`,
so that checking time goes only to candidates that can show new behavior.

It is a superset of `known-defects.toml`. Its first 13 signatures are the shipped
ones, in the same order, with the same ids, descriptions and patterns, so
primary signatures do not change. Use it *instead of* `known-defects.toml`;
listing both defines every shipped id twice, which is an error:

```toml
[workflow.inputs]
known_defects = ["../signatures/all-defects.toml"]
```

The added signatures follow one rule:
- **Uncommon operators.** When a defect depends on run-time values and the
  operator is uncommon in generated code, the signature matches every use of
  the operator. Examples are `Int`, `Nat`, `SUBSET`, `..`, `^`, `\div`,
  `ENABLED`, `WF` and `SF`.
- **Frequent operators.** When the operator is frequent, the signature matches
  only the literal shapes that fail whenever they are evaluated. Examples are
  `f[x]`, `CHOOSE`, `CASE`, `Head`, `Tail`, `SubSeq` and `%`.
- **Function sets.** `[S -> T]` stays admitted except in its failing special
  cases. A function set here is `[S -> T]` itself, or one `IF` branch, `\union`,
  `\intersect` or `\` operand of it. The special cases are:
  - `function-set-empty-component`: a literal empty domain or range, or a range
    computed by a fold;
  - `function-set-equality`: a function set compared with `=` or `#`;
  - `function-set-expansion`: a function set that is folded, mapped, filtered,
    counted, unioned by `UNION`, or the set of an applied `CHOOSE`.

The remaining signatures match fixed shapes:
- the TLC findings about temporal formulas;
- the SANY `%` level-error crash;
- the Apalache temporal and assignment findings;
- the open `PrettyWriter` findings. `printer-fold-in-left-operand` matches a fold
  that ends an operand, directly or one infix level down, followed by another
  operand.
- the Community Modules operators whose defects depend on argument values:
  `IsInjective`, `AntiFunction`, `IndexFirstSubSeq`, `LongestCommonPrefix`,
  `ExistsSurjection`, `SumBag` and `ProductBag`. A generated module applies a library operator
  through an alias named `Custom<hex(module)>N<hex(operator)>`, so a pattern
  names that alias as the first argument of `OPER_APP`, as in
  `(OPER_APP Custom42616773457874N53756d426167 _)` for `SumBag`.
- `variant-filter`: every `VariantFilter`, whose Apalache encoding fails on
  run-time payload values
  ([`apalache-bmc-022`](../../findings/apalache-bmc/apalache-bmc-022.md)).
- `set-map-duplicate-product`: a set map with at least two bound variables in a
  fold combinator, or in the first or second bound set of another such map.
  Apalache counts a mapped set by its tuples rather than its distinct values,
  so these shapes can exceed its product guard of 1,000,000
  ([set-map-product-guard](../../conformance/set-map-product-guard.md)).
- `recursion-insertion-sort`: every application of the recursion library's
  `SeqInsertionSort` or `SetToSortedSeq`, whose Apalache definitions nest
  `ApaFoldSeqLeft` and can exhaust the heap from about nine elements
  ([`apalache-performance-002`](../../findings/apalache-performance/apalache-performance-002.md)).

The database header and `AllDefectsTest` list the documents that no pattern can
express:
- A constant `FALSE` invariant or property: a pattern cannot be anchored to a
  definition.
- Labels in `EXCEPT` (`sany-002`): labels are skipped.
- The order sensitivity of a fold combinator, and TLC heap exhaustion in an
  applied `CHOOSE`: these depend on operand values.
- Fixed findings.

The test fails when a new finding or conformance document is neither
referenced by a signature nor listed as uncovered.

Measurements, taken on 2026-09-18:
- **corpus29 sample.** The database matched 73.3% of a sample of 600 corpus29
  entries. corpus29 was generated with `known-defects.toml`, so every entry in
  the sample had already passed the shipped signatures. The top matches by
  share:
  - `choose-literal-without-witness`, 57%: the generator often writes
    `CHOOSE x \in S : FALSE`;
  - `printer-fold-in-left-operand`, 48%;
  - `enabled`, 37%;
  - `power`, 33%;
  - `division`, 32%.
- **Function sets.** Of the 34 sampled entries that contain `[S -> T]`, 10 match a
  function-set signature. Every one of the 34 also matches another signature,
  so the total stays at 73.3%.
- **Fresh corpus with all categories enabled.** 313 of 373 candidates above the
  richness threshold were quarantined, and generation completed.
- **Remaining disagreements.** The admitted entries still produced 29
  aggregator disagreements:
  - 16 constant `FALSE` invariants or properties;
  - 13 function applications, `CASE`s and `CHOOSE`s that failed on run-time
    values.
