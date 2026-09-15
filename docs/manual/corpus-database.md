# Corpus database

> **Status:** Implemented.
> [ADR 0009](../decisions/0009-corpus-database.md) records the design and its
> rationale.

`fuzztla export-db` turns a corpus into one SQLite file. After that, questions
such as "which agreeing entries are shallow" or "failure codes by cohort" are SQL
queries instead of scripts over thousands of CBOR files.

The database is derived data. Every export builds a new file from the corpus,
and no command updates it or reads it back. When the corpus changes, or when a
new build changes the schema version, export again.

## 1. Exporting

```sh
fuzztla export-db --corpus corpus23                     # writes corpus23/corpus.sqlite
fuzztla export-db --corpus corpus23 -o /tmp/c23.sqlite  # another location
fuzztla export-db --corpus corpus23 --force             # replace an existing file
fuzztla export-db --corpus corpus23 --no-lock           # export while a run uses the corpus
```

- `--corpus DIR`: the corpus directory. The default is `corpus`, as for `init`
  and `run`.
- `-o`, `--output FILE`: the database file. The default is `corpus.sqlite` in
  the corpus root.
- `--force`: replace `FILE` if it exists. Without it the command refuses and
  leaves the file unchanged.
- `--no-lock`: do not take the corpus lock. By default the export takes the same
  exclusive lock as `fuzztla run`, so it fails while a run holds the lock and a
  run cannot start during an export. With `--no-lock`, the export reads a corpus
  that a run keeps changing. An entry that moves while the export lists its
  directories may appear in two directories or in none, and an entry that
  disappears before it is read is counted as vanished and skipped.

The export writes a temporary file next to `FILE` and renames it to `FILE` only
after it succeeds, so a failed export leaves no partial database. On success it
prints one line, for example:

```text
exported 1344 entries (0 unreadable, 0 vanished) to corpus23/corpus.sqlite
```

The export reads every entry directory of the [corpus layout][storage], from
`00-inputs` and `00-known-defects` to `03aggregator-fail`. It does not read
`.stacktrace` sidecars, `.work/generator-crash`, or `.workflow-stats.cbor`. It
does not decode the IR in an entry's input.

## 2. Schema

`PRAGMA user_version` holds the schema version, currently `1`. Any change to a
table, column or view increments it. Old databases are not migrated; export them
again.

Table and column names are camelCase. A column that stores an envelope field has
that field's name.

Types are SQLite storage classes. A column marked *null* holds `NULL` when the
envelope does not record its value. Timestamps are ISO-8601 text in UTC with
milliseconds (`2026-09-15T12:40:03.000Z`). The fixed width makes text order
chronological, and SQLite's date functions accept the format.

### 2.1. `export`

One row per property of the export.

| Column | Type | Null | Meaning | Source |
| --- | --- | --- | --- | --- |
| `key` | TEXT | no | `corpus`, `exportedAt` or `fuzztlaVersion` | – |
| `value` | TEXT | no | The corpus's absolute path, the export start time, or the `fuzztla --version` string | – |

### 2.2. `entry`

One row per entry file, keyed by `id`. `(hash, directory)` is unique: one input
can sit in several directories at once, for example `02tlc-pass` and `02apa-pass`
while the other checker is still running, and each copy has its own stage
records.

| Column | Type | Null | Meaning | Source |
| --- | --- | --- | --- | --- |
| `id` | INTEGER | no | Row id, referenced by `stage` and `knownDefect` | – |
| `directory` | TEXT | no | Directory name, such as `03aggregator-pass` | file path |
| `hash` | TEXT | no | SHA-256 of the input bytes, from the file name | file name |
| `kind` | TEXT | no | `expr` or `module` | `kind` |
| `inputBytes` | INTEGER | no | Size of the generator input | `input` |
| `cohort` | INTEGER | yes | Richness cohort of the admission | `gen.cohort` |
| `richness` | REAL | yes | Richness score of the admission | `gen.richness` |

### 2.3. `knownDefect`

One row per known-defect signature an entry matched when it was generated. The
key is `(entryId, position)`. Signatures are not matched again during export.

| Column | Type | Null | Meaning | Source |
| --- | --- | --- | --- | --- |
| `entryId` | INTEGER | no | `entry.id` | – |
| `position` | INTEGER | no | Position in the list; 0 is the primary signature | `gen.knownDefects` |
| `signature` | TEXT | no | Signature id | `gen.knownDefects` |

### 2.4. `stage`

One row per stage record of an entry. The key is `(entryId, stage)`. An entry in
`03aggregator-pass` has four rows: `parser`, `tlc`, `apalache` and `aggregator`.
A stage that this build does not know still gets a row if its record has a
verdict and both times.

| Column | Type | Null | Meaning | Source |
| --- | --- | --- | --- | --- |
| `entryId` | INTEGER | no | `entry.id` | – |
| `stage` | TEXT | no | `parser`, `tlc`, `apalache` or `aggregator` | key in `stages` |
| `verdict` | TEXT | no | `pass`, `counterexample`, `fail` or `crashed` | `stages.<stage>.verdict` |
| `startTime` | TEXT | no | When the stage started | `stages.<stage>.startTime` |
| `endTime` | TEXT | no | When the stage finished | `stages.<stage>.endTime` |
| `durationMillis` | INTEGER | no | `endTime` minus `startTime`; stages record whole seconds | derived |
| `code` | INTEGER | yes | Checker failure code: 75, 120 or 150 ([ADR 0003][adr-0003]) | `stages.<stage>.code` |
| `detail` | TEXT | yes | One-line failure detail, at most 80 characters | `stages.<stage>.detail` |
| `phase` | TEXT | yes | `init`, `explore` or `complete` | `stages.<stage>.metrics.phase` |
| `saturated` | INTEGER | yes | 1 if a value walk hit its node cap, 0 if not; `NULL` without metrics | `stages.<stage>.metrics.saturated` |
| `initStates` | INTEGER | yes | Distinct initial states | `stages.<stage>.metrics.initStates` |
| `distinctStates` | INTEGER | yes | Distinct reachable states | `stages.<stage>.metrics.distinctStates` |
| `generatedStates` | INTEGER | yes | Generated states, duplicates included | `stages.<stage>.metrics.generatedStates` |
| `projectedStates` | INTEGER | yes | Distinct states without the step variable | `stages.<stage>.metrics.projectedStates` |
| `depth` | INTEGER | yes | Largest depth of a new state | `stages.<stage>.metrics.depth` |
| `projectedDepth` | INTEGER | yes | Largest depth of a new projected state | `stages.<stage>.metrics.projectedDepth` |
| `actions` | INTEGER | yes | Sub-actions of the next-state action | `stages.<stage>.metrics.actions` |
| `actionsFired` | INTEGER | yes | Actions that produced a transition | `stages.<stage>.metrics.actionsFired` |
| `actionsDiscovering` | INTEGER | yes | Actions that produced a new state | `stages.<stage>.metrics.actionsDiscovering` |
| `maxStateNodes` | INTEGER | yes | Largest value node count of a state | `stages.<stage>.metrics.maxStateNodes` |
| `maxCardinality` | INTEGER | yes | Largest collection in a state | `stages.<stage>.metrics.maxCardinality` |
| `maxNesting` | INTEGER | yes | Deepest value nesting in a state | `stages.<stage>.metrics.maxNesting` |
| `traceLength` | INTEGER | yes | Transitions in the counterexample | `stages.<stage>.metrics.traceLength` |

The metric columns, from `initStates` to `traceLength`, follow section 2 of the
[exploration-metrics manual][metrics], which defines each metric exactly. A
metric the checker did not measure is `NULL`, not 0. Entries checked before
exploration metrics existed have `NULL` in every metric column.

### 2.5. `unreadable`

One row per entry file that does not decode as a corpus envelope. The export
continues past such files. The key is `(directory, hash)`.

| Column | Type | Null | Meaning | Source |
| --- | --- | --- | --- | --- |
| `directory` | TEXT | no | Directory name | file path |
| `hash` | TEXT | no | Digest from the file name | file name |
| `error` | TEXT | no | The decoder's diagnostic | – |

### 2.6. `verdictPair`

A view with one row per entry that has an `aggregator` stage record. It puts the
two checkers' results side by side.

| Column | Type | Null | Meaning | Source |
| --- | --- | --- | --- | --- |
| `entryId` | INTEGER | no | `entry.id` | – |
| `hash` | TEXT | no | `entry.hash` | – |
| `cohort` | INTEGER | yes | `entry.cohort` | – |
| `aggregator` | TEXT | no | Aggregator verdict, `pass` or `fail` | `stage.verdict` |
| `tlc` | TEXT | no | TLC verdict | `stage.verdict` |
| `tlcCode` | INTEGER | yes | TLC failure code | `stage.code` |
| `tlcTraceLength` | INTEGER | yes | TLC counterexample length | `stage.traceLength` |
| `apalache` | TEXT | no | Apalache verdict | `stage.verdict` |
| `apalacheCode` | INTEGER | yes | Apalache failure code | `stage.code` |
| `apalacheTraceLength` | INTEGER | yes | Apalache counterexample length | `stage.traceLength` |

### 2.7. Indexes

Besides the keys, `stage(stage, verdict)` is indexed. The unique key
`(hash, directory)` also serves lookups by `hash`. Every table except `entry` is
stored `WITHOUT ROWID`.

## 3. Example queries

Entries per directory:

```sql
SELECT directory, count(*) FROM entry GROUP BY directory ORDER BY directory;
```

Verdict pairs of aggregated entries:

```sql
SELECT tlc, apalache, count(*) AS entries
FROM verdictPair GROUP BY tlc, apalache ORDER BY entries DESC;
```

The shallow patterns of [ADR 0008][adr-0008] over agreeing entries, each entry
assigned the first pattern that matches. Entries checked before exploration
metrics existed count as `no metrics`:

```sql
SELECT CASE
         WHEN t.phase IS NULL THEN 'no metrics'
         WHEN t.verdict = 'pass' AND t.initStates = 0 THEN 'vacuous pass'
         WHEN t.verdict = 'counterexample' AND t.phase = 'init' THEN 'initial-state violation'
         WHEN t.verdict = 'fail' AND t.phase = 'init' THEN 'early failure'
         WHEN t.actionsDiscovering = 0 THEN 'no discovering action'
         WHEN t.projectedStates <= 1 THEN 'counter-only progress'
         ELSE 'none of the above'
       END AS pattern,
       count(*) AS entries
FROM entry e
JOIN stage t ON t.entryId = e.id AND t.stage = 'tlc'
WHERE e.directory = '03aggregator-pass'
GROUP BY pattern ORDER BY entries DESC;
```

Apalache failure details by cohort:

```sql
SELECT e.cohort, s.code, s.detail, count(*) AS entries
FROM entry e JOIN stage s ON s.entryId = e.id
WHERE s.stage = 'apalache' AND s.verdict = 'fail'
GROUP BY e.cohort, s.code, s.detail ORDER BY entries DESC LIMIT 20;
```

TLC time per verdict:

```sql
SELECT verdict, count(*) AS entries,
       round(avg(durationMillis)) AS meanMillis, max(durationMillis) AS maxMillis
FROM stage WHERE stage = 'tlc' GROUP BY verdict;
```

Join a triage report of `script/triager.py` after importing it with the
`sqlite3` shell:

```sql
.import --csv corpus22/03aggregator-fail-triage.csv triage
SELECT t.*, p.tlc, p.apalache FROM triage t JOIN verdictPair p ON p.hash = t.entry_hash;
```

The column names of the imported table come from the CSV header.

[storage]: ../architecture/fuzzing-workflows.md#23-corpus-storage
[metrics]: exploration-metrics.md#2-metric-reference
[adr-0003]: ../decisions/0003-checker-failure-codes.md
[adr-0008]: ../decisions/0008-exploration-metrics.md
