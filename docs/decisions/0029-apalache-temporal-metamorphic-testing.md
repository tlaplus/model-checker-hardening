# 0029: Apalache in temporal metamorphic testing

**Authors:** Igor Konnov and Claude

**Status:** Implemented

**Date:** 2026-11-04

**Implementation.** Implemented on the branch `igor/mt-temporal-apalache`. It settled
these details:
- **`init` leaves `max_fairness` unchanged** (section 3). An `mt` corpus decodes its
  base's payloads with its own generator settings, and `AdoptableBase` refuses a base
  whose `[generator]` table differs. The fairness signature alone keeps `WF` and `SF`
  away from Apalache.
- `FuzzInputModule.SideEntryPoints` names, per side, the `Init`, `Next`, specification
  and property that an invocation reads. It replaces two switches that named the
  specification and the property.
- `ApalacheWorkerMain` folds the runs. Each property run writes below a directory of
  its own, so the trace length is read from that run's output.
- `ApalacheCheckerBackend` gives a request `CheckKind.invocations()` times the stage's
  timeout. `ProcessToolWorker` is unchanged, since the parser uses it too.
- `fuzztla print` lists the Apalache invocations under `apalache runs:`, after the TLC
  runs.
- `TemporalRelationCheckersTest` runs an equivalent and a strengthened property through
  the Apalache worker: the first passes and the second is "property verdicts differ".
- **The triager** also reads TLC's `The property of PropC is equal to FALSE` as
  [constant-property-tlc-rejects](../../conformance/constant-property-tlc-rejects.md).
  In a TLC-only corpus, this detail never met an Apalache verdict.
- **Smoke run.** FuzzTLA `e7ca0f06`, `init --how=mt --temporal` without a base, so with
  the default `known-defects.toml` and checkers `tlc` and `apalache`, `max_entries`
  600:
  - 600 entries in 8 min 49 s. 215 checked entries had a property, and 84 a temporal
    rewrite.
  - On the 84, Apalache passed 73, reported 2 counterexamples and crashed once. TLC
    passed 37, failed 32 and crashed 7. No property run reported different verdicts.
  - Both Apalache counterexamples are relation counterexamples with a TLC `fail`, from
    partial operations that Apalache evaluates in both copies
    ([function-application-outside-domain](../../conformance/function-application-outside-domain.md)).
  - The triager attributed all 195 aggregator deviations to known documents.
  - The median Apalache time was 2.0 s with a temporal rewrite and 1.0 s without.
  - `temporal-apalache-enabled` quarantined 51 entries as their first signature.

## Context

[ADR 0027][adr-0027] checks a rewritten temporal property against its original by
checking each side's property under that side's specification, `SpecE ⊨ PropE` and
`SpecC ⊨ PropC`. Equal verdicts pass. Only TLC is tested: `run` refuses
`temporal_rules` when Apalache is a checker, and `init --temporal` drops it, because
Apalache supports no fairness.

Apalache checks a fragment of temporal logic. It supports `[]`, `<>`, `~>`, `[][A]_v`
and `<><<A>>_v` over `Init` and `Next`, and rejects `ENABLED`, `WF` and `SF`
([conformance][enabled], [conformance][fairness]). Conformance corpora already ask it
for `--temporal=Liveness` ([ADR 0007][adr-0007]). That has found five open
`apalache-temporal` findings.

**Scope.** This ADR proposes:
- running the property runs of ADR 0027 §4 in Apalache too;
- a corpus that checks with both TLC and Apalache;
- how such a corpus stays inside Apalache's fragment.

It does not change the rules, the relation module of ADR 0027 §3, or the oracle.

### Probes

**Setup.**
- FuzzTLA `4feb6f8e` for decoding and rendering. TLC `309e489` (tla2tools
  `2026.11.03`). Apalache 0.62.3 (build `3eb15b2`).
- Corpus: corpus79, `mt --temporal` over a `module` base with `max_steps = 5`,
  checkers `tlc` and `tlcn`, signatures `all-defects.toml`. It holds 53,695 entries.
- Sample: every entry that applied a temporal rule and has no `ENABLED`, `WF` or
  `SF`, as the corpus database ([corpus database][corpus-db]) records them. That
  gives 733 entries.
- Each entry was rendered once as Apalache's IR (`fuzztla print --apalache-ir`).
  Apalache then ran from the command line, in a fresh JVM per run, with a 120 s
  timeout:
  - P_E: `--init=Init --next=Next --temporal=Prop`;
  - P_C: `--init=InitC --next=NextC --temporal=PropC`;
  - each at `--length=6`, which is `max_steps + 1` as for a temporal check today,
    and at `--length=7`.

**P0: the signatures quarantined every temporal relation.**
`apalache-always-action-bound` ([`apalache-temporal-005`][f005]) excluded
`[][Next]_vars` and `[][Step]_vars` but not `SpecC`'s `[][NextC]_vars`. It matched
all 2,120 relation modules with a property in corpus79. TLC checked none of the
1,312 entries with a temporal rewrite. Commit `dbd3f242` excludes `NextC`, and
`KnownDefectScaffoldingTest` covers temporal relations.

**P1: verdicts of the two property runs at `--length=6`.**

| Both runs | Entries |
| --- | ---: |
| decided, equal verdicts | 528 |
| decided, different verdicts | 4 |
| one decided, one failed | 38 |
| neither decided | 163 |

- **Three of the four differences** involve `CHOOSE` or `VariantFilter`. Apalache
  evaluates both non-deterministically, and `metamorphic-choose` and
  `variant-filter` quarantine them.
- **The fourth, `235cdb54`, is an Apalache defect.** `AlwaysOrBreak` turns the
  antecedent `var0` of a `~>` into a formula that contains `var0 /\ <>(~var0)`.
  Apalache misses the violation of this rewrite at the lasso length. Reduced, it
  reports counterexamples to valid properties such as `[]((x /\ <>x) => x)`
  ([`apalache-temporal-006`][f006]). The new signature
  `apalache-eventually-in-conjunction` quarantines the shape.
- **One-sided failures.** 32 of the 38 are `apalache-temporal-001`, a `~>` nested in
  a `~>` on one side only. The other 6 are single evaluation errors.
- **Neither decided.** 138 of the 163 are `apalache-temporal-001`.

**P2: the bound.** Length 7 changed no verdict compared with length 6. One run
timed out at 6 and failed at 7. With each side's property checked directly, rather
than as `Fairness => Prop`, the property runs need no extra step for
[`apalache-temporal-005`][f005]. `apalache-temporal-006` needs more steps, but its
signature quarantines it.

**P3: what the signatures leave.** Each sampled entry was matched against
`all-defects.toml` (with P0's fix and P1's new signature) and
`metamorphic-defects.toml`. For 720 of the 733 entries, the match succeeded. In the
table, a *TLC signature* has the prefix `tlc-`. An *Apalache signature* has the
prefix `apalache-`, or is `metamorphic-choose` or `variant-filter`. Every other
signature counts as *other*.

| Signatures that match | Entries |
| --- | ---: |
| a TLC and an Apalache signature, with or without others | 522 |
| an Apalache signature, with or without others, no TLC signature | 142 |
| TLC signatures only | 35 |
| a TLC signature and others, no Apalache signature | 10 |
| other signatures only | 11 |
| none | 0 |

`apalache-eventually-in-conjunction` alone matches 523. `AlwaysOrBreak` and
`EventuallyWeaken` carry the highest temporal weights (ADR 0027 §6), and each
produces `F /\ <>G` under a temporal operator. Signatures quarantine the whole
module, for every checker. With the TLC signatures ignored, as in a corpus without
TLC, 35 entries remain, and all have equal verdicts.

**P4: cost.** Median time per Apalache run, with JVM startup: 5.6 s for a pass,
4.2 s for a counterexample, 2.8 s for an error. The 90th percentiles are 20.1 s,
11.4 s and 14.0 s. 13 of 2,932 runs reached the 120 s timeout.

## Decision

### 1. Property runs in Apalache

An Apalache stage that receives `CheckKind.RELATION_AND_PROPERTIES` runs the relation
of [ADR 0016][adr-0016] §3 and then the property runs of ADR 0027 §4. It folds the
outcomes with the same fold as TLC, and the oracle is unchanged.

| Run | Apalache arguments |
| --- | --- |
| relation | `--init=Init --next=Next --inv=Inv,Step --length=n` |
| P_E | `--init=Init --next=Next --temporal=Prop --length=n+1` |
| P_C | `--init=InitC --next=NextC --temporal=PropC --length=n+1` |

- **Each side's property is checked directly,** not as `Fairness => Prop`. Section 3
  quarantines every fairness condition, so `Fairness` and `FairnessC` are `TRUE`. A
  `TRUE =>` on one side only would also trigger `apalache-temporal-005` (P2).
- **`n` is the longest path of the step counter,** as for every Apalache check
  (`CheckRequest.unrollingLength`).
- **The fold runs in the Apalache worker process,** as one request. The worker is
  persistent ([`ToolBackend`][backend]). If its JVM dies during any of the three
  runs, the whole input is a crash, and the stage retires the worker as before. The
  request's timeout is the stage timeout times the number of invocations, up to 3.
- **The fold moves** from `workflow.tlc` to `workflow.tool`, which both backends use.
- **Exploration metrics** come from TLC's relation run, as before. Apalache reports
  no metrics but a trace length.

### 2. Checkers

`run` accepts `temporal_rules` with Apalache among the checkers. `init --how=mt
--temporal` keeps the base's checkers, including Apalache. Its other conditions
(ADR 0027 §5) stay.

A corpus that checks with TLC and Apalache compares TLC's folded outcome with
Apalache's, as every `mt` corpus does ([metamorphic testing][mt-manual] §3).

### 3. Staying in Apalache's fragment

With Apalache among the checkers, `init --temporal` writes:

```toml
[metamorphic]
temporal_weights = { WFDef = 0, SFDef = 0 }

[workflow]
known_defects = ["../signatures/all-defects.toml",
                 "../signatures/metamorphic-defects.toml",
                 "../signatures/temporal-apalache-defects.toml"]
```

The new database `temporal-apalache-defects.toml` quarantines `ENABLED`, `WF` and
`SF` anywhere, citing the conformance notes on [`ENABLED`][enabled] and
[fairness][fairness]. Like `metamorphic-defects.toml`, it would quarantine valid
inputs of other corpora, so `all-defects.toml` does not contain it.

The generator is unchanged, as is `max_fairness`, which an `mt` corpus shares with its
base. A module may still draw `ENABLED`, `WF` or `SF`, and the signature then
quarantines it.

## Alternatives considered

- **Apalache only.** Without TLC's temporal-shape signatures, P3 leaves 35 of 720
  entries instead of 0. TLC would lose nothing, since TLC-only `--temporal` corpora
  remain. Rejected in favor of one corpus that checks both. The yield of either
  depends on `apalache-temporal-006` being fixed.
- **Signatures per checker.** Quarantining a module for one checker only would let TLC
  check what Apalache cannot, and the reverse. The aggregator compares checkers, so a
  verdict missing for one checker needs a rule of its own. This is outside this ADR.
- **A generator option without `ENABLED`, `WF` and `SF`.** It wastes no candidates,
  but it changes the byte layout and the generator.
- **`Fairness => Prop` as today.** P_E would check `TRUE => Prop` and P_C `TRUE =>
  PropC`. That is symmetric, but it needs one more step for every `[][A]_v`
  ([`apalache-temporal-005`][f005]), which the signature then quarantines.

## Consequences

- **Low yield until Apalache is fixed.** With `all-defects.toml`, no probe entry would
  reach the checkers (P3). With the default `known-defects.toml`, which lacks the
  signatures of 006 and 001, 84 of 600 smoke entries with a temporal rewrite did, and
  no verdicts differed. The probe itself found `apalache-temporal-006`. Once 006 and
  001 are fixed, their signatures are removed, and the pipeline needs no change.
- **TLC loses fairness coverage in such a corpus.** `WFDef` and `SFDef` are off, and
  modules with fairness are quarantined. TLC-only `--temporal` corpora keep them.
- **Cost.** An entry with a property costs up to three Apalache runs. They run in one
  persistent worker, with no JVM startup per run.
- **Deviations.** This ADR replaces ADR 0027 §5's refusal of Apalache and its
  consequence "Only TLC is tested". ADR 0027 carries a note pointing here.

[adr-0007]: 0007-levels-and-temporal-properties.md
[adr-0016]: 0016-metamorphic-testing.md
[adr-0027]: 0027-temporal-metamorphic-testing.md
[backend]: ../../src/main/java/io/github/tlaplus/hardening/workflow/tool/ToolBackend.java
[corpus-db]: ../manual/corpus-database.md
[enabled]: ../../conformance/enabled-apalache-unsupported.md
[fairness]: ../../conformance/fairness-apalache-unsupported.md
[f005]: ../../findings/apalache-temporal/apalache-temporal-005.md
[f006]: ../../findings/apalache-temporal/apalache-temporal-006.md
[mt-manual]: ../manual/metamorphic-testing.md
