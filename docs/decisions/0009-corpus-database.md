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

This ADR covers the envelope metadata only. It does not cover static IR
features, re-matching known defects, rendered specifications, crash stack traces
or triage labels.

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
not import them.

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

## Consequences

- **Cost.** On corpus22, the export writes 103,598 entries and 399,922 stage
  rows into a 77 MB file. It takes 23 s when the entry files are not in the page
  cache and 4 s when they are, with a peak resident set of 470 MB. The `stage`
  table takes 45 MB, `entry` 11 MB, its unique index 10 MB and the
  `stage(stage, verdict)` index 8 MB. Corpus23 (1,344 entries) exports in under
  a second.
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
  - Whether triage labels, static IR features or crash stack traces become
    tables.

[adr-0008]: 0008-exploration-metrics.md
[manual]: ../manual/corpus-database.md
