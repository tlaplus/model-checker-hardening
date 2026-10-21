# FuzzTLA

[![Build](https://github.com/tlaplus/model-checker-hardening/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/tlaplus/model-checker-hardening/actions/workflows/build.yml)
[![Integration](https://github.com/tlaplus/model-checker-hardening/actions/workflows/integration.yml/badge.svg?branch=main)](https://github.com/tlaplus/model-checker-hardening/actions/workflows/integration.yml)

FuzzTLA is work-in-progress on the grant "Hardened Testing of TLA+ Model Checkers" supported by the TLA<sup>+</sup> Foundation.

The project uses Apalache's Java façade for its TLA+ intermediate representation to synthesize TLA+ specifications.

> [!TIP]
> This project is under active development. Expect plenty of changes and no backwards compatibility in 2026.

## Results

- [Conformance reports](./conformance/) document corpus-confirmed differences
  between TLC and Apalache, with counts, classifications, and reduced TLA+
  examples.
- [Filed findings](./findings/) document confirmed defects in the checked tools,
  grouped by subsystem and accompanied by reproductions.

## Documentation

The [user manual](https://tlaplus.github.io/model-checker-hardening/user/) explains
how to run campaigns and analyse their results; its sources are in
[`docs/manual`](docs/manual/). The
[architecture atlas](https://tlaplus.github.io/model-checker-hardening/dev/) maps
the code for developers; design decisions are in [`docs/decisions`](docs/decisions/).

## Requirements

- JDK 25
- Apache Maven 3.9.10 or newer
- Python 3, for triage and campaign reports

## Build and test

The build downloads the Apalache Java façade (a 0.62.4 snapshot from the Central
Portal snapshots repository) and the pinned Apalache 0.62.3 release archive, whose
SHA-256 digest it verifies.

```sh
make compile
make test
make package        # the executable JAR, used by bin/fuzztla
make verify         # all Maven verification checks
make user-manual    # the user manual, into target/user
```

`bin/fuzztla` runs the packaged JAR from any working directory. It runs a temporary
snapshot of the JAR, so a concurrent build does not replace classes under a running
campaign.

## Quick start

```sh
./bin/fuzztla init --corpus my-corpus
./bin/fuzztla run --how=pbt --corpus my-corpus
./bin/fuzztla print --corpus my-corpus my-corpus/03aggregator-fail/<hash>.cbor
python3 -m pip install -r script/requirements.txt
python3 script/triager.py my-corpus
```

[Getting started](docs/manual/getting-started.md) walks through these steps.

## License

Licensed under either of

- Apache License, Version 2.0 ([LICENSE-APACHE](LICENSE-APACHE)), or
- MIT License ([LICENSE-MIT](LICENSE-MIT)).
