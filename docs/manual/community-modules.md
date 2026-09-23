# Community Modules

> **Status:** Implemented.
> [ADR 0014](../decisions/0014-per-checker-library-linking.md) records the design and
> the probe behind it.

A community-module campaign compares the TLA+ Community Modules, as TLC evaluates
them (including Java overrides), with Apalache's own definitions of the same
operators.

## 1. Setting up a corpus

```sh
make community-modules
./bin/fuzztla init --corpus corpus-cm --library libraries/community-modules.toml
./bin/fuzztla run --how=pbt --corpus corpus-cm
```

`make community-modules` downloads the latest `CommunityModules.jar` release into
`libraries/community-modules/`, and writes `VERSION` with its release tag and
commit. Rerun it to pick up a newer release.

`init --library FILE` reads `[generator] classpath` and `custom_operators` from
FILE, whose paths are relative to FILE. It copies each classpath entry, plus a
sibling `VERSION`, into `<corpus>/tla/`, and writes the corresponding keys into
`config.toml`. Every other setting is the default. The corpus therefore keeps the
release it was initialized with; the replay manifest rejects a changed JAR. To
move to a newer release, initialize a new corpus.

## 2. Linkage

```toml
custom_operators = [
  { module = "FiniteSetsExt", operators = ["SumSet", "Max"], link = "instance" },
]
```

| `link` | Apalache input | Parser and TLC input |
| --- | --- | --- |
| `"inline"` (default) | the module's definitions | the same definitions |
| `"instance"` | the definitions Apalache imports for `EXTENDS <Module>`, which are its rewired ones for the modules it rewires | `INSTANCE <Module>` resolved on `generator.classpath`, with its Java overrides |
| `"diff"` | the definitions of `<Module>`, typechecked as root | `INSTANCE <tlc_module>`, a different module with the same operators ([recursive operators](recursive-operators.md)) |

With `link = "instance"`, the TLC source declares one named instance per module
and one alias per operator, for example:

```tla
Custom46…I == INSTANCE FiniteSetsExt
Custom46…N53… (p1) == Custom46…I!SumSet(p1)
```

`print --spec` shows this source, and `print --apalache-ir` the JSON Apalache reads. Replaying it outside fuzztla
requires `<corpus>/tla/CommunityModules.jar` on TLC's class path.

## 3. Selected operators

`libraries/community-modules.toml` selects the first-order operators of Apalache's
rewired `SequencesExt`, `FiniteSetsExt`, `Functions` and `BagsExt` that pass library
validation. Operators whose results depend on `CHOOSE` are included; their
disagreements may reflect underspecified semantics. `Reverse`, `ReplaceAll`,
`InsertAt`, `Zip`, `IsSuffix`, `IsStrictSuffix`, `SeqOf`, `TupleOf`, `BoundedSeq` and
`ReplaceAllSubSeqs` are excluded until [apalache-json-003](../../findings/apalache-json/apalache-json-003.md)
is fixed.

## 4. Findings

Cite the CommunityModules commit from `<corpus>/tla/VERSION` besides the fuzztla,
TLC and Apalache commits.
