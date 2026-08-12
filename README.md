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

Initialize a FuzzTLA project or run property-based fuzzing with:

```sh
./bin/fuzztla init
./bin/fuzztla run --how=pbt
```

The command structure and argument validation are in place, but initialization and property-based fuzzing are not implemented yet. These commands report that status and exit with code `1`.

Generate a deterministic, typed TLA+ expression from an arbitrary binary file with:

```sh
./bin/fuzztla print input.bin
```

The binary input is interpreted directly by FuzzTLA's generator framework. Variable-length values use per-element continuation markers—an odd byte continues and an even byte terminates—instead of a length prefix. The encoding is implementation-local and may change between versions; a suffix may remain unused when the selected expression is complete.

## License

Licensed under either of

- Apache License, Version 2.0 ([LICENSE-APACHE](LICENSE-APACHE)), or
- MIT License ([LICENSE-MIT](LICENSE-MIT))

at your option.
