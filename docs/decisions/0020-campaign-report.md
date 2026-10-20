# 0020: Campaign report

**Authors:** Igor Konnov and Claude

**Status:** Implemented

**Date:** 2026-10-20

**Implementation.** Branch `igor/campaign-report`.

## Context

A fuzzing campaign leaves its results in four places: the entry envelopes
(exported by `fuzztla export-db`, [ADR 0009][adr-0009]), the cumulative run
statistics in `.workflow-stats.cbor`, the run configuration in `config.toml`, and
the triage labels that `script/triager.py` derives from crash diagnostics and
aggregator failures. Answering "how did this campaign go" has meant running each
tool and writing ad-hoc SQL. The answers are needed after every campaign, so the
queries belong in one tool.

## Decision

### 1. A Python script, not a FuzzTLA command

`script/report.py <corpus>` writes `<corpus>/report.md` and puts its images, the
exported database and a copy of `config.toml` under `<corpus>/.report`. The report
has three parts: how the campaign ran (setup, time, stages, generator, entries),
what it found (verdicts, triage labels, known-defect signatures, export problems),
and the quality of its inputs. Each part closes with plots; the
[campaign-report manual][manual] lists them.

The report is a script because it plots with Matplotlib and reuses the triager,
which is Python. `fuzztla report` only prints the command to run, so the
subcommand list stays the entry point users look at.

### 2. Inputs

- **Entries** come from `export-db`, which the script runs with `--force` into
  `.report/corpus.sqlite`; `--db FILE` reuses an existing export. The script
  refuses a database whose `user_version` is not the schema version it reads.
- **Run statistics** come from `.workflow-stats.cbor`; without the file, the
  figures and rows that need it are skipped.
- **Triage labels** come from calling the triager's classifiers in-process. The
  labels go into a `TEMP` table `triage(hash, directory, label)` of the read-only
  connection, so SQL joins them with the export. The report writes no
  `*-triage.csv` into the corpus and does not modify the exported database.

### 3. Counting inputs once

An input can sit in several directories at once, for example `02tlc-crash` and
`02tlcn-pass`, and each copy repeats the earlier stage records (corpus-database
manual §2.2). The report counts stage records once per input and stage, and
assigns each checked input one outcome: selected (`04quality-pass`), agreeing,
disagreeing, or crashed.

### 4. Versions

The corpus records no tool versions or commits. The report states the FuzzTLA
commit, the tla2tools version and the Apalache release of the checkout that
generates it, and says that these need not be those of the run.

## Consequences

- **Schema coupling.** A schema change of the export needs a matching change of
  `script/campaign_report/queries.py`; the version check makes a stale report fail
  instead of misreporting.
- **Old corpora.** The report reads what the current `export-db` can export. A
  corpus that the current build cannot export, such as one created before TLC-n,
  needs an export by the build that ran it, passed with `--db`.
- **Cost.** On `corpus66` (304k entries, 831 MB database), the export takes
  2 minutes and the report 40 seconds.

## Alternatives considered

- **Render in Java.** FuzzTLA has no plotting library, and the triager's
  signatures would need a second implementation.
- **Read the triager's CSV files.** They are stale whenever the corpus or the
  signatures change after the triager ran; classifying again costs under a second.
- **Record versions in the corpus.** That is a change of the run, not of the
  report, and remains open.

[adr-0009]: 0009-corpus-database.md
[manual]: ../manual/campaign-report.md
