---
state: open
labels: [apalache]
---

# JSON reader cannot read `ApalacheInternal` operators from Apalache's community-module wiring

## Summary

Apalache replaces several TLA+ Community Modules with its own definitions
(`__rewire_*_in_apalache.tla`). Some of these definitions use the internal
operators `ApalacheInternal!__ApalacheSeqCapacity` and
`ApalacheInternal!__NotSupportedByModelChecker`. `typecheck --output` writes
them to JSON, but the JSON reader does not know them, so Apalache cannot read
its own output. A module that extends `SequencesExt` checks successfully as
TLA+ source and fails as the JSON produced from that source with
`key not found: ApalacheInternal!__ApalacheSeqCapacity`.

Observed with Apalache 0.62.2 (release CLI) and Apalache main at
1371658a2c7377b43882b82764ea7720ec689e21. Found by fuzztla at
56933a3f314ec25fd69f7096deae944aad912d9f while preparing a community-module
operator library; TLC is not involved (tla2tools rev 30cc360).

## Reproduction

Save as `RT.tla`:

```tla
---- MODULE RT ----
EXTENDS SequencesExt
VARIABLE
  \* @type: Seq(Int);
  x
Init == x = <<1, 2>>
Next == UNCHANGED x
Inv == Reverse(x) = <<2, 1>>
====
```

Run:

```sh
apalache-mc check --length=0 --init=Init --next=Next --inv=Inv RT.tla
apalache-mc typecheck --output=RT.json RT.tla
apalache-mc check --length=0 --init=Init --next=Next --inv=Inv RT.json
```

The first two commands exit with `EXITCODE: OK`. The third fails:

```text
  > Error parsing file RT.json
  > key not found: ApalacheInternal!__ApalacheSeqCapacity
EXITCODE: ERROR (150)
```

The same failure occurs through `JsonToTla` in the Java I/O facade
(`TlaJson.readModule`). Because the reader rejects the whole module, one
affected definition makes every operator of the module unreadable, including
operators that do not use internal operators.

## Affected definitions

Declarations in the typechecked output of `EXTENDS <Module>` that contain an
internal operator:

| Module | Operator | Internal operator |
| --- | --- | --- |
| `SequencesExt` | `Reverse`, `ReplaceAll`, `InsertAt`, `Zip`, `SetToSortSeq` | `__ApalacheSeqCapacity` |
| `SequencesExt` | `TupleOf`, `SeqOf`, `BoundedSeq`, `ReplaceAllSubSeqs` | `__NotSupportedByModelChecker` |
| `BagsExt` | `SubBag`, `FoldBag`, `MapThenFoldBag` | `__NotSupportedByModelChecker` |

`IsSuffix` and `IsStrictSuffix` call `Reverse` and are affected as well.
`FiniteSetsExt` and `Functions` round-trip.

## Root cause

`StandardLibrary` maps `__apalache_internal!__NotSupportedByModelChecker` and
`__apalache_internal!__ApalacheSeqCapacity` to
`ApalacheInternalOper.notSupportedByModelChecker` and
`ApalacheInternalOper.apalacheSeqCapacity`. `TlaToJson` writes them by name.
`JsonToTla` resolves operator names through `Builder.byName`, whose `m_nameMap`
registers other `ApalacheInternalOper` members (`distinct`, `selectInSet`,
`storeInSet`, …) but not these two. `m_nameMap(operatorName)` then throws
`NoSuchElementException`.

## Expected behavior

Every operator that `TlaToJson` can write should be readable by `JsonToTla`.
Register both operators in `Builder.m_nameMap`, and add a round-trip test over
the typechecked output of every `__rewire_*` module, so that a new internal
operator cannot reintroduce the failure.

## Impact

Tools that exchange Apalache's typed JSON, including fuzztla's Apalache stage,
cannot use the most common `SequencesExt` operators (`Reverse`, `InsertAt`,
`Zip`, …) or any module that extends `SequencesExt` or `BagsExt` without pruning
unused declarations. Users who run `typecheck --output` and later `check` the
JSON hit the same error.
