# 0025: Self-contained corpora, chosen at `init`

**Authors:** Igor Konnov and Claude

**Status:** Implemented

**Date:** 2026-10-28

**Implementation.** Three stacked branches implement the phases of the last section:
- `igor/init-how`: the technique key and `init --how` (FuzzTLA `8241615c`), and the
  removal of `.technique` and `.checkers` (`826ef4f6`).
- `igor/self-contained-corpus`:
  - `tools/` and the `[tools]` table (`284b2b9b`, `ad0b74d0`);
  - the copied rule libraries (`a78ac6c0`);
  - the removal of the two manifests (`d63536b2`).
- `igor/init-mt-base`: `init --how=mt --base-corpus` (`4d352c9d`).

Implementation settled these details:
- The tool table is keyed by `config.ToolJar`. `workflow.CorpusTools` resolves it, and
  `workflow.library.LibraryInstall` plans the copies of a whole configuration.
- The TLC classpath keeps the library first and puts `tla2tools` after it, so a
  library's TLC overrides still win, as before.
- A legacy `.technique` is still read, through `ReplayRecord.requireUnchanged`. Delete
  it once old corpora are gone.

## Context

A corpus is a directory that `fuzztla run` extends over many sessions and that `print`,
`export-db`, `shrink` and the campaign report read long afterwards. Its entries are byte
payloads, so they mean something only together with what decodes and checks them. Today
that context is split three ways:

- **Choices on the command line.** `run --how=X` picks the technique on every run
  ([ADR 0016][adr-0016] §1). The first run records it in `.technique`, and the checker set
  in `.checkers` (§5), which repeats `[workflow] checkers`.
- **Sources outside the corpus.** The rewrite rules and the edge guards are read from
  the repository, as in `classpath = ["../libraries/rewrites"]`
  ([ADR 0017][adr-0017], [ADR 0023][adr-0023] §6). Only operator libraries are copied
  into the corpus's `tla/` at `init --library`.
- **Tools outside the corpus.** TLC is shaded into `fuzztla.jar`, and Apalache is the
  release jar staged in `target/`. Both change with every rebuild.

Because sources and tools live outside, the first run fingerprints them:
- `.operator-library` holds SHA-256 hashes of the operator library sources and the Apalache
  jar ([ADR 0014][adr-0014], [ADR 0015][adr-0015]);
- `.rewrite-library` does the same for the rewrite rules or the edge guards
  ([ADR 0017][adr-0017] §6).

A later run, or `print`, refuses the corpus once a `git pull` changes a rule or a
rebuild changes Apalache. The corpus is protected, but it can no longer be read with
the current build, and the files that would make it readable are gone.

The split also makes metamorphic testing on top of an earlier corpus laborious. For
`mt` over corpus71 (a `gbt` corpus), a user copies the `[generator]` and `[graph]`
tables and the `tla/` directory, sets `[metamorphic] rules` and `base_corpus`, adds
`metamorphic-defects.toml`, and passes `--how=mt` on every run.

## Decision

A corpus holds everything that decides how its entries decode and check:
`config.toml`, `tla/` for every TLA<sup>+</sup> library it reads, `tools/` for the
checker jars, and the entries. `init` assembles it, `run` follows it, and nothing is
fingerprinted.

### 1. The technique is a configuration key, chosen at `init`

- `fuzztla init --how=pbt|mt|gbt` is required. It writes `technique` into the
  `[workflow]` table, next to `checkers`.
- `fuzztla run` has no `--how`. It runs the configured technique and prints it next
  to the seed. `print`, `export-db`, `shrink` and the report read the same key.
- `init` writes the settings that follow from the technique:
  - `gbt` sets `[generator] kind = "graph"`, since `gbt` generates nothing else.
    `Technique.kindFor` holds this per constant.
  - `mt` sets `[metamorphic] rules` to the shipped `Rewrites` module. If Apalache is among
    the checkers, it also adds `signatures/metamorphic-defects.toml` to `known_defects`.
- A configuration without `technique` reads as `pbt`, the technique of an unrecorded corpus
  under ADR 0016. A corpus that still has a `.technique` file but no key is refused, with
  the key to add, so an old `gbt` or `mt` corpus never runs as `pbt`.

### 2. Libraries are copied into the corpus

`init` copies every library the configuration names into `tla/`, and writes its
classpath as `tla/<name>`. This was already done for operator libraries (`LibraryInstall`),
and now also covers the `[metamorphic]` rewrite rules and the `[graph]` edge-guard rules.
A rule or library edit in the repository no longer reaches an existing corpus. To change
a corpus's rules, edit its `tla/` copy before the first run.

### 3. Tools are copied into the corpus

`make package` stages `target/tla2tools.jar` next to `target/apalache.jar`. `init` copies
both into `tools/`, and writes a new table:

```toml
[tools]
apalache = "tools/apalache.jar"
tla2tools = "tools/tla2tools.jar"
```

Every tool use goes through these paths:
- the Apalache worker;
- Snowcat typing of operator libraries and rule modules, which decides how library
  operators decode;
- the shrinker's checkers;
- the TLC, TLC-n and parser workers, and the library source probe. These put `tla2tools`
  in front of FuzzTLA's class path, where `WorkerLaunch` already puts a worker's own
  entries.

Without a `[tools]` table, a run uses the build's staged jars, as before this ADR.
FuzzTLA's own code is not copied, so a corpus pins its tools but not its decoder.

### 4. No replay records

`.technique`, `.checkers`, `.operator-library` and `.rewrite-library` are removed, with
`ReplayRecord` and the record I/O of `LibraryManifest`. Their duties move as follows:

| record | before | after |
| --- | --- | --- |
| `.technique` | refuses a run whose `--how` differs | `[workflow] technique`; no `--how` exists to differ |
| `.checkers` | refuses a run whose `[workflow] checkers` differs | `[workflow] checkers` alone |
| `.operator-library`, `.rewrite-library` | refuse changed sources or Apalache | sources in `tla/`, Apalache in `tools/`; the manifests are still computed, but only to compare a base corpus (section 5) |

Editing a decoding setting after the first entry, such as the technique, the checkers,
a rule weight or a file under `tla/`, is the user's responsibility. It already was for
`[generator]`, which no record ever pinned.

### 5. `init --how=mt --base-corpus DIR`

The base is optional, since `mt` also generates its own bases. With a base, `init`:

1. refuses an `mt` base, whose payloads carry a metamorphic header;
2. starts from the base's whole `config.toml`, including the checkers, timeouts, workers
   and known-defect databases;
3. copies the base's `tla/` and `tools/`, so adopted parents decode with the same
   libraries and tools;
4. sets `technique = "mt"`, `base_corpus`, and the `mt` settings of section 1.

`run` still refuses a base whose entries this run would not decode to their parents
([ADR 0016][adr-0016] §6). It compares the generator settings, as before. The library and
guard manifests are now computed from both corpora's `tla/` and `tools/` rather than read
from the base's records. A base that predates `tools/` is compared against the staged
jars.

`init` writes each path relative to the corpus when both lie in the FuzzTLA project, and
absolutely otherwise, as it already does for the shipped known-defect database.

## Alternatives considered

- **Keep the fingerprints and add `init --how`.** This was the smallest change, but it
  keeps corpora unreadable after a rule edit or an Apalache upgrade.
- **One `.replay` file** pinning the technique, the checkers and both manifests. This
  merges four records into one, but keeps the fingerprints and their failure mode.
- **Fingerprints in `config.toml`.** `init` would have to type the libraries, a weight
  edit before the first run would need a refreshed fingerprint, and a hand-edited file
  would pin itself.
- **A hash over the decoding settings** in place of the records. This would also guard
  `[generator]`, but no record has guarded it so far, and copying the inputs removes the
  drift these records were meant to catch.

## Consequences

- **A corpus can be replayed later.** Its rules, libraries and tools travel with it.
  A newer FuzzTLA reads it with the corpus's tools, unless FuzzTLA's worker code needs a
  tool API that the copied jar lacks; then the worker fails to start.
- **Upgrading a corpus's tools is explicit.** Replace the jars in its `tools/`.
- **Each corpus is about 200 MB larger**: 197 MB of `apalache.jar` and about 4 MB of
  `tla2tools.jar`. This adds about 12% to a corpus the size of corpus71 (1.7 GB, 100,000
  inputs).
- **Edits after the first entry are not detected.** This includes the checkers, whose
  change confuses startup recovery ([ADR 0016][adr-0016] §5).
- **Starting `mt` over a previous run is one command**:
  `fuzztla init --how=mt --base-corpus corpus71 --corpus corpus79`.
- **Deviations.** This ADR replaces:
  - [ADR 0016][adr-0016] §1 (`--how` on `run`; `.technique`) and §5 (`.checkers`);
  - the replay manifests of [ADR 0014][adr-0014] item 7, [ADR 0015][adr-0015] item 6,
    [ADR 0017][adr-0017] §6 and [ADR 0023][adr-0023] §6.
  Those ADRs carry a note pointing here.

## Implementation phases

1. The technique in `config.toml`, `init --how`, `run` without `--how`, and the removal
   of `.technique` and `.checkers` (sections 1 and 4).
2. `tools/`, the copied rule libraries, and the removal of the two manifests (sections
   2–4).
3. `init --how=mt --base-corpus` (section 5).

[adr-0014]: 0014-per-checker-library-linking.md
[adr-0015]: 0015-diff-linked-libraries.md
[adr-0016]: 0016-metamorphic-testing.md
[adr-0017]: 0017-rewrite-rule-library.md
[adr-0023]: 0023-graph-based-testing.md
