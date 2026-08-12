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
└── 00-inputs/
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

[pbt]
# Target number of unique, accepted inputs in 00-inputs.
corpus_entries = 1000
# Inclusive upper bound on a randomly generated input's length.
maximum_input_bytes = 1024
```

Populate the corpus with property-based inputs by running:

```sh
./bin/fuzztla run --how=pbt
./bin/fuzztla run --how=pbt --corpus=another-corpus --seed=42
```

The configured `corpus_entries` is a target total, not a number added on every run. Before generating anything, FuzzTLA verifies every existing entry's filename, CBOR envelope, embedded-input hash, kind, and acceptance by the configured IR generator. It then tries random byte arrays until the target contains that many unique accepted inputs. Lengths are selected from uniformly chosen logarithmic buckets—`0..3`, `4..7`, `8..15`, and so on through `maximum_input_bytes`—and uniformly within the selected bucket.

The effective nonnegative seed and run counters are printed as an aligned summary on success. Supplying that seed with the same configuration and starting corpus reproduces the pseudorandom candidate stream. Entries are stored as `00-inputs/<sha256>.cbor`, where the filename contains the lowercase SHA-256 digest of the embedded generator bytes. A newly generated entry is the CBOR map `{"kind": "expr", "input": <byte string>}`. Metadata added by later stages does not change the filename. Only `InputRejectedException` skips a candidate; other generator failures stop the run.

Generate a deterministic, typed TLA+ expression from a CBOR corpus input with:

```sh
./bin/fuzztla print input.cbor
./bin/fuzztla print --corpus=corpus corpus/00-inputs/example.cbor
```

`print` always expects the CBOR envelope described above. Without `--corpus`, it uses the built-in generator defaults. With `--corpus`, it uses the generator settings in that corpus's `config.toml`, which is necessary to replay inputs created with changed limits.

The embedded `input` byte string is interpreted directly by FuzzTLA's generator framework. Variable-length values use per-element continuation markers—an odd byte continues and an even byte terminates—instead of a length prefix. The encoding is implementation-local and may change between versions; a suffix may remain unused when the selected expression is complete.

## License

Licensed under either of

- Apache License, Version 2.0 ([LICENSE-APACHE](LICENSE-APACHE)), or
- MIT License ([LICENSE-MIT](LICENSE-MIT))

at your option.
