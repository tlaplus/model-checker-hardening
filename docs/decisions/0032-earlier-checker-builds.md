# 0032: Earlier TLC and Apalache builds

**Authors:** Igor Konnov and Claude

**Status:** Implemented

**Date:** 2026-11-14

**Implementation.** Branch `igor/tlc-builds`:
- `init --tool` (FuzzTLA `cc79ae99`): `cli.ToolOverride`, applied before
  `LibraryInstall.plan`;
- the `make` targets `tlc-build` and `apalache-release` (`c7f2d024`);
- `.github/workflows/tlc-build.yml` (`05b1e56b`).

The build recipe and the smoke criterion were checked locally, with Ant 1.10.15 and JDK
17 on tlaplus `d8fb231d` and `f8eabae1`. The workflow itself runs only from the default
branch, so its first dispatch happens after merge.

## Context

A corpus runs the checker JARs in its `tools/` directory ([ADR 0025][adr-0025] §3).
`init` copies them from the build, which stages one version of each:
- Apalache `0.63.0`, a versioned GitHub release;
- TLC `1.8.0-SNAPSHOT`, a rolling snapshot that changes with every TLC commit.

To find out when a defect appeared, or whether a fix regressed, a campaign has to run
an earlier checker. ADR 0025 allows this only by hand: replace the JARs in
`tools/` before the first run.

Apalache publishes every release, so an earlier Apalache only has to be downloaded.
TLC publishes rolling releases, which keep no earlier builds. An earlier TLC has to be
built from source.

A TLC build published next to FuzzTLA can be mistaken for the current TLC. A user
looking for `tla2tools.jar` may download it, or a script may follow the repository's
`releases/latest`. Every build must therefore name the TLC commit and date it was
built from, and must never become the latest release.

## Decision

### 1. A workflow publishes TLC builds

`.github/workflows/tlc-build.yml` is dispatched by hand with a tlaplus/tlaplus commit
(a SHA or a ref) and a JDK version (default 17, the version of TLC's own CI). It
works as follows:

1. It resolves the ref to a full SHA, and refuses a commit that `master` does not
   reach, so branch builds are never published.
2. It runs `ant -f tlatools/org.lamport.tlatools/customBuild.xml -Dtest.skip=true info
   compile compile-test dist`. These are the steps of TLC's own `default-maven` target
   without `test-dist`; `dist` packages a few test classes, so it needs `compile-test`.
   `info` stamps the commit into the manifest (`X-Git-Revision`), and the workflow checks
   the stamp.
3. It runs a smoke campaign (section 3).
4. It publishes a release of this repository.

| item | form | example |
| --- | --- | --- |
| tag | `tlc-YYYYMMDD-<sha12>` | `tlc-20250314-1a2b3c4d5e6f` |
| assets | `tla2tools-YYYYMMDD-<sha12>.jar`, plus `.jar.sha256` | `tla2tools-20250314-1a2b3c4d5e6f.jar` |
| title | `TLC tlaplus@<sha12> of YYYY-MM-DD (not the latest TLC)` | |

- **The date** is the commit's committer date in UTC, when the commit landed.
  The build time is recorded in the notes and the manifest (`Build-TimeStamp`), not
  in the name.
- **The release** is created with `--prerelease --latest=false`, so `releases/latest`
  never resolves to it. No asset is named `tla2tools.jar`.
- **The notes** open with "**Not the latest TLC.**" and a link to TLC's releases. They
  name the full commit, its subject, the build time and JDK, the hardening commit that
  built it, the SHA-256, and the smoke result.
- **The tag** points at the hardening commit of the run, which records the workflow
  version. The TLC commit is in the tag's name.
- **The tag is a function of the commit.** A run whose tag exists fails, so an
  asset is never replaced.

### 2. `make` fetches builds, `init --tool` installs them

- `make tlc-build TLC_BUILD=<tag>` downloads a published build and its checksum into
  `target/checkers/` and verifies the checksum.
- `make apalache-release APALACHE_VERSION=<x.y.z>` downloads Apalache's release
  archive and extracts `apalache.jar` as `target/checkers/apalache-<x.y.z>.jar`.
- `fuzztla init --tool TOOL=FILE`, repeatable, with TOOL a `[tools]` key (`apalache`,
  `tla2tools`), copies FILE into `tools/` in place of the staged JAR. With
  `--base-corpus`, it replaces the base's JAR.

`init` already copies a JAR that the configuration names (`LibraryInstall.plan`). The
option names the JAR without a hand-edited configuration.

### 3. Compatibility is reported, not guaranteed

FuzzTLA's workers compile against the staged JARs. The parser and TLC workers put the
corpus's `tla2tools.jar` in front of FuzzTLA's class path, and the Apalache worker calls
`Tool.run` with JSON IR. An earlier JAR may lack an API they use, for example
`tla2sany.drivers.SanySettings` in `ParserWorkerMain`. The worker then fails to start.

The workflow therefore runs a 20-entry, TLC-only `pbt` campaign with the new JAR, at the
hardening commit of the run. The campaign has passed if `run` exits 0 and TLC gives at
least one entry a verdict other than `crash`. The notes record the result, which holds
for that hardening commit only. A build that does not run is still published: a later
FuzzTLA may adapt to it.

At FuzzTLA `0ba4bc04`, the earliest compatible TLC is tlaplus `7e64fd03` (2026-02-23,
"SANY: enumerate possible exit codes"), which added `tla2sany.drivers.SanyExitCode`.
Its parent `2c4d2b02` fails only on that enum, in `ParserWorkerMain` and
`RuleModuleDefinitions`. `SanySettings` arrived four days earlier, in `932a781c`.
This was found as follows:
- FuzzTLA's main sources were compiled against `tla2tools.jar` built at
  first-parent commits of `master`, bisecting the 299 commits from `932a781c` to
  `d8fb231d`. The bisection assumes that a commit compiles once its predecessor does.
- A 20-entry TLC-only campaign with the `7e64fd03` build gave TLC pass 3,
  counterexample 3, fail 13 and crash 1, and parser pass 20. The crash is TLC reporting
  an empty `CHOOSE` as an unexpected exception, not an API mismatch.
- With a build of `f8eabae1` (2025-07-19), the parser worker failed on all 20 entries
  with `NoSuchMethodError` on `SANY.parse(SpecObj, String, SanyOutput, SanySettings)`,
  so TLC received no input.

Earlier Apalache releases get no smoke test; their compatibility shows when a corpus
runs.

## Alternatives considered

- **TLC's own releases.** TLC tags only a few stable releases, and its rolling
  release replaces its assets. Neither covers an arbitrary commit.
- **Maven snapshots.** The snapshot repository keeps a bounded number of timestamped
  snapshots, which expire. Their names carry the build time, not the commit.
- **Building TLC locally, in `make`.** This needs Ant and a TLC checkout on every
  machine, and two people would run different bytes for the same commit.
- **Smoke test blocks publishing.** Compatibility depends on the FuzzTLA commit as much
  as on the TLC commit, so a refusal would be outdated by the next FuzzTLA change.

## Consequences

- **An earlier checker is one command per tool,** plus `init --tool`.
- **The corpus copy loses the asset name.** `tools/tla2tools.jar` keeps its commit in
  `META-INF/MANIFEST.MF` (`X-Git-Revision`), and findings cite the TLC commit from
  there.
- **Releases of this repository are TLC builds.** All are prereleases. If the repository
  later publishes its own releases, they remain the latest.
- **Extension.** This extends [ADR 0025][adr-0025] §3, which named only a hand copy into
  `tools/`.

## Implementation phases

1. `init --tool`.
2. The `make` targets.
3. The workflow. It runs only from the default branch, so it is tested after merge.

[adr-0025]: 0025-self-contained-corpora.md
