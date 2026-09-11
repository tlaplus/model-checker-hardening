# Known-defect signatures

> **Status:** Implemented. [ADR 0006](../decisions/0006-known-defect-signatures.md)
> records the design and its rationale.

A known-defect signature describes a shape of generated TLA+ IR that a tool is
already known to reject. Examples are a literal-zero divisor, which Apalache
reports as `Mod by zero`, and the set `STRING`, which Apalache does not support.
When a signature database is configured, the input stage checks every candidate
against it. Matching candidates go to a quarantine directory instead of the
parser, so SANY, TLC and Apalache spend no time rediscovering filed defects.
Without a database, every candidate is admitted as before.

## 1. Enabling signatures

Two keys in the corpus's `config.toml` control the feature:

```toml
[workflow.inputs]
max_entries = 100000
# Known-defect signature databases, relative to this config file.
known_defects = ["../signatures/known-defects.toml"]
# Quarantined entries kept per signature in 00-known-defects.
known_defect_samples = 100
```

- `known_defects` lists database files. Relative paths resolve against the
  directory that contains `config.toml`. Several databases are concatenated in
  the listed order. The empty list turns the feature off.
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

An identifier consists of letters, digits, `_`, `!` and `$`, and does not
start with a digit. In head position it names an operator, which must exist;
anywhere else it names a variable, parameter or definition. The words `_`,
`TRUE`, `FALSE`, `STRING`, `Int`, `Nat` and `BOOLEAN` are reserved.

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
  - `Init`, `Next`, `Inv` and `Bound`;
  - every top-level definition they reference, such as generated operators and
    linked custom-library definitions;
  - every `LET` definition and lambda inside that code, referenced or not.

  A top-level definition that nothing references is skipped. A pattern is never
  anchored to the root.
- **Alternatives.** The alternatives in `match` are independent. Bindings do
  not carry from one alternative to another.
- **Metavariables.** Two occurrences of `?x` must match structurally equal
  expressions. Type annotations are not compared; use a type constraint for that.
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
- **Counts.** The run table reports the number of known-defect rejections, in
  total and per signature. `.workflow-stats.cbor` accumulates the same counts
  across runs, keyed by primary signature:

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
