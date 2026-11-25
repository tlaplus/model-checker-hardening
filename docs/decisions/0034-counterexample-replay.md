# 0034: Replaying counterexamples as corpus inputs

**Authors:** Igor Konnov and Claude

**Status:** Accepted; phases 1 to 3 implemented

**Date:** 2026-11-25

## Context

The aggregator compares verdicts ([ADR 0005][adr-0005]) and, for completed searches,
state counts ([ADR 0019][adr-0019]). Under conformance, a counterexample/counterexample
pair passes. Neither checker's trace is examined, so an entry passes even when one of the
two traces is not a behavior of the specification. A checker that reports the right
verdict with a wrong witness goes unnoticed.

The checkers record nothing of a trace except its length, `traceLength`
([ADR 0008][adr-0008]):
- TLC's error trace reaches only the worker's diagnostic buffer, which is kept only for a
  crash. `TlcMessageMetrics` counts the state messages and ignores their payloads.
- Apalache writes `violation1.itf.json`. `ApalacheTraceLength` counts its states, and the
  worker then deletes the job directory.

There are two places to check a trace:
1. **In the aggregator,** which would run a checker on the trace before judging the entry.
2. **As a new input,** which every checker of the corpus checks.

This ADR chooses the second. A trace is checked by every checker, including the one that
reported it, since that checker may be wrong too. The replay is an entry of its own,
stored, judged and exported like any other.

**Scope.** This ADR covers:
- the input kind and its payload;
- the module that replays a trace;
- the oracle;
- trace capture by TLC, TLC-n and Apalache;
- the candidate source and its place in the generation loop;
- the configuration.

Capturing traces of the generic checker ([ADR 0033][adr-0033]) and replay in `mt` corpora
are out of scope.

### Notation

| Symbol | Meaning |
| --- | --- |
| s0, ..., sn | the states of a recorded trace; it has n transitions |
| v1, ..., vm | the state variables of the base module, in declaration order, `step` included |
| ci_j | the TLA<sup>+</sup> literal of the value of vj in si |
| l | the loop index of a lasso: the behavior continues from sn as from sl, and sn = sl |

## Decision

### 1. Trace endings

A recorded trace ends in one of three ways. The ending decides what the replay checks in
the last state.

| Ending | Claim about sn | Source |
| --- | --- | --- |
| `invariant` | violates `Inv` | TLC names `Inv` as the violated invariant; Apalache reports a finite trace |
| `loop l` | equals sl | TLC reports "back to state l" or stuttering; Apalache reports a lasso |
| `prefix` | none | any other finite violation, such as TLC checking a safety part of `Prop` |

A lasso is normalized so that its last state repeats the loop state:
- TLC prints s0, ..., sk and then "back to state l". The normalized trace appends sl as
  s(k+1), so n = k + 1.
- TLC prints s0, ..., sk and then "stuttering". The normalized trace appends sk, with l = k.
- Apalache's lasso already ends in a copy of its loop state. Probe P2 confirms how its ITF
  marks the loop.

The replay of a lasso checks only that the lasso is a behavior: the transitions exist, and
the last state equals the loop state. It checks neither fairness nor the property. Whether
the lasso violates the property is a claim about the original checker, and conformance
already compares that claim with the other checkers.

### 2. Input kind `trace`

`InputKind.TRACE("trace")` names a fourth decoder. Its payload is a CBOR array in
deterministic encoding ([RFC 8949 §4.2][cbor-det]):

```cbor
[
  "module",            / base kind: "expr", "module" or "graph" /
  h'0123af',           / base payload: the parent's input bytes /
  [[...], ..., [...]], / states s0..sn, each one value per variable /
  ["loop", 2]          / ending: ["invariant"], ["loop", l] or ["prefix"] /
]
```

- **The base is embedded,** as the base of an `mt` payload is ([ADR 0016][adr-0016]). A
  `trace` entry therefore decodes without its parent.
- **Values are typed by the base module.** Decoding the base gives each variable's type,
  and that type decides how a value is read. The encoding is untagged:

  | Type | Encoding |
  | --- | --- |
  | `Bool` | CBOR bool |
  | `Int` | CBOR integer, or bignum (tags 2 and 3) |
  | `Str` | text |
  | model value | text, as printed: `name_OF_TYPE` |
  | `Set(T)` | array, sorted by the encoded bytes of the elements |
  | `Seq(T)`, tuple | array |
  | record | array of the field values, in the order of the record type's fields |
  | `a -> b` | array of `[key, value]` pairs, sorted by the encoded bytes of the keys |
  | variant | `[tag, value]` |

  The type also resolves an ambiguity of TLC, whose values do not tell a tuple from a
  sequence, or a sequence from a function on `1..n`.
- **The encoding is canonical.** Equal traces have equal bytes. When TLC and Apalache
  report the same trace, the two replays have one digest, so one entry replays both.
- **Malformed input is rejected.** A value that does not match its type, a wrong state
  width, a loop index outside `0..n`, or a malformed array raise `InputRejectedException`.
  The `trace` decoder is not total over arbitrary bytes, unlike the generators
  ([ir-generators.md §1][generators]). No candidate source produces arbitrary bytes for it,
  and the mutator never mutates a `trace` entry (section 6).
- **Tests pin the layout:** a round-trip test of encoder and decoder, and fixed-byte tests
  of each value form.

The value model is a sealed `TraceValue` and a record `Trace(states, ending)` in a new
package `gen.trace`. The package also holds the codec and the conversion of a value to typed
IR. It depends only on `gen` and `common`.

### 3. Replay module

`FuzzInputModule` assembles the replay from the base's skeleton:
- The base's initial predicate, next-state action and invariant become the definitions
  `BaseInit`, `BaseNext` and `BaseInv`. `BaseNext` keeps the stuttering disjunct
  ([ir-generators.md §9.3][generators]), so a stuttering lasso replays.
- A fresh variable `idx : Int` counts the replayed transitions. The decoders never draw
  that name.
- The entry points are redefined:

```tla
Init == v1 = c0_1 /\ ... /\ vm = c0_m /\ idx = 0 /\ BaseInit
Next ==
  \/ idx = 0 /\ v1' = c1_1 /\ ... /\ vm' = c1_m /\ idx' = 1 /\ BaseNext
  ...
  \/ idx = n - 1 /\ v1' = cn_1 /\ ... /\ vm' = cn_m /\ idx' = n /\ BaseNext
Inv == idx /= n \/ Last
```

`Last` is written as a positive formula, with no double negation:

| Ending | `Inv` |
| --- | --- |
| `invariant` | `idx /= n \/ BaseInv` |
| `loop l` | `idx /= n \/ <<v1, ..., vm>> /= <<cl_1, ..., cl_m>>` |
| `prefix` | `idx /= n` |

- **The trace's equalities come first,** before `BaseInit` and `BaseNext`. TLC evaluates a
  conjunction from left to right and binds an unbound primed variable at its first equality, so
  it binds every variable from a constant, and `BaseInit` and `BaseNext` only check values instead
  of enumerating successors that the constants would discard. Apalache's assignment finder
  takes the leftmost candidates, which are also the trace's. Apalache, however, desugars
  `UNCHANGED x` into the assignment `x' := x` and rejects a second assignment of a variable
  ("Manual assignment is spurious"). The replay module therefore writes every `UNCHANGED e` of
  the base as `e' = e`, which means the same, and both checkers check it against the trace's
  values.
- **One disjunct per transition.** A tuple of states indexed by `idx` would need a
  variable index into a heterogeneous tuple, which Apalache's type system rejects. n is at
  most the step bound, so the disjunction stays short.
- **Literals.** Each value becomes typed IR:
  - a set becomes `{...}`, and an empty set a typed `{}`;
  - a sequence or tuple becomes `<<...>>`, at its type;
  - a record becomes `[f |-> ...]`;
  - a variant becomes `Variant("Tag", ...)`;
  - a function with domain {d1, ..., dk} becomes
    `[x \in {d1, ..., dk} |-> CASE x = d1 -> r1 [] ... [] OTHER -> rk]`, and one with an
    empty domain becomes `[x \in {} |-> t]`, where t is the closed terminal of the range type.
- **Base kinds.** `module` and `graph` entries contribute their `GeneratedSpec`, and `expr`
  entries the skeleton of the expression wrapper. All three assemble the same way.
- **Check request.** n transitions, the invariant only, no temporal property, and no
  deadlock check. The `idx = n` state has no successor besides stuttering.

### 4. Oracle

A `trace` entry predicts a counterexample of exactly n transitions. If the trace is a
behavior and its last state satisfies the ending's claim, then:
- the module has exactly one path to `idx = n`;
- `Inv` fails only in its last state;
- every checker, shortest-search or not, reports it with `traceLength = n`.

Otherwise no reachable state violates `Inv`, and the checker passes. The failing cases are:
- an infeasible initial state or transition;
- a last state that satisfies `BaseInv`;
- a lasso whose last state differs from its loop state.

The prediction is `ExpectedOutcome.counterexample(n)`, and `Oracle.GRAPH`
([ADR 0023][adr-0023] §5) judges it unchanged. That oracle requires agreement, including
the state counts, and every result admitted by the prediction. Its javadoc names both
kinds of entry it judges.

A `trace` entry is judged by prediction under every technique. Unlike a `graph` entry,
which a `pbt` corpus judges by conformance, it exists only to test its prediction.
- The fact belongs to the kind: `InputKind.derived()` holds for `TRACE`. No technique
  generates a derived kind, and `[generator] kind` rejects it.
- `Oracle.forKind(InputKind)` returns `GRAPH` for a derived kind, and the oracle itself
  otherwise. `AggregationInput` holds the entry's kind, and its `verdict` asks for that kind's
  oracle.
- `GraphOutcomes` becomes `PredictedOutcomes`, which predicts both kinds: a `graph` entry by
  decoding it, and a `trace` entry from its payload's states, without decoding the base.
  Recovery judges again through the same path.

`ExpectedOutcome.admits` accepts `fail`, which a partial term causes on any input. Whether a
replay should admit it is open until probe P3. The original checker evaluated the same
expressions in the same states without failing.

### 5. Capturing traces

`CheckRequest.recordsTrace` asks a checker worker for the trace of a counterexample. The
stage sets it while `[workflow] replay` holds, through `StageEnvironment.recordsTraces`,
and never for a `trace` entry. The worker protocol carries the flag (version 9).

- **Worker result.** `ToolResult.trace` is the recorded trace as JSON:

  ```json
  {"ending": "invariant" | "loop" | "prefix", "loop": l, "states": [{"x": v, ...}, ...]}
  ```

  Its values follow the [Informal Trace Format][itf]. A lasso already ends in a copy of its
  loop state. Above 256 KiB the trace is dropped. This JSON travels from the child worker to
  the stage and is not stored.
- **TLC and TLC-n.** A second message recorder, `TlcTraceRecorder`, subscribes beside the one
  that measures exploration. TLC broadcasts every message to both. Probe P1 settled which
  messages it reads:

  | TLC message | Use |
  | --- | --- |
  | 2110: invariant violated in a behavior | the ending is `invariant` when it names `Inv`, else `prefix` |
  | 2107, 2108: invariant or property violated initially | the state is not attached |
  | 2216, 2217: a trace state | its `TLCState` |
  | 2122: back to state l | a lasso to state l − 1; state l − 1 is appended |
  | 2218: stuttering | a lasso to the last state, which is appended again |

  A violation in an initial state comes without a `TLCState`. TLC writes every initial
  state before it checks the invariant there, so the violating state is the last one
  `TlcStateMetrics` saw. `TlcItfValues` converts TLC's values: a tuple as an array, a record
  as an object, a function as `#map`, and any other enumerable set as `#set`. TLC's
  `Variant(t, v)` is the function `t :> v`.
- **Apalache.** `ApalacheWorkerMain` reads `violation1.itf.json` before it deletes the job
  directory. `ApalacheTrace` replaces `ApalacheTraceLength` and gives both the trace and its
  length. Probe P2 showed how the temporal encoding marks a lasso. It adds variables whose
  names begin with `__`. `__InLoop` turns true in the state after the loop state, and the
  last state repeats the loop state. ITF has no `loop` field. The recorded trace drops the
  `__` variables. It is a lasso when the last state equals the state before `__InLoop`
  first holds, and otherwise an `invariant` violation, the only invariant Apalache checks.
- **Normalization.** `ItfTraces` reads the JSON at the types of the module's variables, which
  `GeneratedInputPreparation` returns with the tool input. The type settles what TLC does
  not distinguish:
  - a sequence may arrive as an array, `#tup`, or a `#map` on `1..n`;
  - a function may arrive as a `#map`, an array, or a record with string keys;
  - an integer may arrive as a number or `#bigint`.

  Sets and function pairs are ordered by the encoded bytes of their elements and keys.
  `RecordedTraces` then encodes the trace as `[states, ending]`
  (`TracePayload.encodeTrace`). In these cases no trace is stored, which, like a missing
  `traceLength`, changes no verdict:
  - a value it cannot convert;
  - a variable missing from a state;
  - a trace over the cap.
- **Storage.** `stages.<checker>.trace` holds the encoded trace as a byte string, allowed
  only with `counterexample`. `StageRecord` carries it as an opaque `EncodedTrace`, and
  `CorpusEnvelopeCodec` reads and writes it. The aggregator's merge keeps every branch's
  stage map, so an aggregated entry holds the traces of every checker.
- **The generic checker** records no trace. Its formats are tool-specific
  ([ADR 0033][adr-0033], Alternatives). It still checks every `trace` entry.

### 6. Replay source

Replays of generation g join generation g + 1, on top of its `generation_size`.

- **When.** The loop collects the replays of g + 1 after it has gated g, so every entry of
  g has settled. `ReplayCandidates.collect` reads the aggregated entries of g in
  `03aggregator-*` and `04quality-*`, whether they passed or failed, and builds one
  `trace` payload per recorded trace. The following are not sources:
  - entries with a crash, which are not aggregated;
  - `trace` entries, so a replay is never replayed.

  This reads the aggregated directories once per generation. It is the one scan of
  finished entries between generations, and it runs only with replay.
- **Order.** Traces are ordered by parent digest, then by `CorpusStage` order. Equal
  payloads are kept once, and so are payloads the corpus already holds
  (`CorpusDirectory.contains`). Replay draws no randomness, so a resumed run computes the
  same list ([ADR 0026][adr-0026]).
- **Targets.** `EntryOrigin.REPLAY` is outside `EntryOrigin.SIZED`, the origins that share a
  generation's size, so `GenerationTargets` gives it no range. The replays go to the input
  stage in one `GenerationPlan` of kind `trace`, numbered from `size(g + 1)` upward, with one
  quota of one target per replay. Each target has exactly one candidate, a
  `ReplayCandidates` source whose `Target.retries()` is false.
  - Richness rejection does not apply: a replay keeps its parent's cohort and records its
    own richness.
  - The request-frame limit and the known-defect signatures apply.
  - A candidate that is not stored, as a duplicate, a known defect or a rejected input,
    settles its target without an entry. The input stage reports
    `WorkflowEvents.skipped`.

  `GenerationProgress` counts the admitted entries of the sized origins and the replay
  targets apart. The loop waits for both: `awaitAdmitted` for the size, `awaitReplays` for
  the replays admitted or skipped.
- **Provenance.** A replay records `gen.parent` and `gen.operators = ["replay"]`. Like
  `adopt`, `MutationOperator.REPLAY` is provenance only and is never drawn. Which checker
  reported the trace follows from the parent's stage map.
- **Resumption.** A resumed run recomputes the list and leaves out what the corpus holds. A
  generation is short of entries when its sized entries fall below `generation_size`.
  Replays do not make up for missing entries.
- **Last generation.** After the last generation that `workflow.max_entries` allows, the
  loop runs one more generation, of size 0, while it has replay targets. Its entries are
  `trace` entries, so nothing follows it.
- **Limits.**
  - `workflow.max_entries`, and the global checks of `OccupancyLimits`, count
    `CorpusInventory.sizedEntries`: every entry but the replays.
  - Replays are bounded by g itself: at most one per recording checker and entry, so at
    most three times `generation_size` with `tlc`, `tlcn` and `apalache`. Deduplication
    and counterexample rates make the number lower in practice.
  - The per-checker `max_entries` still apply, since replays occupy checker capacity.
- **Quality gate.** A `trace` entry is not admissible, and the selection fraction applies
  to the generated entries alone. A replay therefore moves to `04quality-fail` and takes no
  parent's place. `ParentPool` loads only the run's kind, so the mutator never sees one.

### 7. Configuration

One key in `[workflow]`, beside `technique` and `checkers`, off by default:

```toml
[workflow]
replay = false
```

It was first proposed as a table `[replay]` with one key, `enabled`. A boolean beside the
other keys that shape the whole corpus is the smaller change. Loading rejects
`replay = true` in two cases:
- with `technique = "mt"`. Its counterexamples are traces of a relation module whose base
  payload carries a rewrite, and section 3 does not assemble it.
- with a generic checker whose `errors = "nonzero"`. `CheckingPolicy` already forbids
  prediction with an exit-status-only checker ([ADR 0033][adr-0033] §7).

### 8. Architecture

This is the first flow from checker outputs back into the input stage. In the diagram of
[fuzzing-workflows.md §1.1][workflows], the aggregated directories gain an edge to the
input stage, beside the mutator's edge from `04quality-pass`. The envelope (§2.4, §2.5)
gains `stages.<checker>.trace` and the operator `replay`. [ir-generators.md][generators]
gains the `trace` kind as a decoder outside the byte-generator protocol (section 2). Both
documents change with the phase that implements each part.

## Probes

The implementation phases follow three probes. Each uses at least 200 counterexample
entries of an existing `pbt` corpus whose modules have temporal properties.

- **P1. TLC capture.** Capture the traces through the recorder. Measure:
  - which value classes occur;
  - how many traces are lassos, and of which form;
  - how often the violated invariant is `Inv`.
- **P2. Apalache ITF.** Read the ITF of the same entries. Determine how a lasso and its
  loop are represented, which variables the temporal encoding adds, and which values do
  not convert.
- **P3. Replay.** A scratch renderer in the `cli` package builds the replay modules in one
  JVM. TLC, Apalache and Manyan check them, and the probe measures:
  - verdicts, contradicted predictions and failures;
  - the cost against the parent's checks.

  P3 decides whether a replay admits `fail` (section 4).

## Implementation phases

1. `gen.trace`, `InputKind.TRACE`, the replay module, `PredictedOutcomes`, the oracle per
   kind, and `fuzztla print` for `trace` entries.
2. Capture: `[workflow] replay`, the `ToolResult` field and its protocol, the TLC recorder,
   the Apalache ITF reader, normalization, and `stages.<checker>.trace`.
3. The source: `EntryOrigin.REPLAY`, the replay targets of `GenerationLoop` and
   its last generation, `OccupancyLimits`, and the quality gate. The architecture
   documents change here (section 8).
4. The database view, the campaign report, and a triager label for contradicted replays.

Each phase is a branch of its own, stacked on the previous one. Types that carry a new
`@Key` get an `@ArchRole`.

## Alternatives considered

- **Replay in the aggregator.** The aggregator is a fan-in point without tool workers
  ([ADR 0001][adr-0001]). Running a checker there would put tool processes, timeouts and
  crashes into it. Its result would also be a field of the parent rather than an entry
  that the test suite keeps.
- **Each tool's own replay:** TLC's trace explorer, or Apalache's `--init` from an ITF file.
  Each replays only its own traces, so neither checks one tool's trace with another.
- **ITF JSON as the payload.** ITF is readable, but it is not canonical and is ambiguous
  without types, so equal traces from TLC and Apalache would not deduplicate.
- **`BaseNext` before the trace's equalities.** TLC would enumerate every successor of
  `BaseNext` and then discard all but one.
- **States as a tuple indexed by `idx`.** It is shorter, but Apalache cannot index a
  heterogeneous tuple with a variable.
- **Replays as a share of `generation_size`.** This bounds the cost, but it lets replays
  displace generated inputs. Replays on top of a generation keep the generated stream
  unchanged when replay is switched on.

## Consequences

- **A wrong witness becomes a failure.** A trace that is not a behavior, or that does not
  violate `Inv`, fails its replay. This holds even when both checkers reported a
  counterexample, and even when the checker that reported it replays it.
- **One-sided disagreements get a witness.** When the other checker accepts the replay of a
  counterexample/pass entry, the trace is a behavior, and the checker that passed missed it.
- **Cost.** With replay enabled, each generation is followed by up to one replay per
  recorded trace. A replay is a single path of at most `max_steps` transitions, so each
  check is cheap, but every configured checker runs it.
- **Envelope size.** Each counterexample stores its trace, about the size of a state times
  n.
- **The `trace` decoder rejects bytes.** It is the first decoder that is not total, and the
  mutator never feeds it.
- **The generic checker is checked but not checked against.** Its own counterexamples are
  not replayed until a later ADR reads its trace format.

[adr-0001]: 0001-stages-and-workers.md
[adr-0005]: 0005-counterexample-verdict.md
[adr-0008]: 0008-exploration-metrics.md
[adr-0016]: 0016-metamorphic-testing.md
[adr-0019]: 0019-state-count-agreement.md
[adr-0023]: 0023-graph-based-testing.md
[adr-0026]: 0026-reproducible-runs.md
[adr-0033]: 0033-generic-checker.md
[cbor-det]: https://www.rfc-editor.org/rfc/rfc8949.html#name-deterministically-encoded-c
[itf]: https://apalache-mc.org/docs/adr/015adr-trace.html
[generators]: ../architecture/ir-generators.md
[workflows]: ../architecture/fuzzing-workflows.md
