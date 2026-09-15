# 0008: Exploration metrics

**Authors:** Igor Konnov and Claude

**Status:** Proposed

**Date:** 2026-09-15

## Context

The general pipeline of [fuzzing-workflows.md §1.1][workflows] places a quality
gate after the conformance aggregator. The gate would move the entries of
`03aggregator-pass` that are worth keeping to `04quality-pass`. What makes an
entry worth keeping is still undecided: §1.3 and §1.4 say "Good quality gates are
to be found". This ADR proposes the measurements such a gate needs. It does not
propose the gate.

Agreement between TLC and Apalache is weak evidence when neither checker
explored anything. Corpus22 holds 35,089 agreeing entries: 9,818 pass/pass,
6,785 counterexample/counterexample and 18,486 fail/fail. The corpus records
nothing about how those verdicts were reached. `ToolResult` and protocol v5
carry a verdict, a failure code and bounded text, and `StageRecord` stores the
verdict, the code, a one-line detail and two timestamps. TLC's output is
discarded unless the verdict is a crash.

The two filtering mechanisms that exist do not address this. Known-defect
signatures ([ADR 0006][adr-0006]) quarantine IR shapes that a tool is known to
reject, and the triager labels crashes and disagreements. Both recognize known
*defects*. Neither recognizes an agreement that is *shallow*, meaning one that
does not exercise the checkers.

### Calibration

We re-ran TLC on a stratified sample of corpus22 and measured every metric
proposed below. The sample holds 700 random entries for each agreed verdict and
300 random entries of `03aggregator-fail`. Each module was rendered with
`fuzztla print --spec` and checked by `tlc2.TLC` from `target/fuzztla.jar`. The
arguments and configuration were those of `TlcWorkerMain` and
`TlcConfiguration`: `-workers 1 -deadlock -noGenerateSpecTE`, `SPECIFICATION
Spec`, `INVARIANT Inv`, and `PROPERTY Prop` when the module has a property. A
probe installed a first version of the state writer described below; it
counted non-stuttering actions instead of `actionsDiscovering`. The two agree on
whether the count is zero for every sampled entry (see [TLC
metrics](#tlc-metrics)), so the table is the same under either. No run timed
out. All 2,400
runs reproduced their stored TLC verdict under `TlcOutcomeClassifier`. Weighted
estimates scale each verdict's sample share to its corpus count.

Each entry was assigned the first shallow pattern it matches, in the order of
this table:

| Pattern | Definition | pass/pass | cex/cex | fail/fail | Est. of 35,089 | Disagreeing |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| Vacuous pass | verdict `pass`, `initStates = 0` | 599 | – | – | 8,401 (23.9%) | 8 |
| Initial-state violation | verdict `counterexample`, `phase = init` | – | 686 | – | 6,649 (18.9%) | 10 |
| Early failure | verdict `fail`, `phase = init` | – | – | 691 | 18,248 (52.0%) | 280 |
| No discovering action | `actionsDiscovering = 0` | 52 | 2 | 9 | 986 (2.8%) | 1 |
| Counter-only progress | `projectedStates ≤ 1` | 43 | 6 | 0 | 661 (1.9%) | 1 |
| None of the above | | 6 | 6 | 0 | 142 (0.4%) | 0 |

- **Initial-state violations.** 645 of the 686 violate `Inv` in an initial
  state (TLC error 2107). The other 41 violate `Prop` there (2108). Twelve
  counterexamples are reached after one transition, and two are liveness
  violations.
- **Early failures.** The fail/fail pairs agree on the failure code in 584 of
  700 cases. The other 116 are a TLC parse failure (150) against an Apalache
  evaluation failure (75). In all 116, TLC rejects a constant `FALSE` invariant,
  property or specification.
- **Entries matching no pattern.** None of the twelve exceeds 16 distinct
  states, 5 projected states, 7 value nodes per state or value nesting 3. Six of
  them are counterexamples at depth 1.
- **Richness.** None of the 952 agreeing entries in richness cohorts 5–9 has
  more than one projected state. Richer collection literals make `Init` fail or
  empty.

Across the agreeing sample, `maxStateNodes` has median 0 and maximum 20, and
`maxCardinality` has maximum 8. No value walk reached the node cap. Almost no run
leaves its initial states, so `max_steps = 5` rarely limits exploration.

## Decision

### Measurement is separate from policy

The TLC and Apalache stages measure each run and store the result in the entry.
No stage accepts or rejects an entry based on the metrics, and no configuration
controls them. A later ADR defines the quality gate. The gate will read only
stored metrics and the stored input, so its thresholds can be recalibrated over
an existing corpus without re-running a checker.

### TLC metrics

`TlcWorkerMain` collects the metrics in the child JVM through three public TLC
APIs:

- **State writer.** An `IStateWriter` installed with `TLC.setStateWriter` after
  `handleParameters`. TLC calls `writeState(s)` for every initial state, and
  `writeState(s, t, seen, action)` for every generated transition. The `seen`
  flag says whether `t` is new.
- **Message recorder.** An `IMessagePrinterRecorder` registered with
  `MP.setRecorder`. The `EC.TLC_INIT_GENERATED*` messages mark the completion of
  initial states. The calibration probe detected them by their text,
  "Finished computing initial states".
- **Checker counters.** `TLCGlobals.mainChecker` provides
  `getDistinctStatesGenerated` and `getStatesGenerated` after `process`
  returns.

| Field | Definition | Computed from |
| --- | --- | --- |
| `phase` | `init` if the run stopped before initial states were complete, `explore` if it stopped afterwards, `complete` if the search finished | recorder, error code |
| `initStates` | distinct initial states | writer |
| `distinctStates`, `generatedStates` | TLC's counters | checker |
| `projectedStates` | distinct new states after removing the step variable | writer: a second fingerprint per new state, over the other variables |
| `depth` | largest `TLCState.getLevel()` of a new state, minus the initial level | writer |
| `projectedDepth` | largest level at which a new projected state appeared | writer |
| `actions` | sub-actions in `ITool.getActions()` | checker |
| `actionsFired` | distinct source locations of the actions of generated transitions | writer |
| `actionsDiscovering` | distinct source locations of actions that produced a new state | writer, `seen` flag |
| `maxStateNodes` | largest value node count of a new state | writer, value walk |
| `maxCardinality` | largest set, sequence, tuple, record or function domain | value walk |
| `maxNesting` | deepest value nesting; a scalar is 0 | value walk |
| `saturated` | a value walk stopped at its node cap | value walk |
| `traceLength` | transitions in the counterexample, for `counterexample` only | recorder: `EC.TLC_STATE_PRINT*` messages of the error trace |

Three rules keep the collection cheap and well defined:

- **Action identity is the source location.** TLC creates one `Action` per
  binding of an existential parameter. On the calibration stress module
  (below), identity counted 13 progressing actions where 3 of the 4 disjuncts
  change the state. The writer caches `Action.getLocation()` per `Action`
  object, so each object is formatted once.
- **Stuttering transitions are not counted.** Detecting a stuttering transition
  means fingerprinting the source and target of every transition, which added
  60–70% to TLC time on the stress module. `actionsDiscovering` uses the `seen` flag
  instead. On the 116 calibration entries with transitions, it agreed with a
  non-stuttering test on whether any action progressed, and differed in the
  exact count in 2.
- **The value walk is bounded.** It visits new states only and stops at a node
  cap (the probe used 100,000), then sets `saturated`. A large state therefore
  cannot turn measurement into a timeout. The projection needs the name of the
  step variable, which today is the private constant
  `IrSpecGeneratorEngine.STEP_VARIABLE`; the implementation shares it. An `expr`
  module has no step variable, so there `projectedStates` equals
  `distinctStates`.

### Apalache metrics

Apalache stores `traceLength` for a counterexample. It is the number of states
in `violation1.itf.json`, minus one. The file lies in the run directory under
`--out-dir`, which `ApalacheWorkerMain` removes only after the next invocation.
Apalache explores no explicit state graph, so the TLC metrics have no Apalache
counterpart. Solver statistics require `--smtprof` and a log parser, and are not
collected.

### Derived, not stored

The gate derives these from stored data:

- equality of the two failure codes;
- equality of the two trace lengths;
- static IR features: node count over the evaluated roots, using
  `signature/IrTree.evaluatedSubexpressions`, and the set of expression kinds.
  Their purpose is diversity, not depth.

### Storage and transport

Each checker stores its metrics as a CBOR map in `stages.tlc.metrics` or
`stages.apalache.metrics`, for the `pass`, `counterexample` and `fail` verdicts.
A crash has no metrics. Counts are unsigned integers, `saturated` is a Boolean,
and `phase` is text. The [manual][manual] gives an example.

- **Types.** An `ExplorationMetrics` record travels in `ToolResult`, bumping
  `ToolWorkerProtocol` to version 6, and in `StageRecord`. The field names are
  the constants of one enum, which the codec, the protocol and
  `fuzztla print --envelope` share. A round-trip test pins the encoding.
- **Preservation.** `CorpusEnvelopeCodec` already preserves unknown stage
  fields, and the aggregator merges stage records unchanged. Metrics therefore
  reach `03aggregator-*` without aggregator changes.
- **No migration.** Existing entries have no metrics, and per repository policy
  no migration adds them.

## Alternatives considered

- **Parse TLC's text output.** Distinct states and depth appear in the final
  report, but only on some exit paths. The text has no projection, action or
  state-size information, and its format is not a contract.
- **`POSTCONDITION` with `TLCGet("stats")`.** Needs an operator in the checked
  module, which changes the input the checkers are compared on.
- **`-dump` and re-parsing the states.** Costs I/O proportional to the state
  space, plus a TLA<sup>+</sup> value parser in the parent.
- **`-coverage`.** Reports per-expression costs rather than per-action results,
  and adds overhead to every evaluation instead of every transition.
- **Decide in the checker stage.** Would couple thresholds to checker runs, so
  changing a threshold would mean re-checking the corpus.
- **Novelty maps.** Admitting only entries that add a bucket to an AFL-style
  feature map depends on these metrics, and belongs to the gate ADR.

## Consequences

- **Overhead is small.**
  - *Corpus entries.* On 200 entries (the 100 largest calibration state spaces
    plus 100 random), TLC's `process` took a median of 709 ms with the writer
    and 699 ms without. Each pair ran one after the other in the same
    environment, and the error codes were identical. The writer measured here
    was the first version described below, so the proposed one costs less.
  - *Stress module.* A hand-written module with 133,110 distinct states,
    1,717,119 transitions and states of up to 29 value nodes took 1,345–1,479 ms
    without the writer over five runs, and 1,491–1,649 ms with the proposed
    writer over three, about +15%. The first version, which fingerprinted every
    transition to detect stuttering and formatted each action's location per
    transition, took 3,007–3,223 ms.
  - *Where time goes.* TLC's per-input cost stays dominated by JVM start-up.
- **Shallow agreement becomes visible corpus-wide.** An estimated 99.6% of
  corpus22's agreeing entries are shallow. The patterns point at the generator:
  `Init` is unsatisfiable or violates `Inv`, and rich cohorts fail early. Stored
  metrics measure progress on that work without re-running a checker.
- **Protocol and envelope change.** Both change once. `fuzztla print --envelope`
  renders the metrics.
- **Open questions for the gate ADR:**
  - the thresholds;
  - whether fail/fail pairs with unequal codes count as agreement;
  - whether shallow pass/pass and counterexample/counterexample entries are
    dropped, sampled like `known_defect_samples`, or kept in a separate
    directory;
  - whether the metrics should feed generation, as a Hypothesis-style `target`
    next to the richness cohorts of [ADR 0002][adr-0002].

[workflows]: ../architecture/fuzzing-workflows.md#11-general-architecture
[adr-0002]: 0002-pbt-richness-score.md
[adr-0006]: 0006-known-defect-signatures.md
[manual]: ../manual/exploration-metrics.md
