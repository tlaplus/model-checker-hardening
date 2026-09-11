# 0006: Known-defect signatures

**Authors:** Igor Konnov and Claude

**Status:** Accepted

**Date:** 2026-09-11

## Context

Commit 139a2d9 made generated labels declare the formal parameters that TLA+
requires. Before that commit, 57% of corpus10's module inputs failed SANY in
about 0.1 s and never reached a model checker. Most of them were large modules,
because a large module almost always places a label under a binder. In corpus12,
101,079 of 101,241 inputs pass the parser, so Apalache now checks the large
modules that SANY used to reject. In a sample of 296 rendered corpus12 modules,
Apalache time grew with module size and did not depend on whether the module
contained labels.

Apalache is now the bottleneck. Over 36,661 s of wall-clock time, corpus12's
Apalache stage accumulated 582,510 s of worker time. That is 15.9 of its 16
workers busy on average, against 230,079 s for TLC and 10,691 s for the parser.
This caps throughput at about 240,000 entries per day. Before 139a2d9, the same
setup produced about 1M.

Apalache time in corpus12, by outcome:

| Apalache outcome | Entries | Share of Apalache time | Median |
| --- | ---: | ---: | ---: |
| `pass` | 22,908 | 19.5% | 3 s |
| `counterexample` | 41,042 | 18.7% | 1 s |
| `fail`: Mod by zero | 9,551 | 14.8% | 7 s |
| `fail`: Division by zero | 9,302 | 14.5% | 7 s |
| `fail`: `Seq(_)` unsupported | 6,694 | 11.3% | 8 s |
| `crashed` (1,566 of them timeouts) | 1,963 | 8.7% | 31 s |
| `fail`: `STRING` unsupported | 4,068 | 6.9% | 8 s |
| `fail`: `0 ^ 0` is undefined | 3,148 | 5.5% | 9 s |

The five `fail` groups take 53% of Apalache time. Each one is a documented and
deterministic Apalache limitation ([modulo][mod], [division][div], [`Seq`][seq],
[`STRING`][string], [`0 ^ 0`][pow]). Each is triggered by a syntactic shape that
is already present in the generated IR:
- Apalache rejects `Seq(S)` and `STRING` wherever they occur.
- Its preprocessing reports modulo and division by zero, and `0 ^ 0`, before it
  eliminates unreachable branches.
- In 13,242 of the 18,853 division and modulo failures, the reported divisor is
  the literal `0`. The remaining details are truncated.

Existing mechanisms act at the wrong granularity or too late:

- `generator.ignore` excludes whole expression categories. Excluding a
  literal-zero divisor this way means excluding all of `arithmetic`.
- A decoder can withdraw a form, as it does for labels inside `EXCEPT`
  ([sany-002]). That hard-codes a tool defect in the byte decoder, reinterprets
  stored inputs, and needs another code change once the tool is fixed.
- [`script/triager.py`][triager] classifies results by their tool diagnostics,
  so it runs only after the tools have spent the time.

## Decision

The input stage consults an optional database of *known-defect signatures*,
which are patterns over the generated Apalache IR. A candidate that matches is
quarantined instead of admitted, so SANY, TLC and Apalache never see it. Without
a database, admission is unchanged. The [manual][manual] specifies the file
format and the pattern language. This section records the design decisions.

### Database

`[workflow.inputs] known_defects` lists database files. Relative paths resolve
against the configuration file's directory. The empty list disables the
feature. `fuzztla init` enables the shipped database (see *Default database*).
Databases are concatenated in the listed order.
Each is a TOML file with one `[[signature]]` table per signature, which has:

- `id`: a stable name, unique across all configured databases;
- `references`: the finding or conformance documents that record the defect;
- `description`: one sentence saying what the tool does with the shape;
- `match`: one or more pattern alternatives.

The workflow reads the databases once, when a run starts. An invalid database
stops the run before the corpus is locked. Stored entries are not re-checked
when a database changes.

### Default database

*Revision.* This section revises the original decision, under which the
default was the empty list.

`fuzztla init` writes `known_defects` naming the repository's
`signatures/known-defects.toml`:
- for a corpus inside the repository, relative to the corpus, for example
  `["../signatures/known-defects.toml"]` for a corpus in the repository root,
  so that the corpus keeps working when the repository moves;
- for a corpus elsewhere, as an absolute path.

`init` needs the project directory to find the database:
- `bin/fuzztla` runs a snapshot of the JAR from a temporary directory, so it
  passes the project directory as the system property `fuzztla.home`.
- Without the property, the JAR runs in place as `target/fuzztla.jar`, and the
  project is the parent of the JAR's directory.
- If no database is found there, `init` writes the empty list and says so on
  standard error.

`FuzzTlaConfig.defaults()` still configures no database: only the command knows
where the repository is. Existing corpora keep the setting their
`config.toml` records.

### Pattern language

Patterns are S-expressions over the vocabulary that `fuzztla print
--apalache-ir` prints:
- operator names, as in the JSON `oper` field, for example `MOD`, `DIV`,
  `Sequences!Seq`, `OPER_APP`;
- literals;
- the predefined sets `STRING`, `Int`, `Nat` and `BOOLEAN`;
- names.

The wildcard `_` matches any expression. A metavariable `?x` matches any
expression, but all occurrences of it must match equal expressions. A trailing
`...` matches any remaining arguments. A type constraint `(: p "T")` also
requires the expression's type to match `T`. `T` is written in Apalache's type
syntax, and its type variables act as wildcards. In-process `TlaOper.name()`
equals the JSON `oper` field, so a pattern names the same operator a user sees
in the printed IR. Unknown operator names are load errors, so a typo cannot
produce a signature that silently never matches.

A signature matches a candidate when one of its alternatives matches a
subexpression of the code the tools evaluate:
- the entry points `Init`, `Next`, `Inv` and `Bound`;
- every top-level definition they reference, transitively, including linked
  library definitions;
- every `LET` definition inside that code, referenced or not, lambdas included.

A top-level definition that nothing references is skipped. The measurement
below explains the asymmetry. Labels are transparent to matching, as they are to
the checkers and to `print --apalache-ir`. Matching is a deterministic function of
the input bytes, the configuration and the database.

### Admission and quarantine

The signature check is the last admission step, after the richness threshold
([ADR 0002]) and the worker request-frame limit. A signature count therefore
means "would otherwise have been admitted". The check consumes no generator
randomness, so the candidate stream is the same with or without a database.

A matching candidate is stored in `00-known-defects/<sha256>.cbor`:
- Its `gen` map records every matching signature id, in database order, under
  `knownDefects`. The first id is the entry's *primary* signature.
- At most `[workflow.inputs] known_defect_samples` entries are stored per
  primary signature. The default is 100. Beyond that cap, candidates are counted
  and discarded.
- The directory is created lazily. It is not a stage: it feeds no queue and
  counts towards no capacity limit. It does take part in digest identity, so
  generating a quarantined input again yields a duplicate.

`.workflow-stats.cbor` gains `generator.knownDefects`, a map from primary
signature id to the number of matching candidates, stored or discarded. It is
omitted while no candidate has matched, so a run without signatures writes the
document it wrote before. The run table prints the total and the per-signature
counts.

`fuzztla print --known-defects FILE` reports the signatures that match one
stored entry and the subexpression each one matched. It is the tool for
developing and checking a signature.

### Signature policy

A signature must reference a filed finding or conformance document. Whether
Apalache reaches a shape can depend on its evaluation order, so a syntactic
signature is not exact. A signature is therefore measured on a corpus before it
ships:
- **precision:** how many of its matches the tool failed;
- **recall:** how many of the failures with its diagnostic it matches.

A shape that rarely fails, such as `Head` of a sequence that is empty only at
run time, is not a signature. When the referenced defect is fixed, the signature
is removed or narrowed.

The repository ships `signatures/known-defects.toml` with these signatures:

| Id | Match | Reference |
| --- | --- | --- |
| `modulo-by-literal-zero` | `(MOD _ 0)` | [modulo][mod] |
| `division-by-literal-zero` | `(DIV _ 0)` | [division][div] |
| `zero-power-zero` | `(POW 0 0)` | [`0 ^ 0`][pow] |
| `sequence-set` | `(Sequences!Seq _)` | [`Seq`][seq] |
| `string-set` | `STRING` | [`STRING`][string] |

### Measured precision

Every corpus12 entry was decoded and matched against the shipped database, then
compared with the Apalache verdict that corpus12 recorded for it. The scan
covered 98,676 entries that have an Apalache result. Three scopes of matching
were measured:

| Code a pattern is tried against | Entries matched | Recall per signature | Matched, but Apalache did not fail | Apalache time covered |
| --- | ---: | ---: | ---: | ---: |
| Every definition of the module | 49.0% | 81–100% | 16,659 (16.9% of entries) | 78.1% |
| Referenced top-level definitions, every `LET` inside them | 41.5% | 80–100% | 9,591 (9.7% of entries) | 67.9% |
| Referenced top-level and `LET` definitions only | 20.0% | 20–42% | 3,717 (3.8% of entries) | 34.0% |

Skipping unreferenced top-level definitions removes 7,000 false positives and
costs almost no recall. Skipping unreferenced `LET` definitions as well removes
another 5,900, but it loses more than half of the failures the signatures
target. Apalache evidently reaches many `LET` definitions that the definition
body never applies. The middle scope is the one implemented. Its 9,591 lost
entries are 4,256 passes, 4,035 counterexamples, and 1,300 crashes.

### Implementation

- A new package `io.github.tlaplus.hardening.signature`, at the same layer as
  `gen`, which depends only on the Apalache IR and facade, tomlj and `common`:
  - the pattern AST and its parser;
  - an operator-name table built from `TlaOperators`, pinned by a test;
  - a one-way type matcher over `TlaType1`;
  - the database and its strict TOML reader.
- `config`:
  - `StageConfig` becomes `InputStageConfig`, which carries `known_defects` and
    `known_defect_samples`;
  - `ConfigValueType.CLASSPATH` becomes a general path-list type;
  - relative paths are resolved by the helper that `OperatorLibraryConfig`
    already uses.
- `corpus`:
  - `CorpusPath.KNOWN_DEFECTS`, a `LAZY` entry directory;
  - quarantine writes through `CorpusLayout.createAtomically`;
  - `GenerationMetadata.knownDefects` and `GeneratorAggregate.knownDefects`,
    with their codecs.
- `workflow`:
  - `InputAdmission` owns the ordered admission checks that `PbtStage` used
    to inline;
  - a `KnownDefectQuarantine` owns the per-signature caps;
  - `WorkflowRunner` loads the databases.
- `cli`: the `print --known-defects` option, a picocli-free report renderer,
  and new run-table lines.
- [fuzzing-workflows.md][workflows] describes the admission order, the new
  directory and the new metadata fields.

## Alternatives considered

- **Category exclusion.** Too coarse; see Context.
- **Decoder-level withdrawal.** This couples the byte encoding to tool defects;
  see Context.
- **Output-based triage only.** Accurate, but it runs after the cost is paid.
- **Patterns in TLA+ syntax, parsed by SANY.** Metavariables and free names
  would need a module context. Loading would need a SANY run. The IR that SANY
  imports would also have to be aligned with the generator's IR, which differs
  for labels and for lambdas rendered as `LET`. The generator already produces
  IR, so patterns address it directly.
- **Structured TOML patterns**, written as partial IR-JSON trees. These need no
  parser, but they are verbose and hard to read for nested shapes.
- **Discarding matches without storing them.** Cheaper, but it leaves no samples
  against which to audit a signature.

## Consequences

On corpus12, the shipped signatures match 41.5% of the entries, which took 67.9%
of Apalache's time. While Apalache remains the bottleneck, admitted-entry
throughput is therefore expected to rise by about 1.8×, that is,
(1 − 0.415) / (1 − 0.679).

An estimate made before the measurement predicted 1.24×, because it counted only
entries that failed with a targeted diagnostic. A match removes the whole
entry, however: its Apalache time goes too, whatever Apalache would have
reported. The gain on a new run is still to be measured.

Other consequences:
- **Coverage.** Quarantined inputs reach no tool. On corpus12, 9.7% of all
  entries would have been quarantined although Apalache did not fail on them.
  This is accepted in exchange for the throughput. Quarantined samples, and
  `print --known-defects`, allow such cases to be audited. The policy above
  requires revisiting a signature when its defect is fixed.
- **Replay.** Matching is deterministic, and quarantined entries keep their
  bytes, so an entry's classification can be reproduced and audited with
  `print --known-defects`.
- **Dependency.** Type constraints use Apalache's Scala `DefaultType1Parser`,
  which is present in the shaded jar but is not part of the Java facade. One
  adapter isolates it. If a facade upgrade removed it, only type constraints
  would break.
- **Formats.**
  - The configuration gains two required keys. The strict reader rejects
    existing `config.toml` files until both keys are added.
  - The corpus gains a lazy directory. Envelopes and statistics gain optional
    fields.
  - Existing corpora otherwise remain valid. Per repository policy, there is no
    migration.

[ADR 0002]: 0002-pbt-richness-score.md
[manual]: ../manual/known-defect-signatures.md
[workflows]: ../architecture/fuzzing-workflows.md
[triager]: ../../script/triager.py
[sany-002]: ../../findings/SANY/sany-002.md
[mod]: ../../conformance/modulo-by-zero-apalache-fails.md
[div]: ../../conformance/division-by-zero-apalache-fails.md
[seq]: ../../conformance/sequence-set-unsupported.md
[string]: ../../conformance/string-set-unsupported.md
[pow]: ../../conformance/zero-power-zero-apalache-fails.md
