# FuzzTLA

[![CI](https://github.com/tlaplus/model-checker-hardening/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/tlaplus/model-checker-hardening/actions/workflows/ci.yml)

FuzzTLA is work-in-progress on the grant "Hardened Testing of TLA+ Model Checkers" supported by the TLA<sup>+</sup> Foundation.

The project uses Apalache's Java façade for its TLA+ intermediate representation to synthesize TLA+ specifications.

## Requirements

- JDK 25
- Apache Maven 3.9.10 or newer

## Build and test

The Apalache dependency is a snapshot served by the Central Portal snapshots repository. Compile and test the project with:

```sh
make compile
make test
```

Build the executable JAR or run all Maven verification checks with:

```sh
make package
make verify
```

Run the command-line application with:

```sh
make run
make run ARGS='--help'
make run ARGS='--version'
```

Once packaged, the launcher can be used directly and from any working directory:

```sh
./bin/fuzztla --help
/path/to/model-checker-hardening/bin/fuzztla --version
```

The launcher runs from a temporary snapshot of the packaged JAR, so a concurrent
build cannot replace classes underneath a long-running workflow. With no
arguments, FuzzTLA prints its help. The executable JAR can also be run without
the launcher when it will not be rebuilt during the invocation:

```sh
java -jar target/fuzztla.jar --help
```

## Commands

Initialize a corpus in the default `corpus` directory with:

```sh
./bin/fuzztla init
```

Use `--corpus` to select another directory. Initialization creates this layout without overwriting an existing configuration:

```text
corpus/
├── config.toml
├── 00-inputs/
├── 01parser-pass/
├── 01parser-fail/
└── 01parser-crash/
```

The default configuration is:

```toml
[generator]
maximum_type_depth = 3
maximum_expression_depth = 32
maximum_nodes = 32
maximum_collection_size = 8
maximum_string_bytes = 32
maximum_integer_bytes = 16

[workflow]
# Maximum number of unique entries across every workflow directory.
maximum_entries = 1000

[workflow.inputs]
# Maximum current occupancy of 00-inputs.
maximum_entries = 1000

[workflow.parser]
# Maximum combined occupancy of the parser result directories.
maximum_entries = 1000
# Wall-clock limit for parsing one generated specification.
timeout_seconds = 30

[pbt]
# Inclusive upper bound on a randomly generated input's length.
maximum_input_bytes = 10240
# Number of uniformly selected collection-richness cohorts.
richness_cohorts = 10
# Weight multiplier for each level of collection-literal nesting.
richness_nesting_base = 2.0
# Base of the geometric minimum-richness schedule.
richness_threshold_base = 1.5
```

Populate the corpus with property-based inputs by running:

```sh
./bin/fuzztla run --how=pbt
./bin/fuzztla run --how=pbt --corpus=another-corpus --seed=42 --max-cpus=4
```

The command runs input generation and parsing concurrently. Both stages maintain
up to `--max-cpus` workers; parser workers use persistent isolated JVMs. A shared
fair CPU budget bounds active work across both stages and defaults to all
available processors. Before starting, FuzzTLA validates every corpus entry and
recovers interrupted parser moves.

When standard output is an interactive ANSI terminal, `run` refreshes its
progress table in place once per second. Redirected output omits intermediate
updates. After all stage workers stop, the table changes to `FINALIZING` while
FuzzTLA validates the complete corpus for the final summary.

The workflow tries random byte arrays until `workflow.maximum_entries` unique
accepted inputs exist across all directories. Lengths are selected from uniformly
chosen logarithmic buckets—`0..3`, `4..7`, `8..15`, and so on through
`maximum_input_bytes`—and uniformly within the selected bucket.

For each missing corpus entry, the input stage uniformly selects one richness
cohort. Cohort 0 accepts every generated expression. Cohort `c > 0` requires a
collection-richness score of at least
`richness_threshold_base^(c - 1)`. The score sums the size of every explicit set,
sequence, tuple, and record literal, weighted by
`richness_nesting_base` for each enclosing collection literal. With the default
configuration, the ten effective integer cutoffs are `0, 1, 2, 3, 4, 6, 8, 12,
18, 26`.

The selected cohort remains fixed while generator rejections, insufficiently rich
expressions, and duplicate inputs are retried. A failure to fill one cohort after
10,000 candidates stops the workflow with the cohort, threshold, and best score
in the diagnostic. The progress table reports candidate attempts, generator
rejections, richness rejections, and duplicates separately. It also reports the
minimum, maximum, and average richness of inputs admitted during the current run.

The effective nonnegative seed is printed and flushed before corpus access or
worker startup, and repeated in the final summary on success. The input stage
derives a stable seed for each generator worker, which owns independent cohort
and candidate streams. Reusing the main seed, configuration, starting corpus, and
`--max-cpus` reproduces those worker-local streams. Dynamic target claiming,
duplicate races, and parser-capacity timing may still change the aggregate
corpus. Every stored raw input remains exactly replayable. Entries begin in
`00-inputs/<sha256>.cbor`; its compact `gen` field records the selected cohort and
admission-time richness score. The parser preserves this metadata, records tagged
UTC timestamps and a verdict, and moves the entry to its parser result directory.
Among generator exceptions, only `InputRejectedException` rejects a candidate;
other failures stop the workflow. An unexpected generator or parser-preparation
failure preserves the exact input and stack trace under
`.work/generator-crash/<sha256>.{cbor,stacktrace}` and reports the artifact path.
These diagnostic files do not count as corpus entries. A crashed parser writes
`01parser-crash/<sha256>.stacktrace` with the exception stack trace or other
crash diagnostic.

Generate a deterministic, typed TLA+ expression from a CBOR corpus input with:

```sh
./bin/fuzztla print input.cbor
./bin/fuzztla print --corpus=corpus corpus/00-inputs/example.cbor
./bin/fuzztla print --envelope --corpus=corpus corpus/01parser-pass/example.cbor
./bin/fuzztla print --spec --corpus=corpus corpus/01parser-crash/example.cbor
```

`print` always expects the CBOR envelope described above. By default, it prints
the generated expression. `--spec` prints the complete specification passed to
the parser. `--envelope` prints the supported envelope fields as a nested,
human-readable listing. Stage timestamps use UTC ISO-8601, and each `endTime`
includes the elapsed time since its `startTime`. The `input` field appears last,
rendered as TLA+. Without `--corpus`, the command uses the built-in generator
defaults. With `--corpus`, it uses the generator settings in that corpus's
`config.toml`, which is necessary to replay inputs created with changed limits.

```text
kind: expr
gen:
  cohort: 7
  richness: 18.0
stages:
  parser:
    verdict: pass
    startTime: 2026-08-13T14:26:07Z
    endTime: 2026-08-13T14:27:30Z (duration: 1m 23s)
input:
  FALSE
```

The embedded `input` byte string is interpreted directly by FuzzTLA's generator framework. Variable-length values use per-element continuation markers—an odd byte continues and an even byte terminates—instead of a length prefix. The encoding is implementation-local and may change between versions; a suffix may remain unused when the selected expression is complete.

## License

Licensed under either of

- Apache License, Version 2.0 ([LICENSE-APACHE](LICENSE-APACHE)), or
- MIT License ([LICENSE-MIT](LICENSE-MIT))

at your option.
