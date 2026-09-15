# 0009: Corpus database

**Authors:** Igor Konnov and Claude

**Status:** Proposed

**Date:** 2026-09-15

## Context

Every question about a corpus is currently answered by a one-off script over its
CBOR files. `script/triager.py` decodes aggregator envelopes with `cbor2`, and
the calibration of [ADR 0008][adr-0008] needed its own scripts. Corpus22 has
103,598 entry files in 506 MB. The quality gate that ADR 0008 defers will
query stored metrics across a whole corpus, and so will the calibration of that
gate.

Every copy of the envelope format in a script is another place where the format
can drift. Stage names (`corpus.CorpusStage`), verdicts (`CorpusVerdict`),
failure codes (`checker.CheckerFailureCode`) and metric names
(`checker.ExplorationCount`) are each defined once in Java, and
`CorpusEnvelopeCodec` already reads all of them.

This ADR covers the envelope metadata and the operators of each entry's
evaluated code, which the export recovers by replaying the input. It does not
cover other static IR features, re-matching known defects, rendered
specifications, crash stack traces or triage labels.

## Decision

### A SQLite file, rebuilt on every export

`fuzztla export-db [--corpus DIR] [-o FILE] [--force] [--no-lock]` writes one SQLite
database. The [manual][manual] documents the schema, which is the contract.

- **SQLite.** Analysis is local and has one user. A single file opens in the
  `sqlite3` shell, in Python's standard library and in Datasette, and needs no
  server. At 10<sup>5</sup> entries and 4·10<sup>5</sup> stage rows every
  query is small for SQLite.
- **Rebuild only.** The database is derived data and the corpus is the source of
  truth. An export always builds a new file, so there is no incremental sync, no
  change detection and no migration. The schema version is stored in `PRAGMA
  user_version`. A database with an old version is exported again.
- **Atomic replacement.** The export writes a temporary file in the target
  directory and renames it over the target only after it commits. The one
  transaction runs with `journal_mode = OFF` and `synchronous = OFF`: a crash
  leaves only a temporary file behind, so there is nothing to recover.

### Java, in a new `database` package

The exporter is Java code in `io.github.tlaplus.hardening.database`, which
imports `corpus`, `checker` and `common`. The layering becomes `checker`,
`gen` → `corpus` → `database` → `cli`, alongside `corpus` → `config` → `workflow`
→ `cli`. `config` and `workflow` do not import `database`, and `database` does
not import them. The replay that operator counting needs lives in `workflow`,
and `cli` passes it to `database` as an `InputAnalysis` (see below).

- `CorpusDatabaseSchema` derives the tables, the `verdictPair` view and the
  indexes from `DatabaseTable`, `DatabaseColumns`, `CorpusStage` and
  `ExplorationCount`. It adds one `stage` column per `ExplorationCount`, named by
  its field name. A new metric is therefore a new column with no exporter change,
  and a schema version increment.
- `EntryRows` maps a decoded `CorpusEnvelope` to rows. A row sets values by
  column and the column checks the value's type, so the order of bound
  parameters is not maintained by hand.
- `CorpusDatabaseWriter` owns the JDBC connection, the prepared statements and
  the transaction. `CorpusExport` lists entries, decodes them, takes the lock and
  replaces the file.
- `corpus` gains a listing, `CorpusDirectory.storedEntries()`, that returns every
  entry file of every entry directory without decoding it. Interpreting the bytes
  stays with the caller.
- The JDBC driver is `org.xerial:sqlite-jdbc`, which bundles SQLite and native
  libraries for the common platforms. The shaded jar declares
  `Enable-Native-Access: ALL-UNNAMED`, so Java 25 does not warn when the driver
  loads its library.

### Schema choices

- **`entry` keyed by a row id, unique on `(hash, directory)`.** An input can be
  in two directories at once, for example `02tlc-pass` and `02apa-pass` before
  aggregation. Each copy has its own stage records, so the hash alone is not a
  key. Putting `hash` first lets the unique index serve lookups by hash, such as
  joins with triage reports. The other tables have composite keys and are stored
  `WITHOUT ROWID`, which avoids a second B-tree per table.
- **Wide metric columns.** A long table `(entryId, stage, name, value)` would
  need a pivot for every query that compares two metrics, and every ADR 0008
  pattern compares metrics. A metric that was not measured is `NULL`.
- **Names from the envelope.** Tables and columns are camelCase, and a column
  that stores an envelope field has that field's name (`startTime`, `code`,
  `initStates`). A query then reads like the manual of the envelope.
- **Text timestamps with milliseconds.** ISO-8601 in UTC with exactly three
  fraction digits sorts chronologically as text and works with SQLite's date
  functions. `durationMillis` is stored as well, because nearly every timing
  query needs it.
- **Unreadable entries are rows.** An entry that does not decode goes into
  `unreadable` with the decoder's diagnostic, and the export continues. A
  damaged file therefore does not block analysis of the rest, and remains
  visible. Listing or I/O errors still fail the export.

### Operators of the evaluated code

The export replays every entry's input through the generator and stores, per
entry, how often each operator, `LET-IN` form and kind of literal occurs in the
code the checkers evaluate.
This is not optional: a database without operators cannot answer the questions
it exists for, and replay costs minutes, not hours.

- **What is counted.** The walk is `signature.IrTree.evaluatedSubexpressions`
  from `FuzzInputModule.ENTRY_POINTS`, the walk that known-defect signatures
  ([ADR 0006][adr-0006]) match against. It follows referenced top-level
  definitions, walks every `LET` definition and treats labels as transparent.
  `signature.IrOperatorCounts` counts the constructs of the walk under their
  names in Apalache's IR JSON:
  - an operator application (`OperEx`) by `TlaOper.name()`, the `oper` field,
    which is also the name a signature pattern uses;
  - a `LET-IN` by its `kind`, `LetInEx`;
  - a literal (`ValEx`) by the `kind` of its value, such as `TlaInt` or
    `TlaNatSet`.

  `EvaluatedOperatorsTest` checks every counted name against the IR JSON of
  generated modules. `LET-IN` and literals are counted because the checkers treat
  them as constructs of their own: in corpus22, 90% of entries evaluate a
  `LET-IN`, 1.96 million in total. Names (`NameEx`) are not counted. The size of
  the walk is stored as `entry.evaluatedNodes`, one of the static features
  ADR 0008 derives.
- **Replay settings.** `cli` reads the corpus's `config.toml`, prepares
  `SpecDecoders` and checks the custom operator library with
  `LibraryManifest.verify`, as `fuzztla print --corpus` does. Replay records no
  generator-crash artifact: the export does not write to the corpus. A replay
  that throws a `RuntimeException` or `StackOverflowError` sets
  `entry.replayError`, and the entry has no operator rows.
- **Layering.** `database.InputAnalysis` is a functional interface from
  `CorpusInput` to `InputFeatures`, a node count and operator counts.
  `workflow.spec.EvaluatedOperators` decodes and counts, and `cli` adapts it to
  `InputAnalysis` with a lambda. `database` imports neither `workflow` nor
  `signature`.
- **Parallelism.** `EntryBatchExporter` processes the listing in chunks of 1,024
  entries. It reads and decodes the envelopes of a chunk on the calling thread,
  replays the inputs with `ExecutorService.invokeAll` on `--max-cpus` threads,
  and inserts the rows in listing order on the calling thread. Row ids and
  contents therefore do not depend on the thread count, memory is bounded by one
  chunk, and SQLite is only used from one thread. The workflow stages already
  share one `SpecDecoders` between worker threads, and `InputAnalysis` documents
  that it is called concurrently. Interruption, such as Ctrl-C through the
  shutdown hook, cancels the pending replays and deletes the temporary file.

### Locking

By default the export takes the corpus's exclusive lock, so a run and an export
never interleave. `--no-lock` exports a corpus that a run is using. The result is
then not a snapshot: an entry that moves between directories while they are
listed can appear twice or be missing. An entry that disappears before it is read
is counted as vanished and skipped. The export never changes a corpus entry.

## Alternatives considered

- **Python with `cbor2`.** Its tooling suits analysis, and `triager.py` already
  uses it. But it would copy stage names, verdicts, failure codes, metric names
  and the stage-record shape, and CLAUDE.md forbids a second copy of these.
  Future columns that decode the IR, such as the static features of ADR 0008,
  are Java-only anyway.
- **DuckDB.** Its column store and Parquet support pay off at 10<sup>8</sup>
  rows. At 10<sup>5</sup> rows SQLite is as fast, and it is already installed
  and more widely supported.
- **JSON Lines from Java, loaded with `sqlite3 .import`.** This avoids the JDBC
  dependency but leaves the DDL, types and keys outside the build. The two steps
  can also disagree about the columns.
- **Incremental export.** Would need change detection over a corpus whose entries
  move between directories. A full export is cheap enough (see Consequences).
- **Count operators in every definition.** Simpler, but a module links operator
  library definitions that nothing references. In a probe over corpus23 that
  walked every definition, `Variants!Variant` was the third most frequent
  operator. Those counts describe the library, not the tested code.
- **Parse `fuzztla print --apalache-ir` output.** Needs a JVM start per entry
  and a second reader of the IR JSON.
- **An opt-in flag for replay.** Keeps the metadata export at seconds, but makes
  the schema's contents depend on a flag, and every analysis needs operators.

## Consequences

- **Cost.** Replay dominates. A single-threaded probe over corpus22 decoded
  103,598 inputs in 124 s and walked their modules in 3 s, without a failure.
  The export of corpus22 takes 35 s on 8 threads (213 s of CPU, peak resident
  set 1.8 GB). On one thread it took 135 s (680 MB), measured before `LET-IN` and
  literals were counted, and wrote identical tables. The database has 165 MB, of which `operator` (4.0 million rows) takes
  84 MB. An index on `operator(name)` would add about 60 MB and save at most
  0.1 s per query, so there is none. Without replay, the
  metadata alone exports in 23 s from a cold page cache and 4 s from a warm one;
  its `stage` table takes 45 MB, `entry` 11 MB, the unique index of `entry` 10 MB
  and the `stage(stage, verdict)` index 8 MB.
- **Jar size.** `sqlite-jdbc` adds 11.4 MB to `target/fuzztla.jar`, which grows
  to 50 MB. `slf4j-nop` is added too: SLF4J was already on the class path
  through the Apalache facade, and binding its no-op provider explicitly keeps
  its no-op behavior without the missing-provider warning.
- **Schema discipline.** Every change to a table, column or view increments
  `CorpusDatabaseSchema.VERSION` and updates the manual in the same change.
  `CorpusDatabaseSchemaTest` fails when the manual's column tables and the
  generated schema differ.
- **Open questions.**
  - Whether the quality gate reads this database or the envelopes directly.
  - Whether triage labels, further static IR features or crash stack traces become
    tables.

[adr-0006]: 0006-known-defect-signatures.md
[adr-0008]: 0008-exploration-metrics.md
[manual]: ../manual/corpus-database.md
