# FuzzTLA

[![CI](https://github.com/tlaplus/model-checker-hardening/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/tlaplus/model-checker-hardening/actions/workflows/ci.yml)
[![Integration](https://github.com/tlaplus/model-checker-hardening/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/tlaplus/model-checker-hardening/actions/workflows/integration.yml)

FuzzTLA is work-in-progress on the grant "Hardened Testing of TLA+ Model Checkers" supported by the TLA<sup>+</sup> Foundation.

The project uses Apalache's Java façade for its TLA+ intermediate representation to synthesize TLA+ specifications.

> [!TIP]
> This project is under active development. Expect plenty of changes and no backwards compatibility in 2026.

## Requirements

- JDK 25
- Apache Maven 3.9.10 or newer

## Build and test

The Apalache Java façade is a snapshot served by the Central Portal snapshots
repository. The build also downloads the pinned Apalache 0.62.0 release archive from
GitHub and verifies its SHA-256 digest. Compile and test the project with:

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
build cannot replace classes underneath a long-running workflow. It snapshots
the staged `apalache.jar` into the same directory. With no arguments, FuzzTLA
prints its help. The executable JAR can also be run without the launcher when it
and its sibling `target/apalache.jar` will not be rebuilt during the invocation:

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
├── 01parser-crash/
├── 02tlc-inputs/
├── 02tlc-pass/
├── 02tlc-fail/
├── 02tlc-crash/
├── 02apa-inputs/
├── 02apa-pass/
├── 02apa-fail/
└── 02apa-crash/
```

The initialized configuration below assumes eight available processors. The
Apalache worker count is computed from the processors visible to the JVM.

```toml
[generator]
max_type_depth = 3
max_expression_depth = 32
max_nodes = 32
max_collection_size = 8
max_string_bytes = 32
max_integer_bytes = 16
ignore = ["action", "temporal", "unbound", "exotic"]

[workflow]
# Maximum number of unique entries across every workflow directory.
max_entries = 1000

[workflow.inputs]
# Maximum current occupancy of 00-inputs.
max_entries = 1000

[workflow.parser]
# Maximum combined occupancy of the parser result directories.
max_entries = 1000
# Wall-clock limit for parsing one generated specification.
timeout_sec = 30

[workflow.tlc]
# Maximum combined occupancy of the TLC result directories.
max_entries = 1000
# Wall-clock limit for checking one generated specification.
timeout_sec = 30
# Maximum heap allocated to each isolated TLC JVM.
max_heap_mb = 512
# Number of TLC model-checking workers in each isolated JVM.
workers = 1

[workflow.apalache]
# Maximum combined occupancy of the Apalache result directories.
max_entries = 1000
# Wall-clock limit for checking one generated specification.
timeout_sec = 30
# Maximum heap allocated to each persistent Apalache worker JVM.
max_heap_mb = 512
# Number of concurrent FuzzTLA Apalache workers.
# Initialized to half the available processors, rounded down (at least one).
workers = 4

[pbt]
# Inclusive upper bound on a randomly generated input's length.
max_input_bytes = 10240
# Number of uniformly selected collection-richness cohorts.
richness_cohorts = 10
# Weight multiplier for each level of collection-literal nesting.
richness_nesting_base = 2.0
# Base of the geometric minimum-richness schedule.
richness_threshold_base = 1.5
```

Every generated expression form has one category. `generator.ignore` excludes
the selected categories, the structural types that require them, and forms that
depend on their syntax. The available excludable categories are `action`,
`temporal`, `unbound`, `exotic`, `control`, `label`, `operator`, `quantifier`,
`bool_logic`, `arithmetic`, `set`, `finite_set`, `universe`, `sequence`,
`function`, `fold`, `tuple`, `record`, `variant`, and `model`. The reserved
`core` category supplies atomic leaves and terminal fallback and cannot be
ignored.

Dependencies are disabled transitively. For example, ignoring `set` also
removes bounded quantifiers and function values because they require set-valued
domains. Set `ignore = []` to enable every excludable category. The fixed
workflow module still uses `Next == UNCHANGED exprValue`; filtering applies to
the expression copied into `Init` and `Inv`. The current format requires every
listed field and workflow directory.

Populate the corpus with property-based inputs by running:

```sh
./bin/fuzztla run --how=pbt
./bin/fuzztla run --how=pbt --corpus=another-corpus --seed=42 --max-cpus=4
```

The command runs input generation, parsing, TLC, and Apalache concurrently. Generation and
parsing maintain up to `--max-cpus` workers; parser workers use persistent
isolated JVMs. Each TLC input runs in a fresh JVM. A TLC process uses
`workflow.tlc.workers` internal workers and reserves that many permits from the
shared downstream-priority CPU budget, so at most
`floor(max-cpus / workflow.tlc.workers)` TLC processes run concurrently. The TLC
worker count must not exceed `--max-cpus`. Each FuzzTLA Apalache worker lazily
starts one isolated JVM and calls `Tool.run` sequentially for multiple inputs.
Workers run concurrently and reserve one permit per active call. A timeout or
crash retires the child JVM, and the next input starts a replacement. The
initialized worker count is half the available processors, rounded down with a
minimum of one; the stored setting also must not exceed `--max-cpus`.
TLC and Apalache have
equal checker priority over parsing, which has priority over generation. Waiting
checker requests reserve partial CPU capacity so upstream work cannot starve
them. Before starting, FuzzTLA validates the corpus, recovers interrupted moves,
and completes partial parser fan-outs.

When standard output is an interactive ANSI terminal, `run` refreshes its
progress table in place once per second. Redirected output omits intermediate
updates. After all stage workers stop, the table changes to `FINALIZING` while
FuzzTLA validates the complete corpus for the final summary.

The workflow tries random byte arrays until `workflow.max_entries` unique
accepted inputs exist across all directories. Lengths are selected from uniformly
chosen logarithmic buckets—`0..3`, `4..7`, `8..15`, and so on through
`max_input_bytes`—and uniformly within the selected bucket.

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
Stage progress distinguishes inputs awaiting the parser, TLC, and Apalache and
reports verdict counters for both checkers.

The effective nonnegative seed is printed and flushed before corpus access or
worker startup. The input stage
derives a stable seed for each generator worker, which owns independent cohort
and candidate streams. Reusing the main seed, configuration, starting corpus, and
`--max-cpus` reproduces those worker-local streams. Dynamic target claiming,
duplicate races, and parser-capacity timing may still change the aggregate
corpus. Every stored raw input remains exactly replayable. Entries begin in
`00-inputs/<sha256>.cbor`; its compact `gen` field records the selected cohort and
admission-time richness score. The parser preserves this metadata, records tagged
UTC timestamps and a verdict, and moves the entry to its parser result directory.
Parser passes are copied to `02tlc-inputs` and `02apa-inputs`; the two files count
as one logical corpus entry. TLC records `stages.tlc` and moves its copy to the
matching TLC result directory. Apalache does the same under `stages.apalache`
and its result directories. Both checkers run the fixed `Init`, `Next`, and
`Inv` configuration with deadlock checking disabled; Apalache checks length
zero. A violated invariant or another classified non-crash checker error is a
failure. Both stages use the shared failure-code taxonomy.

Among generator exceptions, only `InputRejectedException` rejects a candidate;
other failures stop the workflow. An unexpected generator or parser-preparation
failure preserves the exact input and stack trace under
`.work/generator-crash/<sha256>.{cbor,stacktrace}` and reports the artifact path.
These diagnostic files do not count as corpus entries. A crashed parser writes
`01parser-crash/<sha256>.stacktrace` with the exception stack trace or other
crash diagnostic.

A crashed checker invocation similarly writes
`02tlc-crash/<sha256>.stacktrace` or
`02apa-crash/<sha256>.stacktrace`. Parser and checker temporary files live under
`<corpus>/.work/{parser,tlc,apalache}-tmp` and are removed after the run.

Generate a deterministic, typed TLA+ expression from a CBOR corpus input with:

```sh
./bin/fuzztla print input.cbor
./bin/fuzztla print --corpus=corpus corpus/00-inputs/example.cbor
./bin/fuzztla print --envelope --corpus=corpus corpus/02apa-inputs/example.cbor
./bin/fuzztla print --spec --corpus=corpus corpus/01parser-crash/example.cbor
```

`print` always expects the CBOR envelope described above. By default, it prints
the generated expression. `--spec` prints the complete specification passed to
the parser, TLC, and Apalache. `--envelope` prints the supported envelope fields as a nested,
human-readable listing. Stage timestamps use UTC ISO-8601, and each `endTime`
includes the elapsed time since its `startTime`. The `input` field appears last,
rendered as TLA+. Without `--corpus`, the command uses the built-in generator
defaults. With `--corpus`, it uses the generator settings in that corpus's
`config.toml`, which is necessary to replay inputs under changed generator
settings.

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
- MIT License ([LICENSE-MIT](LICENSE-MIT)).
