# 0016: Metamorphic testing

**Authors:** Igor Konnov and Claude

**Status:** Proposed

**Date:** 2026-09-24

## Context

Conformance testing ([fuzzing-workflows.md §1.3][workflows]) needs two checkers that
evaluate the same input: a verdict difference is the oracle. It cannot test what only
one checker evaluates, such as TLC's recursion, and it misses a defect that both
checkers share.

Metamorphic testing (MT) replaces the second checker by a second specification. A
*rewrite* maps a module M to a module M2 that is equivalent under TLA<sup>+</sup>
semantics, for example by replacing `x` with `x + y - y`. One checker then checks a
module that relates M and M2. Any violation of that relation is a defect of that
checker, or of the rewrite. Single rewrites rarely surface defects, so rewrites
stack.

[fuzzing-workflows.md §1.4][workflows] reserves a metamorphic workflow but does not
design it. [ADR 0010][adr-0010] defers IR-level mutation because it needs a second
generator that maintains the generator's type-safety and closedness invariants.

This ADR proposes the framework: how MT inputs are encoded, decoded, assembled,
checked and judged, and how they enter a corpus. [ADR 0017][adr-0017] defines how a
rewrite rule is written: as an operator `F(x, y) == A = B` of a TLA<sup>+</sup> rule
module, with a contract that the loader checks, and seven seed rules. **The rule
catalog is out of scope.** A later ADR will propose which equivalences exist, grouped
into families, with their weights. The families it will cover are arithmetic, set,
Boolean, temporal, TLA<sup>+</sup>-specific, operator stacking, context-specific and
recursive.

### Relation

The package proposal relates the two modules by a product. Given a deadlock-free
specification `Init /\ [][Next]_vars` and its rewrite `Init2 /\ [][Next2]_vars2`, the
checker checks

```tla
(Init /\ Init2 /\ vars = vars2) /\ [][Next /\ Next2 /\ vars' = vars2']_<<vars, vars2>>
```

for deadlocks. The product does not fit the pipeline. Every generated `Next` ends with
a stuttering disjunct (`workflow/spec/FuzzInputModule.java`), every generated action
is guarded by `step < maximumSteps` ([ir-generators.md §9.3][generators]), and both
checkers run with deadlock detection off. The product therefore needs deadlock
detection per input, and a joint stutter step exactly where neither side has a
successor. The joint stutter needs `ENABLED`, which Apalache does not support.
Probe P1 also shows that the product detects few disagreements.

This ADR checks an implication instead: `Init ⇒ Init2` and `Next ⇒ Next2`, over one
copy of the variables. The checker explores one side. It checks the other side's
initial predicate as an invariant in the states where `step = 0`, which are exactly
the initial states, and the other side's next-state action as an action invariant
on every transition. Which side is explored is chosen per entry, so a corpus covers
both directions.

### Probes

**Setup.**
- Probed with FuzzTLA `f96d466`, TLC commit `4260e47` (tla2tools
  `1.8.0-20260923.155048-80`) and Apalache 0.62.2 (build `f0dec98`).
- TLC ran through the API that the worker uses (`new TLC()`, `handleParameters`,
  `process`), with one worker and a 120 s timeout.
- Sample: 500 entries drawn uniformly (seed 16) from the 3,874 entries of
  `corpus42/04quality-pass`. `corpus42` configures no custom operators. The entries
  were decoded under `f96d466` with `corpus42`'s configuration, so a few no longer
  decode to the module that the gate selected.
- The probe program assembled modules through the facade builder and rendered them
  with `SpecText`, as the pipeline does.

**P1: relations.** Each relation was checked twice. In the *identity* run, the
second side is a copy of the first. In the *perturbed* run, `A2` is `A1` with every
integer literal incremented, except the operands beside `step`. The perturbation
applies to 301 of the 500 entries. Three relations were compared:

- **Product:** the proposal's product over a renamed copy, where every state variable
  (including `step`) and every generated operator is renamed. The generated actions
  lose their stuttering disjuncts, and a joint stutter
  `~ENABLED A1 /\ ~ENABLED A2 /\ UNCHANGED <<vars, vars2>>` is added. Deadlock
  detection is on.
- **Union:** `Next == A1 \/ A2 \/ UNCHANGED vars` over one copy of the variables, with
  `PROPERTY [][A1 <=> A2]_vars`.
- **Implication,** as decided in section 3:
  - `Init == InitE` and `Next == AE \/ UNCHANGED vars`;
  - `Inv == (step = 0 => InitC) /\ (Inv1 <=> Inv2)`;
  - `PROPERTY [][Step]_vars` with `Step == [AC]_vars`;
  - only M2's operators are renamed.

  *Forward* explores M and checks M2 (E = 1, C = 2). *Backward* explores the
  perturbed M2 and checks M.

| Relation | identity: pass | identity: fail | perturbed: violation | perturbed: pass | perturbed: fail |
| --- | ---: | ---: | ---: | ---: | ---: |
| Product | 496 | 4 | 60 | 169 | 72 |
| Union | – | – | 110 | 119 | 72 |
| Implication, forward | 496 | 4 | 108 | 121 | 72 |
| Implication, backward | – | – | 108 | 122 | 71 |
| Implication, either direction | – | – | 111 | – | – |

- **The implication detects at least what the other two detect.** Each direction
  alone detects 108 perturbations, and 105 are detected in both directions. Together,
  the two directions detect 111. That set contains every union detection and every
  product detection. The one extra entry is `5bf681ad9426`, where the perturbed `A2`
  indexes a tuple out of its domain. The union and the forward implication fail while
  evaluating it, but the backward implication, which generates successors from `A2`,
  reports a violation.
- **The implication reports no false violation.** The identity implication passes
  exactly where the identity product passes. Its 4 failures are the product's 4.
- **The product detects a disagreement only if the successor sets are disjoint.** It
  detects one only in a state where the successor sets of `A1` and `A2` are disjoint
  and not both empty. It misses successor sets that overlap but differ. Every product
  detection is also a union detection.
- **The product needs `ENABLED`.** With the step-bound stutter
  `step = maximumSteps /\ UNCHANGED <<vars, vars2>>` in place of the `ENABLED` guard,
  the identity product reports 3 false deadlocks. In each, the base spec reaches a
  state at `step` 0 or 1 of 5 where no action is enabled.
- **Failures are partial evaluations,** such as `CHOOSE` without a witness, a `CASE`
  with no true arm, or a function applied outside its domain. On its own, each base
  spec reports an invariant violation first and stops. A relation replaces the
  invariant with `Inv1 <=> Inv2`, so it explores past that state and reaches the
  error. The relation therefore reaches states that conformance runs never examine:
  TLC stops at the first invariant violation, so a corpus entry with a counterexample
  verdict has been explored only up to that state.
- **Labels need separate definitions.** The first assembly inlined both invariants
  into one definition. SANY rejected 2 of the 5 smoke-test modules with
  `Duplicate label`, because a copied body repeats its labels. Each side's body must
  therefore be a definition of its own.
- **Some undetected perturbations may preserve the semantics.** The probe does not
  separate them.

**P2: action invariants.** TLC and Apalache check an action invariant through
different mechanisms:
- **TLC** has no action-invariant keyword, and it rejects a primed `INVARIANT` with
  "The invariant ... is not a state predicate". A `PROPERTY [][A]_v` is instead
  checked as an *implied action* on every transition, without a tableau. A violation
  reports "Action property ... is violated" and exits with status 13
  (`VIOLATION_LIVENESS`). `workflow/tlc/TlcOutcomeClassifier.java` already classifies
  this status as a counterexample.
- **Apalache** takes a list in `--inv` (`--inv=Inv,Step`), and it classifies an
  action-level operator as an action invariant (`bmcmt.ActionInvariant`).

**Apalache on the implication.** Apalache checked the implication for 60 of the
entries whose `A2` the perturbation changes. It read typed JSON, as it does in the
pipeline, ran with `--inv=Inv,Step --length=6 --no-deadlock`, and had a 300 s timeout.
The sample holds the first 30 entries whose perturbation the product detects and the
first 30 whose perturbation it misses. Apalache's log reports `Step` under "Checking 1
action invariants".

| Module | pass | violation | other |
| --- | ---: | ---: | --- |
| Identity | 58 | 0 | 2 timeouts |
| Perturbed, forward | 20 | 39 | 1 typecheck error |
| Perturbed, backward | 21 | 38 | 1 typecheck error |

- **Apalache and TLC agree** on every module that both decided, in both directions.
- **The typecheck error was not investigated.** Both errors are `245f6a134412`,
  whose identity module passes. Apalache reports "Error when computing the type of
  Lambda6". The perturbed product fails on the same entry, naming a lambda passed to
  `ApaFoldSeqLeft`.

**P3: recursion in the IR.** The facade IR carries all three recursive forms:
- a top-level `RECURSIVE` operator (`TlaOperDecl.isRecursive`);
- `LET RECURSIVE`;
- a recursive function definition (`TlaFunOper.recFunDef`/`recFunRef`).

`PrettyWriter` prints each form. TLC evaluates a recursive sum over a set, a
factorial as a recursive function, and a `LET RECURSIVE` counter correctly.
`TlaTypedScopeUncheckedBuilder` has no method for any of these forms: the probe set
the Scala field and built the operator applications directly.

**P4: temporal relation.** 133 of the 500 entries have a property. For each, `F2`
was a fresh copy of the property `F1`.
- Base spec with `Prop == F1`: 85 pass, 48 liveness violations.
- `Prop == F1 <=> F2`: 67 pass. The other 66 fail with "TLC cannot handle the
  temporal formula". Those entries include formulas as simple as
  `[]<>(\E q \in S : FALSE)`.
- `Prop == (F1 => F2) /\ (F2 => F1)`: 127 pass. The other 6 fail with "Temporal
  formulas containing actions must be of forms <>[]A or []<>A", because the
  implication negates a `[][A]_v` subformula.

TLC's rejection of `<=>` between formulas whose implications it accepts is a
candidate TLC finding. It is not triaged here.

**P5: placeholder rules.** On 200 of the entries, the second side of the identity
product also received two rewrites: `k → k + 0` on every integer literal, and
`P → ~~P` on every conjunction and disjunction. In the product, TLC generates
successors from both sides, as it does from M2 when the implication explores M2.
- **Applied everywhere: all 200 fail.** TLC reports "identifier var0_2 is either
  undefined or not an operator", because an equation under negation is not an
  assignment, so the primed variable stays undefined.
- **`~~` restricted to formulas without a prime: 198 pass.** The 2 failures are
  the entries that also fail as identity products.

A rewrite can therefore be valid in TLA<sup>+</sup> and still take away TLC's
assignments. The rule contract below forbids that.

## Decision

### 1. Metamorphic technique

MT is a fuzzing technique, not an input kind. `fuzztla run --how=mt` enables it, next
to `--how=pbt`.

**Technique.** `RunCommand.Technique` moves to a new enum `corpus.Technique`, because
the corpus records it and the aggregator reads it. Each constant carries the facts
that differ per technique:
- its encoded name, used by `--how` and in the corpus;
- `oracle()`: `CONFORMANCE` for `pbt` and `METAMORPHIC` for `mt` (section 4).

**Corpus marker.** The first run records its technique in a new corpus file,
`.technique`. Later runs require `--how` to match, as `.operator-library` requires
the same library ([fuzzing-workflows.md §1.2][workflows]). `print`, `export-db` and
startup recovery read the file instead of taking the technique as an option. A corpus
without the file is a `pbt` corpus, so existing corpora need no migration.

**Payload.** An entry keeps its kind, `expr` or `module`. Under `mt`, its payload is:

```
[base length: 2 bytes, big-endian] [base payload] [rewrite payload]
```

- **Base payload.** The decoder of the entry's kind decodes it through `Draw.slice`.
  A length beyond the input is clamped.
- **Rewrite payload.** The rewriter (section 2) decodes it. Its first Boolean marker
  is the orientation (section 3).
- **Deviation from [ir-generators.md §4][generators]**, which forbids length
  prefixes for structural lists. The header serves two purposes:
  - it keeps the base payload byte-identical to a stored conformance entry, so a
    lifted parent (section 6) decodes to the same module;
  - it keeps byte edits of the rewrite payload from ever moving the base.

  Proportional sectioning, as in `ModuleSection`, would re-slice the base whenever
  the rewrite payload grows. A mutation of the header bytes does move the base; this
  is accepted.

**Decoders.** `workflow.spec.SpecDecoders.prepare` takes the corpus technique. Under
`mt`, the decoder of each kind slices the header, decodes the base, and composes the
result with the rewriter. Its constructor still rejects a kind without a decoder. The
byte encodings of `expr` and `module` are unchanged, so no stored entry is
reinterpreted.

Entry identity is the digest of `input` ([fuzzing-workflows.md §2.3][workflows]).
A corpus has one technique, so an MT entry never shares a corpus with a plain entry
that it could collide with.

### 2. Rewriter

The rewriter lives in the new package `io.github.tlaplus.hardening.gen.rewrite`. It
is a decoder from bytes to a pair (M, M2) and an orientation. The requirements of
[ir-generators.md §1][generators] therefore apply:
- it is deterministic and has no hidden randomness;
- exhausted input decodes to no rewrite;
- `InputRejectedException` is reserved for expected dead ends.

The rewriter needs package access to `gen.engine`, whose `GenerationContext` and
`NameScope` supply auxiliary operands.

**Walk.**
- The first Boolean marker selects the orientation (section 3).
- The rewriter then visits the typed IR of each rewritable body in pre-order.
- At each node, one Boolean marker decides whether to rewrite it. An odd marker is
  followed by one fixed-width two-byte index over the weighted slots of the
  applicable rules. This is the selection scheme of `IrExprGenFactory`
  ([ir-generators.md §5][generators]).
- The walk continues into the rewritten node, so rewrites stack. `max_rewrites`
  bounds the number of rewrites per body, and `max_rewrite_depth` bounds the
  rewrites stacked on one node.
- Once the input is exhausted, every marker reads even, so the rest of the tree is
  left unchanged, and M is explored.
- Rewritable bodies are the operator definitions, `Init`, the next-state action,
  the invariant, and the property formula, including its fairness conditions.
- The walk tracks lexical scope. A fresh operand, such as the `y` of `x + (y - y)`,
  is drawn by the expression engine at its type and in the node's scope.

**Rules.** [ADR 0017][adr-0017] defines the rules. In summary:
- **A rule is an operator** `F(x, y) == A = B` of a TLA<sup>+</sup> rule module, typed
  by Snowcat. Every top-level definition of the module is a rule.
- **Applicability is byte-free.** A rule applies at a node when A matches the node
  under a type-correct substitution and the contract holds for that match.
- **Declaration order is part of the byte encoding.** The corpus file
  `.rewrite-library` pins the rule module's sources and weights.
- **The contract** requires every rule to be valid in TLA<sup>+</sup>,
  level-preserving, assignment-preserving and closed. The loader and the matcher check
  the last three. Assignment preservation matters because the explored side may be
  M2, so TLC generates successors from rewritten actions (probe P5).

**Identity rejection.** A decoded pair whose M2 renders to the same TLA<sup>+</sup> as
M is rejected. The input stage already rejects a mutant clone through
`CandidateSource.Draft.isClone`; a metamorphic draft supplies a predicate that
compares the two sides.

**IR plumbing.**
- Rewriting uses the facade's `TlaExpressions.rewrite`, `forEach` and `deepCopy`.
- M2's generated operators are renamed; its state variables are not.
  Renaming generalizes `gen/library/LibraryExpressions.rename` into one shared
  helper; it is not copied.
- The facade builder gains methods for recursive operators and recursive functions
  (probe P3), which the recursive rules of the catalog ADR need.

### 3. Relation module

`FuzzInputModule` assembles the relation. The entry points and the tool invocations
stay as they are ([fuzzing-workflows.md §1.1][workflows]), with one more entry point
for the action invariant.

**Side definitions.** Each side's body is a definition of its own: `Init1`, `Init2`,
`Inv1`, `Inv2`, `A1`, `A2`, `F1` and `F2`. SANY scopes labels per definition, and a
copied body repeats its labels (probe P1). M2's generated operators are renamed
injectively into a namespace that the decoder never binds, and so are the side
definitions.

**Orientation.** The orientation marker selects the explored side E and the checked
side C: (E, C) is (M, M2) when the marker is even, and (M2, M) when it is odd.
Exploring only M would check `M ⇒ M2` alone, and it would never run M2's actions in
TLC's successor-generating mode.

**`module` under `mt`.** Over the variables `vars` of M, which M2 shares:

```tla
Init         == InitE
Next         == AE \/ UNCHANGED vars
Inv          == (step = 0 => InitC) /\ (Inv1 <=> Inv2)
Step         == [AC]_vars
StepProperty == [][Step]_vars
Spec         == Init /\ [][Next]_vars /\ FairnessE
```

- **Initial states.** `step = 0` holds exactly in the initial states, since every
  transition of `AE` increments `step`. `Inv` therefore checks `InitE ⇒ InitC`.
- **Transitions.** `AE` and `AC` are the generated next-state actions without their
  stuttering disjuncts. `Step` checks `AE ⇒ AC` on every transition that changes a
  variable. TLC generates successors from `AE` and evaluates `AC` as a predicate on
  each of them.
- **Invariant.** The base invariant's truth is not checked; only its equivalence
  with its rewrite is.
- **Action invariant.** TLC gets `PROPERTY StepProperty`, and Apalache gets
  `--inv=Inv,Step` (probe P2). `CheckRequest` gains an `actionInvariant` flag, which
  is true for a `module` entry under `mt`. `TlcConfiguration` and `ApalacheArguments`
  read it.
- **No deadlock detection.** The generated stuttering disjunct stays, and both
  checkers keep `-deadlock` and `--no-deadlock`. TLC and Apalache receive the same
  relation.
- **Exploration metrics.** The relation reaches exactly the states of the explored
  module. The projections of [ADR 0008][adr-0008] and the quality gate therefore
  apply unchanged.

**`expr` under `mt`.** The single-state case: `Init == v = eE`, `Inv == v = eC` and
`Next == UNCHANGED v`. The orientation decides which side is evaluated as an
assignment.

**Temporal properties.** For a module with a property,
`Prop == (F1 => F2) /\ (F2 => F1)`. The conjunction of implications, not `<=>`, is
required (probe P4).
- `Spec` conjoins the explored side's fairness conditions, `FairnessE`.
- `Liveness == FairnessE => Prop` for Apalache, as before.

`F1 <=> F2` holds on every behavior, so the specification decides only which
behaviors are examined, not whether a violation is a defect. The checked side's
fairness conditions are not compared; a fairness rewrite takes effect only in entries
that explore its side.

### 4. Oracle

`corpus.AggregationInput` passes an entry when every checker verdict is equal. The
aggregator instead asks the corpus technique's `oracle()`:

- `CONFORMANCE`: unchanged.
- `METAMORPHIC`: pass when the configured checkers agree and none reports a
  counterexample.

A counterexample from every checker still fails the entry. It means an unsound rule
or a defect that the checkers share, and triage decides which.

### 5. Configurable checker set

`[workflow] checkers` lists the model-checker stages that run. The default is
`["tlc", "apalache"]`. An MT corpus may list `["tlc"]` alone, which recursive
rewrites need, because Apalache rejects every recursive definition
([ADR 0015][adr-0015]).

Today the set is `CorpusStage.checkerBranches()`, which is every stage with the
`CHECKING` role. It becomes run configuration. The following change:
- the parser's fan-out copies a pass only to the configured branches;
- `AggregationInput` and `AggregationTransition` require exactly the configured
  verdicts. With one branch, only the oracle decides;
- `workflow/CheckerBackends` requires a factory only for configured branches;
- the occupancy gates and startup recovery do the same.

The first run records the set in a corpus file `.checkers`. Later runs require an
exact match, as `.operator-library` does: recovery must not wait for verdicts of a
branch that never runs.

### 6. Candidate sources

PBT and byte mutation apply under `--how=mt` unchanged:
- PBT draws random base and rewrite payloads;
- the mutator edits the corpus's `04quality-pass` entries, which are MT entries, and
  can flip the orientation.

Random modules rarely exercise a transition relation ([ADR 0010][adr-0010]), so the
main source is a new one. `workflow.input.MetamorphicCandidates` *lifts* elite
conformance entries:

- **Parents.** It reads the `04quality-pass` entries of the configured kind from the
  corpus named by `[metamorphic] base_corpus`, which must be a `pbt` corpus. It reads
  them once per generation and never writes to that corpus.
- **Candidates.** A candidate is the header, the parent's payload, and a random
  rewrite payload. Admission, deduplication, quarantine and storage are the input
  stage's, as for every source. Known-defect signatures match the evaluated IR of the
  relation module, which contains both sides.
- **Provenance.** `gen.parent` is the parent's digest and `gen.operators` is
  `["lift"]`. `lift` is a new `MutationOperator` that only this source records; its
  byte-edit weight is zero. The shape of `Mutation` is unchanged.
- **Ordinals.** `GenerationLoop` splits a generation's target ordinals into three
  disjoint ranges: mutants, lifted candidates, then PBT. The ranges are disjoint for
  the reason [ADR 0010][adr-0010] gives for two.

### 7. Configuration

A top-level `[metamorphic]` table, read only under `--how=mt`:

| Key | Default | Meaning |
| --- | ---: | --- |
| `base_corpus` | none | `pbt` corpus whose `04quality-pass` supplies lifted parents; none disables lifting. |
| `lift_ratio` | 0.5 | Share of a generation admitted from lifted parents. |
| `max_rewrites` | 16 | Rewrites per rewritable body. |
| `max_rewrite_depth` | 4 | Rewrites stacked on one node. |
| `rules` | none | Rule module and its classpath ([ADR 0017][adr-0017]). |
| `weights` | 1 per rule | Rule weights, keyed by rule name; 0 disables a rule, and their sum must be positive. |

`[workflow] checkers` is described in section 5. `[generator] kind` selects `expr` or
`module` as before.

### 8. Implementation phases

1. `expr` under `--how=mt`:
   - `corpus.Technique` and `.technique`;
   - the rule loader and the rewriter with the seed rules of [ADR 0017][adr-0017],
     and the orientation marker;
   - the metamorphic oracle.

   It runs with both checkers.
2. The configurable checker set and the action-invariant entry point.
3. `module` under `--how=mt`: the relation, the lift source and the `[metamorphic]`
   table.
4. The temporal relation.
5. Triage:
   - `fuzztla print` shows M, M2, the orientation and the applied rules by replay;
   - a shrinker clears rewrite markers one at a time;
   - `export-db` stores the technique and the applied rules ([ADR 0009][adr-0009]
     schema bump).

The rule catalog follows in its own ADR.

## Alternatives considered

- **The product relation.** This is the proposal's relation, and an earlier draft of
  this ADR adopted it (probe P1). It has three costs:
  - it needs deadlock detection per input, which means dropping TLC's `-deadlock`
    flag, because the flag overrides `CHECK_DEADLOCK TRUE`;
  - it needs a joint stutter that differs per checker: `ENABLED` for TLC, and the step
    bound for Apalache, which yields false deadlocks where the base spec deadlocks
    before the bound;
  - it detects only disjoint successor sets: 60 of 301 perturbations.

  Its one advantage is that both sides generate successors in every entry. The
  orientation provides that across entries.
- **Union encoding.** Explore `A1 \/ A2` over one copy of the variables, and assert
  `[][A1 <=> A2]_vars`. It checks both directions in one entry and detected 110 of the
  301 perturbations. Its behaviors, however, belong to neither module: a
  counterexample may mix steps of both sides, and the quality gate would rank a module
  that was never generated. The implication explores one generated module, and it
  shows the rest of the difference in entries with the other orientation.
- **History variables.** Keep the predecessor state in a copy `pvars` and check
  `step /= 0 => A2` as a state invariant, with `x` read as `px` and `x'` as `x`. This
  would be a state invariant on both checkers. However:
  - every reachable transition becomes a state;
  - the substitution must keep the primes that `ENABLED` binds, and it has no
    syntactic form for `\cdot`;
  - action operators need shifted copies.
- **Metamorphic input kinds.** An earlier draft added the kinds `mt-expr` and
  `mt-module`, with `InputKind.base()` and `InputKind.oracle()`. That doubles every
  kind, lets one corpus mix oracles, and makes every consumer of `InputKind` aware of
  MT. A corpus has one technique, so the technique belongs to the corpus.
- **The rewrite as envelope metadata.** The digest of the input identifies an entry,
  so two rewrites of one parent would collide. A replay would also depend on metadata
  outside `input`.
- **Rewriting at check time from configuration.** Every entry would be rewritten by
  one run-wide choice. The rewrite could then neither be mutated nor replayed per
  entry.
- **A separate MT framework.** It would duplicate decoding, admission, stage
  scheduling, recovery and storage. The only MT-specific parts are the rewriter, the
  relation module, the oracle and the lift source.
- **Proportional sectioning of base and rewrite payloads.** Rejected above because it
  re-slices the base.

## Consequences

- **Existing corpora are unaffected.** The encodings of `expr` and `module` are
  unchanged, a corpus without `.technique` is a `pbt` corpus, and `pbt` runs keep
  their configuration files and checker flags.
- **A corpus runs one technique.** Conformance and MT entries live in separate
  corpora; lifting reads across them.
- **TLC-only corpora test no conformance.** A single-branch corpus has no verdict to
  compare, and its aggregator judges only by the oracle.
- **Blind spots.** An entry checks one direction, `E ⇒ C`, and only on the states
  that E reaches. A rewrite that only adds initial states or successors is detected
  only by entries that explore M2. Within an entry, the checked side is evaluated
  only as a predicate. In probe P1, one direction detected 108 of 301 perturbations,
  and both directions together detected 111.
- **The first TLC candidate finding already exists.** TLC rejects `F1 <=> F2` where
  it accepts `(F1 => F2) /\ (F2 => F1)` (probe P4). It needs triage and a findings
  document of its own.
- **Triage attributes a violation to the rules applied.** It replays the entry to
  list them and its orientation. Findings cite the rule module and the FuzzTLA
  commit, besides the TLC and Apalache commits.
- **Cost.** The relation adds no state. Per explored transition, it evaluates `AC`
  once more, and per initial state, `InitC`. The probe did not measure the time
  overhead.

[adr-0008]: 0008-exploration-metrics.md
[adr-0009]: 0009-corpus-database.md
[adr-0010]: 0010-mutation.md
[adr-0015]: 0015-diff-linked-libraries.md
[adr-0017]: 0017-rewrite-rule-library.md
[generators]: ../architecture/ir-generators.md
[workflows]: ../architecture/fuzzing-workflows.md
