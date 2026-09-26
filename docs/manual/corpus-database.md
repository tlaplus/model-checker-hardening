# Corpus database

> **Status:** Implemented.
> [ADR 0009](../decisions/0009-corpus-database.md) records the design and its
> rationale.

`fuzztla export-db` turns a corpus into one SQLite file. After that, questions
such as "which agreeing entries are shallow", "which mutation operators pay off", "failure codes by cohort" or "which
operators are overrepresented in disagreements" are SQL queries instead of scripts over
thousands of CBOR files.

The database is derived data. Every export builds a new file from the corpus,
and no command updates it or reads it back. When the corpus changes, or when a
new build changes the schema version, export again.

## 1. Exporting

```sh
fuzztla export-db --corpus corpus23                     # writes corpus23/corpus.sqlite
fuzztla export-db --corpus corpus23 -o /tmp/c23.sqlite  # another location
fuzztla export-db --corpus corpus23 --force             # replace an existing file
fuzztla export-db --corpus corpus23 --no-lock           # export while a run uses the corpus
fuzztla export-db --corpus corpus23 --max-cpus=4        # replay inputs on 4 threads
```

- `--corpus DIR`: the corpus directory. The default is `corpus`, as for `init`
  and `run`.
- `-o`, `--output FILE`: the database file. The default is `corpus.sqlite` in
  the corpus root.
- `--force`: replace `FILE` if it exists. Without it the command refuses and
  leaves the file unchanged, including a file created while the export runs.
- `--no-lock`: do not take the corpus lock. By default the export takes the same
  exclusive lock as `fuzztla run`, so it fails while a run holds the lock and a
  run cannot start during an export. With `--no-lock`, the export reads a corpus
  that a run keeps changing. An entry that moves while the export lists its
  directories may appear in two directories or in none, and an entry that
  disappears before it is read is counted as vanished and skipped.
- `--max-cpus N`: the number of threads that replay inputs. The default is all
  available processors, as for `run`.

The export writes a temporary file next to `FILE` and renames it to `FILE` only
after it succeeds, so a failed export leaves no partial database. On success it
prints one line, for example:

```text
exported 1344 entries (0 unreadable, 0 replay failures, 0 vanished) to corpus23/corpus.sqlite
```

The export reads every entry directory of the [corpus layout][storage], from
`00-inputs` and `00-known-defects` to `04quality-fail`. It does not read
`.stacktrace` sidecars, `.work/generator-crash`, or `.workflow-stats.cbor`.

Besides the envelope, the export replays every entry's input through the
generator, as `fuzztla print --corpus` does, and counts the operators, `LET-IN`
forms and literals of the resulting module (section 2.6). Replay uses the corpus's `config.toml` and
fails if the corpus's custom operator library has changed. Replay dominates the
export time: about 1.2 ms of CPU per entry, so a corpus of 100,000 entries takes
about two minutes on one thread. The export writes rows in the same order for
every `--max-cpus`, so the database does not depend on it.

## 2. Schema

`PRAGMA user_version` holds the schema version, currently `7`. Any change to a
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
| `id` | INTEGER | no | Row id, referenced by `stage`, `knownDefect`, `mutationOperator` and `expr` | – |
| `directory` | TEXT | no | Directory name, such as `03aggregator-pass` | file path |
| `hash` | TEXT | no | SHA-256 of the input bytes, from the file name | file name |
| `kind` | TEXT | no | `expr` or `module` | `kind` |
| `inputBytes` | INTEGER | no | Size of the generator input | `input` |
| `cohort` | INTEGER | yes | Richness cohort of the admission | `gen.cohort` |
| `richness` | REAL | yes | Richness score of the admission | `gen.richness` |
| `generation` | INTEGER | yes | Generation that admitted the entry ([ADR 0010][adr-0010]) | `gen.generation` |
| `parent` | TEXT | yes | For a mutant, the `hash` of the entry it was mutated from | `gen.parent` |
| `evaluatedNodes` | INTEGER | yes | Subexpressions the checkers evaluate (section 2.6); `NULL` when replay failed | replayed `input` |
| `replayError` | TEXT | yes | Why replaying the input failed; `NULL` when it succeeded | replayed `input` |

### 2.3. `knownDefect`

One row per known-defect signature an entry matched when it was generated. The
key is `(entryId, position)`. Signatures are not matched again during export.

| Column | Type | Null | Meaning | Source |
| --- | --- | --- | --- | --- |
| `entryId` | INTEGER | no | `entry.id` | – |
| `position` | INTEGER | no | Position in the list; 0 is the primary signature | `gen.knownDefects` |
| `signature` | TEXT | no | Signature id | `gen.knownDefects` |

### 2.4. `mutationOperator`

One row per mutation operator applied to a mutant, in the order applied. The key
is `(entryId, position)`. An entry that was not mutated has no rows.

| Column | Type | Null | Meaning | Source |
| --- | --- | --- | --- | --- |
| `entryId` | INTEGER | no | `entry.id` | – |
| `position` | INTEGER | no | Position in the list; 0 is the first edit | `gen.operators` |
| `operator` | TEXT | no | Operator name, such as `random_byte` or `splice` | `gen.operators` |

### 2.5. `stage`

One row per stage record of an entry. The key is `(entryId, stage)`. An entry in
`03aggregator-pass` has four rows: `parser`, `tlc`, `apalache` and `aggregator`;
an entry in `04quality-pass` or `04quality-fail` also has a `quality` row.
A stage that this build does not know still gets a row if its record has a
verdict and both times.

| Column | Type | Null | Meaning | Source |
| --- | --- | --- | --- | --- |
| `entryId` | INTEGER | no | `entry.id` | – |
| `stage` | TEXT | no | `parser`, `tlc`, `apalache`, `aggregator` or `quality` | key in `stages` |
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

### 2.6. `expr`

One row per construct that occurs in an entry's evaluated code: an operator
application, a `LET-IN`, or a literal. The key is `(entryId, name)`.

The evaluated code is what the checkers evaluate. The walk starts at the
definitions `Init`, `Next`, `Inv`, `Spec`, `Prop` and `Liveness` and follows every
reference to another top-level definition. A top-level definition that nothing
references is skipped. Inside reached code, every `LET` definition is walked.
Labels are transparent. This is the walk that
[known-defect signatures][signatures] match against.

`name` is the construct's name in the IR JSON of `fuzztla print --apalache-ir`:

- **Operator application** (`"kind": "OperEx"`): its `oper` field, for example
  `SET_ENUM`, `FUN_APP`, `OPER_APP` for an application of a user-defined
  operator, or `Sequences!Head` for a standard module operator.
- **`LET-IN`** (`"kind": "LetInEx"`): `LetInEx`.
- **Literal** (`"kind": "ValEx"`): the `kind` of its value. That is `TlaInt`,
  `TlaStr`, `TlaBool` or `TlaDecimal` for a scalar, and `TlaBoolSet`,
  `TlaStrSet`, `TlaIntSet`, `TlaNatSet` or `TlaRealSet` for a predefined set
  such as `BOOLEAN` or `Nat`.

Operator names are upper case or qualified with `!`, so they never collide with
the other two. Names (`NameEx`), such as variables and parameters, are not
counted; they count towards `entry.evaluatedNodes` only.

| Column | Type | Null | Meaning | Source |
| --- | --- | --- | --- | --- |
| `entryId` | INTEGER | no | `entry.id` | – |
| `name` | TEXT | no | Operator name, `LetInEx`, or literal value kind, as in Apalache's IR JSON | replayed `input` |
| `occurrences` | INTEGER | no | Occurrences of the construct in the evaluated code | replayed `input` |

### 2.7. `exprEdge`

One row per edge between constructs of an entry's evaluated code, as the walk
of section 2.6 visits it. An edge joins a construct to a construct that is its
immediate argument, or to the body of a `LET-IN`, once labels are removed.
Names are not constructs, so an edge never passes through a name: the body of
a referenced definition has no parent. The key is
`(entryId, parentName, childName)`. The quality gate's coverage features are
derived from these edges and from `expr`
([ADR 0013][adr-0013]).

| Column | Type | Null | Meaning | Source |
| --- | --- | --- | --- | --- |
| `entryId` | INTEGER | no | `entry.id` | – |
| `parentName` | TEXT | no | The enclosing construct, named as in `expr.name` | replayed `input` |
| `childName` | TEXT | no | The argument construct, named as in `expr.name` | replayed `input` |
| `occurrences` | INTEGER | no | Occurrences of the edge in the evaluated code | replayed `input` |

### 2.8. `unreadable`

One row per entry file that does not decode as a corpus envelope. The export
continues past such files. The key is `(directory, hash)`.

| Column | Type | Null | Meaning | Source |
| --- | --- | --- | --- | --- |
| `directory` | TEXT | no | Directory name | file path |
| `hash` | TEXT | no | Digest from the file name | file name |
| `error` | TEXT | no | The decoder's diagnostic | – |

### 2.9. `verdictPair`

A view with one row per entry that has an `aggregator` stage record. It puts the
two checkers' results side by side. A corpus that runs one checker
(`[workflow] checkers`) has null columns for the other.

| Column | Type | Null | Meaning | Source |
| --- | --- | --- | --- | --- |
| `entryId` | INTEGER | no | `entry.id` | – |
| `hash` | TEXT | no | `entry.hash` | – |
| `cohort` | INTEGER | yes | `entry.cohort` | – |
| `aggregator` | TEXT | no | Aggregator verdict, `pass` or `fail` | `stage.verdict` |
| `tlc` | TEXT | yes | TLC verdict; null when the corpus does not run TLC | `stage.verdict` |
| `tlcCode` | INTEGER | yes | TLC failure code | `stage.code` |
| `tlcTraceLength` | INTEGER | yes | TLC counterexample length | `stage.traceLength` |
| `apalache` | TEXT | yes | Apalache verdict; null when the corpus does not run Apalache | `stage.verdict` |
| `apalacheCode` | INTEGER | yes | Apalache failure code | `stage.code` |
| `apalacheTraceLength` | INTEGER | yes | Apalache counterexample length | `stage.traceLength` |

### 2.10. Indexes

Besides the keys, `stage(stage, verdict)` is indexed. `expr` has no index on
`name`: on corpus22 it would add 61 MB and save at most 0.1 s per query. The unique key
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
assigned the first pattern that matches. An agreeing entry sits in
`03aggregator-pass` until the quality gate moves it to `04quality-pass` or
`04quality-fail`, so agreement is selected by the aggregator's verdict rather
than by directory. Entries checked before exploration metrics existed count as
`no metrics`:

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
FROM verdictPair p
JOIN stage t ON t.entryId = p.entryId AND t.stage = 'tlc'
WHERE p.aggregator = 'pass'
GROUP BY pattern ORDER BY entries DESC;
```

Admissions, gate passes and agreement per generation ([ADR 0010][adr-0010]):

```sql
SELECT e.generation,
       count(*) AS entries,
       sum(e.parent IS NOT NULL) AS mutants,
       sum(p.aggregator = 'pass') AS agreeing,
       sum(e.directory = '04quality-pass') AS selected
FROM entry e LEFT JOIN verdictPair p ON p.entryId = e.id
WHERE e.directory NOT IN ('00-known-defects', '02apa-inputs', '02apa-pass',
                          '02apa-counterexample', '02apa-fail', '02apa-crash')
GROUP BY e.generation ORDER BY e.generation;
```

The yield of each mutation operator: how many of the mutants it took part in
the gate selected. A mutant with stacked edits counts once per operator:

```sql
SELECT m.operator,
       count(DISTINCT e.id) AS mutants,
       count(DISTINCT CASE WHEN e.directory = '04quality-pass' THEN e.id END) AS selected
FROM mutationOperator m JOIN entry e ON e.id = m.entryId
GROUP BY m.operator ORDER BY selected DESC;
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

Verdict pairs of the entries whose evaluated code applies `Sequences!Head`:

```sql
SELECT p.tlc, p.apalache, count(*) AS entries
FROM expr o JOIN verdictPair p ON p.entryId = o.entryId
WHERE o.name = 'Sequences!Head'
GROUP BY p.tlc, p.apalache ORDER BY entries DESC;
```

How much more often each operator occurs in entries that TLC passes and Apalache
fails than in all aggregated entries:

```sql
WITH total AS (SELECT count(*) AS n FROM verdictPair),
     subset AS (SELECT count(*) AS n FROM verdictPair WHERE tlc = 'pass' AND apalache = 'fail')
SELECT o.name,
       sum(p.tlc = 'pass' AND p.apalache = 'fail') AS inSubset,
       round((sum(p.tlc = 'pass' AND p.apalache = 'fail') * 1.0 / (SELECT n FROM subset))
             / (count(*) * 1.0 / (SELECT n FROM total)), 2) AS lift
FROM expr o JOIN verdictPair p ON p.entryId = o.entryId
GROUP BY o.name HAVING inSubset >= 5 ORDER BY lift DESC LIMIT 15;
```

Evaluated code size per verdict pair:

```sql
SELECT p.tlc, p.apalache, count(*) AS entries,
       round(avg(e.evaluatedNodes)) AS meanNodes, max(e.evaluatedNodes) AS maxNodes
FROM verdictPair p JOIN entry e ON e.id = p.entryId
GROUP BY p.tlc, p.apalache ORDER BY entries DESC;
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
[signatures]: known-defect-signatures.md
[adr-0003]: ../decisions/0003-checker-failure-codes.md
[adr-0008]: ../decisions/0008-exploration-metrics.md
[adr-0010]: ../decisions/0010-mutation.md
[adr-0013]: ../decisions/0013-behaviour-archive-and-operator-coverage.md
