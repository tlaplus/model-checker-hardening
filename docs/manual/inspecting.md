# Inspecting a corpus

A corpus stores inputs as bytes, not as TLA<sup>+</sup>. This page shows how to see
the module behind an entry, rerun it with a checker, and sort a corpus's disagreements
and crashes into known and new issues.

## Printing an entry

```sh
fuzztla print --corpus my-corpus my-corpus/03aggregator-fail/<hash>.cbor
```

| Option | Prints |
|---|---|
| none | The generated TLA<sup>+</sup> module, or the expression for an `expr` corpus. |
| `--spec` | The complete specification given to SANY and TLC. |
| `--apalache-ir` | The typed JSON given to Apalache. |
| `--envelope` | The stored metadata: how the entry was generated, and each stage's verdict, failure code, timestamps and metrics. |
| `--known-defects=FILE` | Also lists the [known-defect signatures](known-defect-signatures.md) of `FILE` that match the entry. |

Always pass `--corpus`. Without it, `print` uses the built-in generator defaults,
which give a different module when the corpus was generated with other settings.

An envelope looks like this:

```text
kind: module
gen:
  generation: 0
  cohort: 3
  richness: 123.0
stages:
  parser:
    verdict: pass
  tlc:
    verdict: fail
    code: 75 (spec_eval)
    detail: Attempted to apply Head to the empty sequence.
    metrics:
      phase: init
      distinctStates: 1
      …
  apalache:
    verdict: counterexample
    metrics:
      traceLength: 0
  aggregator:
    verdict: fail
```

## Rerunning an entry by hand

Write the specification to a file and run TLC on it:

```sh
fuzztla print --spec --corpus my-corpus my-corpus/03aggregator-fail/<hash>.cbor > FuzzInput.tla
```

The module is named `FuzzInput`. It defines `Init`, `Next` and `Inv`, and for a
module with a temporal property, also `Liveness`. FuzzTLA runs both checkers without
deadlock checking. For the exact arguments, see
[What each checker is asked](temporal-properties.md#3-what-each-checker-is-asked).

To shrink a metamorphic counterexample to a smaller rewrite, use `fuzztla shrink`
([Metamorphic testing](metamorphic-testing.md#4-triage)).

## Triage

```sh
python3 -m pip install -r script/requirements.txt   # once
python3 script/triager.py my-corpus
```

The triager compares every crash and every disagreement in the corpus with the
signatures of the published [findings and conformance reports](results.md). It writes
one CSV file per directory into the corpus root:

- `01parser-crash-triage.csv`
- `02tlc-crash-triage.csv`, `02tlcn-crash-triage.csv`, `02apa-crash-triage.csv`
- `03aggregator-fail-triage.csv`

Each row has two columns, `entry_hash` and `issue`. `issue` is one of:

| Value | Meaning |
|---|---|
| a file name, such as `head-of-empty-sequence.md` | The finding or conformance report that explains the entry. |
| `TIMEOUT` | The worker timed out, and no known slow defect matches ([Timeout diagnostics](timeout-diagnostics.md)). |
| `FIRST_ERROR` | TLC and TLC-n stopped at different errors, which is expected ([Comparing TLC with itself](running.md#comparing-tlc-with-itself)). |
| `STATE_COUNT` | TLC and TLC-n completed with different numbers of distinct states. |
| `NEW` | Nothing explains the entry. Investigate it. |

Signatures are conservative: an entry is attributed to a document only when the
diagnostic, and for disagreements also the verdict pair and failure code, match.
An ambiguous entry stays `NEW`.

To count the rows by issue:

```sh
cut -d, -f2 my-corpus/03aggregator-fail-triage.csv | sort | uniq -c | sort -rn
```

## Beyond single entries

- [Corpus database](corpus-database.md): export the corpus to SQLite and query it
  with SQL.
- [Campaign report](campaign-report.md): one Markdown report with plots of a
  campaign.
- [Exploration metrics](exploration-metrics.md): tell entries that exercise the
  checkers apart from shallow ones, which explore almost nothing.
