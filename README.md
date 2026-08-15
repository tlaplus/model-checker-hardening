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

With no arguments, FuzzTLA prints its help. The executable JAR can also be run without the launcher:

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
maximum_nodes = 16
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
maximum_input_bytes = 1024
```

Populate the corpus with property-based inputs by running:

```sh
./bin/fuzztla run --how=pbt
./bin/fuzztla run --how=pbt --corpus=another-corpus --seed=42 --max-cpus=4
```

The command runs input generation and parsing concurrently. Input generation uses
one worker; the parser uses persistent isolated JVM workers. `--max-cpus` bounds
active work across both stages and defaults to all available processors. Before
starting, FuzzTLA validates every corpus entry and recovers interrupted parser
moves.

When standard output is an interactive ANSI terminal, `run` refreshes its
progress table in place once per second. Redirected output omits intermediate
updates and contains only the final summary.

The workflow tries random byte arrays until `workflow.maximum_entries` unique
accepted inputs exist across all directories. Lengths are selected from uniformly
chosen logarithmic buckets—`0..3`, `4..7`, `8..15`, and so on through
`maximum_input_bytes`—and uniformly within the selected bucket.

The effective nonnegative seed and stage counters are printed on success.
Supplying that seed with the same configuration and starting corpus reproduces
the pseudorandom candidate stream. Entries begin in
`00-inputs/<sha256>.cbor`; the parser records tagged UTC timestamps and a verdict
before moving each entry to its parser result directory. Only
`InputRejectedException` skips a candidate; other generator failures stop the
workflow. A crashed parser also writes
`01parser-crash/<sha256>.stacktrace` with the exception stack trace or other
crash diagnostic.

Generate a deterministic, typed TLA+ expression from a CBOR corpus input with:

```sh
./bin/fuzztla print input.cbor
./bin/fuzztla print --corpus=corpus corpus/00-inputs/example.cbor
./bin/fuzztla print --spec --corpus=corpus corpus/01parser-crash/example.cbor
```

`print` always expects the CBOR envelope described above. By default, it prints
the generated expression. `--spec` prints the complete specification passed to
the parser. Without `--corpus`, the command uses the built-in generator defaults.
With `--corpus`, it uses the generator settings in that corpus's `config.toml`,
which is necessary to replay inputs created with changed limits.

The embedded `input` byte string is interpreted directly by FuzzTLA's generator framework. Variable-length values use per-element continuation markers—an odd byte continues and an even byte terminates—instead of a length prefix. The encoding is implementation-local and may change between versions; a suffix may remain unused when the selected expression is complete.

## License

Licensed under either of

- Apache License, Version 2.0 ([LICENSE-APACHE](LICENSE-APACHE)), or
- MIT License ([LICENSE-MIT](LICENSE-MIT))

at your option.
