# Campaign report

> **Status:** Implemented.
> [ADR 0020](../decisions/0020-campaign-report.md) records the design.

`script/report.py` turns a corpus into one Markdown report of the campaign: how
it ran, what it found, and how good its inputs are.

## 1. Running

```sh
python3 -m pip install -r script/requirements.txt   # once: cbor2, matplotlib
python3 script/report.py corpus66
python3 script/report.py corpus66 --db corpus66/.report/corpus.sqlite
```

`fuzztla report --corpus DIR` prints these commands.

- `CORPUS`: the corpus directory.
- `--db FILE`: use an existing `export-db` database instead of exporting again.
- `--no-lock`: export without the corpus lock while a run uses the corpus; the
  export is then not a consistent snapshot (corpus-database manual §1).
- `--max-cpus N`: threads of the export.
- `--fuzztla PATH`: the launcher, by default `bin/fuzztla` of this checkout.

Without `--db`, the script runs `fuzztla export-db --force` into
`.report/corpus.sqlite`, which takes about 2 minutes for 300,000 entries. The
rest takes under a minute. The script fails if the database's schema version is
not the one it reads, or if the triager rejects the corpus.

The checkers are those the entries record, so a corpus without a `.checkers`
file, which FuzzTLA writes only for a non-default checker set, is reported
correctly.

The [Corpus triage workflow](../../.github/workflows/triage.yml) writes the report
of its short campaign, checks that every section and embedded image exists, and
uploads `report.md` with its images as a workflow artifact.

## 2. Output

| Path | Content |
| --- | --- |
| `report.md` | The report |
| `.report/*.png` | One image per plot; a run removes the images of the previous one |
| `.report/config.toml` | Copy of the run configuration, linked from the report |
| `.report/corpus.sqlite` | The export, unless `--db` names another file |

The script writes nothing else into the corpus. In particular, it classifies
crashes and disagreements with the triager's signatures in memory and does not
write `*-triage.csv`.

## 3. Contents

**Summary.** Inputs admitted and aggregated, disagreements and crashed inputs per
triage class, the documented defects reproduced, the `NEW` and `STATE_COUNT`
entries to investigate, and candidates filtered by known-defect signatures.

**1. Campaign.**

- Setup: technique, checkers, versions, and selected configuration values.
- Time: wall-clock span and sessions. A pause of more than 15 minutes between
  stage records starts a new session. Run and worker time come from
  `.workflow-stats.cbor`.
- Stages: records per verdict, duration percentiles, timeouts and worker time.
- Generator: candidate outcomes and the origin of admitted inputs.
- Entries per directory.

**2. Findings.**

- Verdict pairs of aggregated inputs.
- Triage labels per crash and disagreement directory, linked to their finding or
  conformance document. Up to 25 `NEW` and `STATE_COUNT` entries per directory
  are listed with their checker results.
- Known-defect signatures: candidates matched, samples stored, references.
- Entries the export could not decode or replay.

Entries are cited by the first 8 hexadecimal digits of their hash.

**3. Input quality.** The plots below, and two collapsible tables:

- every construct, with the share of inputs whose evaluated code contains it,
  overall and per outcome;
- pairwise construct coverage: every unordered pair of constructs that occur
  together in the evaluated code of at least one checked input, with the number
  and the share of such inputs. The text gives how many of the possible pairs of
  the constructs seen occur, and names the uncovered pairs when there are at
  most 25.

The report groups checked inputs into four outcomes: *selected* (`04quality-pass`),
*agreeing, not selected* (`03aggregator-pass` and `04quality-fail`), *disagreeing*
(`03aggregator-fail`), and *crashed* (any crash directory). An input that sits in
several directories is counted once. Exploration metrics come from TLC, or from
TLC-n when the corpus does not run TLC.

## 4. Plots

| Plot | Section | Shows |
| --- | --- | --- |
| Throughput | 1 | Stage records over wall-clock time, cumulative and per minute |
| Generation funnel | 1 | Admitted, mutant, agreeing and selected inputs per generation; median distinct states of the selected inputs |
| Checker times | 1 | ECDF of stage durations per verdict, with the timeout |
| Discovery curve | 1 | Cumulative disagreements and crashed inputs per triage class against inputs parsed |
| Verdict pairs | 2 | Heatmap of verdicts for each pair of checkers |
| Triage labels | 2 | Entries per triage label and directory |
| Known-defect signatures | 2 | The 20 signatures that matched the most candidates |
| Verdicts and failure codes | 2 | Share of each stage's records by verdict and failure code |
| Input size | 3 | Input bytes and evaluated nodes per outcome; evaluated nodes against distinct states |
| State-space shape | 3 | ECDFs of state counts, depths and discovering actions, selected against other agreeing inputs |
| Shallow patterns and state values | 3 | Shallow patterns of agreeing inputs, collection size, value nesting, state size, richness cohorts and scores |
| Operator coverage | 3 | Rarest constructs, constructs over-represented in disagreements and crashes, and operator edges seen per generation |

A plot without data, for example the verdict pairs of a one-checker corpus or the
signatures of a corpus without `.workflow-stats.cbor`, is replaced by a note.

*Lift* is the share of a group's inputs that contain a construct divided by the
share of all checked inputs that contain it. The report shows lifts above 1 for
constructs in at least 5 inputs of the group.

## 5. Limits

- The corpus records no tool versions. The report shows those of the checkout
  that runs it.
- Stage times have whole-second resolution, so short durations show as 0 or 1 s.
- `.workflow-stats.cbor` is written on controlled exits only; a killed run adds
  nothing to run and worker time.
