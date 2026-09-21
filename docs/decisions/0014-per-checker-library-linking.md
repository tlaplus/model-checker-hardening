# 0014: Per-checker linking of instance-linked libraries

**Authors:** Igor Konnov and Claude

**Status:** Implemented

**Date:** 2026-09-21

## Context

Apalache replaces several TLA+ Community Modules with its own definitions:
`__rewire_{sequences_ext,finite_sets_ext,functions,bags_ext,folds}_in_apalache.tla`
in the pinned distribution define `SequencesExt`, `FiniteSetsExt`, `Functions`,
`BagsExt` and `Folds`. TLC evaluates the community definitions, or their Java
overrides when `CommunityModules.jar` is on its class path. Whether the two agree
is a conformance question that users depend on and that nobody tests
systematically.

Custom operators ([fuzzing-workflows.md §1.2][workflows]) cannot answer it. Library
preparation typechecks one source, and every checker input is self-contained,
with that source's definitions inlined. Apalache reads JSON IR, so its name-based
rewiring never runs at check time. As a result, TLC and Apalache evaluate the same
inlined bodies.

### Probe

Probed with Apalache 0.62.2, tla2tools rev 30cc360, and CommunityModules release
202609120237.

- Typechecking a wrapper `MODULE W EXTENDS SequencesExt` yields Apalache's
  rewired bodies (`Reverse` via `MkSeq`, `SetToSeq` via `ApaFoldSet`), even with the
  community sources in the same directory. Typechecking the community
  `SequencesExt.tla` as the root yields the community bodies and fails the type
  check.
- TLC parses and checks a module with `EXTENDS Integers, Sequences, FiniteSets, TLC,
  Apalache, Variants` and `CM == INSTANCE SequencesExt`, and it applies the Java
  overrides through the named instance. With the JAR,
  `FiniteSetsExt!SumSet(1..3000)` completes in 0.4 s; with the sources only, it
  overflows the stack.
- All 7 rewired `FiniteSetsExt` operators and all 12 rewired `Functions` operators
  pass library validation.
- The JSON of any module extending `SequencesExt` or `BagsExt` is unreadable,
  because Apalache's JSON reader lacks two internal operators
  ([apalache-json-003][]). `Reverse`, `ReplaceAll`, `InsertAt` and `Zip` use one of
  them.

## Decision

1. A `custom_operators` entry takes `link = "inline"` (default) or
   `link = "instance"`.
2. An instance-linked module is typechecked through a generated wrapper
   `EXTENDS <Module>`, so the library holds what Apalache itself would evaluate.
   Signatures come from the same import, so generation is unchanged.
3. Library JSON is pruned to the selected operators' declaration closure before it
   is decoded. Unused declarations, including those with operators the reader
   rejects, no longer make a module unusable. The pruning applies to both
   linkages.
4. Apalache's JSON input stays self-contained: all used definitions are inlined.
5. The TLA+ source for the parser and TLC declares a named instance per used
   instance-linked module, and an alias for each used export:
   `<export>(p1, …, pn) == <instance>!<Operator>(p1, …, pn)`. Generated call sites
   are unchanged. Instance and alias declarations are spliced as text after
   `EXTENDS`, because the IR printer cannot express `INSTANCE` or `!`.
6. When a module is instance-linked, the library's classpath entries precede the
   class path of the parser and TLC workers. TLC therefore loads the community
   sources and their Java overrides, as users run it.
7. The replay manifest records each module's linkage. With instance linkage, it
   also records the SHA-256 of every classpath entry, because class files now
   affect TLC.
8. `fuzztla init --library FILE` reads `[generator] classpath` and
   `custom_operators` from FILE and copies the classpath entries into
   `<corpus>/tla/`. Each corpus thus freezes one library release.
   `make community-modules` downloads the latest `CommunityModules.jar` and records
   its tag and commit. `libraries/community-modules.toml` selects the rewired
   operators that pass validation.

Richness scoring and known-defect matching remain on the inlined IR, so admission
does not depend on linkage. Without instance-linked modules, every output is
byte-identical to the previous implementation.

## Consequences

- A disagreement localizes to the Apalache wiring or Apalache itself, versus the
  community definition or its TLC override. Findings cite the CommunityModules
  commit from `<corpus>/tla/VERSION`.
- Checker inputs of instance-linked corpora are no longer self-contained: replaying
  a TLC input needs the corpus's `tla/` directory on the class path.
- Operators whose results depend on `CHOOSE` (`SetToSeq`, `Inverse`,
  `AntiFunction`, and `Max`/`Min` on an empty set) are included on purpose. They
  may yield disagreements that reflect underspecified semantics rather than
  defects; triage decides.
- With pruning, 20 rewired `SequencesExt` operators and all 5 `BagsExt` operators
  pass validation. `Reverse`, `ReplaceAll`, `InsertAt`, `Zip`, `SeqOf`, `TupleOf`,
  `BoundedSeq` and `ReplaceAllSubSeqs` use an unreadable internal operator, and
  `IsSuffix` and `IsStrictSuffix` call `Reverse`. They stay excluded until
  apalache-json-003 is fixed.

[workflows]: ../architecture/fuzzing-workflows.md
[apalache-json-003]: ../../findings/apalache-json/apalache-json-003.md
