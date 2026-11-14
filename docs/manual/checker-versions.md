# Earlier checker versions

A corpus runs the TLC and Apalache JARs in its `tools/` directory, which `init` copies
from the build ([The corpus](corpus.md)). To run an earlier TLC or Apalache, fetch it
and name it at `init`.

## 1. Fetching an earlier TLC

TLC publishes only rolling releases. This repository's
[`tlc-build` workflow](https://github.com/tlaplus/model-checker-hardening/actions/workflows/tlc-build.yml)
builds `tla2tools.jar` from a tlaplus/tlaplus commit on `master` and publishes it as a
prerelease. To start a build (needs write access):

```sh
gh workflow run tlc-build.yml --repo tlaplus/model-checker-hardening -f commit=<sha-or-ref>
```

A build is named after the commit's date (UTC) and its first 12 hex digits:

| item | example |
| --- | --- |
| release tag | `tlc-20250314-1a2b3c4d5e6f` |
| asset | `tla2tools-20250314-1a2b3c4d5e6f.jar` |

!!! warning
    These builds are not the latest TLC. For the current TLC, use
    [TLC's releases](https://github.com/tlaplus/tlaplus/releases).

The release notes state whether a small FuzzTLA campaign ran with the build, at the
FuzzTLA commit that built it. An earlier JAR may lack an API that FuzzTLA's workers use;
then a worker fails to start. At the time of writing, the earliest compatible TLC commit
is `7e64fd03` (2026-02-23), which added `SanyExitCode`
([ADR 0032](../decisions/0032-earlier-checker-builds.md#3-compatibility-is-reported-not-guaranteed)).

To download a published build into `target/checkers/` and verify its checksum, run:

```sh
make tlc-build TLC_BUILD=tlc-20250314-1a2b3c4d5e6f
```

## 2. Fetching an earlier Apalache

```sh
make apalache-release APALACHE_VERSION=0.62.0
```

This extracts `apalache.jar` from the release archive as
`target/checkers/apalache-0.62.0.jar`. `make clean` removes `target/checkers/`.

## 3. Initializing a corpus

```sh
./bin/fuzztla init --how=pbt --corpus corpus-old \
  --tool tla2tools=target/checkers/tla2tools-20250314-1a2b3c4d5e6f.jar \
  --tool apalache=target/checkers/apalache-0.62.0.jar
```

`--tool TOOL=FILE` copies FILE into `tools/` in place of the build's JAR. TOOL is
`tla2tools` or `apalache`, and a tool without `--tool` gets the build's JAR. With
`--base-corpus`, `--tool` replaces the base's JAR.

## 4. Citing the version

The corpus copy is named `tools/tla2tools.jar`. Its manifest records the TLC commit
and build time:

```sh
unzip -p corpus-old/tools/tla2tools.jar META-INF/MANIFEST.MF | grep -E 'X-Git-Revision|Build-TimeStamp'
```

Cite this commit in findings, together with the FuzzTLA and Apalache versions.

## Background

[ADR 0032](../decisions/0032-earlier-checker-builds.md) records the design.
