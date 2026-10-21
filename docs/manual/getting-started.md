# Getting started

This page builds FuzzTLA, runs a small campaign, and shows where to look at what it
found.

## Requirements

- JDK 25
- Apache Maven 3.9.10 or newer
- Python 3 for triage and reports (optional)

The build downloads everything else, including pinned releases of TLC and Apalache.

## Build

```sh
git clone https://github.com/tlaplus/model-checker-hardening.git
cd model-checker-hardening
make package
./bin/fuzztla --version
```

`bin/fuzztla` is the launcher. It works from any directory, so you can add `bin` to
your `PATH`. It runs a private copy of the JAR, so rebuilding FuzzTLA does not
disturb a running campaign.

## Your first campaign

Create a corpus, the directory that holds the campaign:

```sh
./bin/fuzztla init --corpus my-corpus
```

`init` writes `my-corpus/config.toml` with defaults and creates the stage directories.
The default campaign stops at 1,000 entries. For a quick trial, lower `max_entries` in
the `[workflow]` table and `generation_size` in the `[mutator]` table to `20` each.
Then run:

```sh
./bin/fuzztla run --how=pbt --corpus my-corpus
```

`--how=pbt` selects property-based testing, which generates fresh random inputs and
mutates good ones. In a terminal, `run` shows a live flow diagram of the stages and
their counters. A new value is bold for one second, with an arrow showing its
direction. When the run ends, it prints a summary:

```text
Random seed: 42
Workflow run finished for 'my-corpus'
Corpus entries: 20
Total elapsed: 48s
Stop reason: COMPLETED

TLC
  Pass 0
  Cex 2
  Fail 18
  Crash 0

APALACHE
  Pass 2
  Cex 8
  Fail 8
  Crash 2

AGGREGATOR
  Agree 8
  Differ 10
...
```

`Cex` counts counterexamples, that is, invariant or property violations. `Differ`
counts entries on which TLC and Apalache disagree. Most disagreements in a fresh
corpus are already documented differences. For example, TLC reports `Head(<<>>)` as
an error, while Apalache leaves its value unconstrained. Triage, below, separates
these from new ones.

Press ++ctrl+c++ to stop a run. Run the same command again to continue: counts
accumulate across runs, and interrupted work is recovered at startup. To grow a
finished corpus, raise `max_entries` and run again.

## Look at the results

Disagreements are in `my-corpus/03aggregator-fail/`, and checker crashes are in
`my-corpus/02tlc-crash/` and `my-corpus/02apa-crash/`. Each entry is a CBOR file named
by the SHA-256 hash of its content. Print the TLA<sup>+</sup> module of one:

```sh
./bin/fuzztla print --corpus my-corpus my-corpus/03aggregator-fail/13d4df05….cbor
```

Classify all crashes and disagreements against the known findings and conformance
reports:

```sh
python3 -m pip install -r script/requirements.txt
python3 script/triager.py my-corpus
```

The triager writes one CSV file per directory, for example
`my-corpus/03aggregator-fail-triage.csv`. Each row names an entry and the document
that explains it, or `NEW` if no document does.

Write a campaign report with plots:

```sh
python3 script/report.py my-corpus
```

The report is `my-corpus/report.md`.

## Next steps

- [The corpus](corpus.md) explains the directories and verdicts.
- [Running campaigns](running.md) covers seeds, CPU limits and the other techniques.
- [Inspecting a corpus](inspecting.md) shows how to rerun an entry with TLC or
  Apalache by hand.
