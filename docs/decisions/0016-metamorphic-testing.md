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
*rewrite* maps a generated module M1 to a module M2 that is equivalent under
TLA<sup>+</sup> semantics, for example by replacing `x` with `x + y - y`. One checker
then checks a module that relates M1 and M2. Any violation of that relation is a defect of that
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

### Notation

An entry's orientation designates one of M1 and M2 as the *explored side* E and the
other as the *checked side* C. For a side X, the relation module contains the following
definitions, copied from X:

| Definition | Meaning |
| --- | --- |
| `InitX` | initial predicate |
| `ActionX` | next-state action without its stuttering disjunct |
| `InvX` | state invariant |
| `PropX` | temporal property |
| `FairnessX` | fairness conditions |
| `exprX` | the expression of an `expr` entry |

X ranges over the roles E and C in the decision, and over the modules 1 and 2 in the
probes, which also compare relations without an orientation. `vars` is the tuple of
state variables, which M1 and M2 share.

### Relation

This ADR checks an implication over one copy of the variables: `InitE ⇒ InitC` and
`ActionE ⇒ ActionC`. The checker explores E. It checks `InitC` as an invariant in the
states where `step = 0`, which are exactly the initial states, and `ActionC` as an
action invariant on every transition. Which side is explored is chosen per entry, so
a corpus covers both directions.

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

**P1: relations.** Each relation was checked twice. In the *identity* run, M2 is a
copy of M1. In the *perturbed* run, `Action2` is `Action1` with every integer literal
incremented, except the operands beside `step`. The perturbation applies to 301 of the
500 entries. Two relations were compared:

- **Union:** `Next == Action1 \/ Action2 \/ UNCHANGED vars`, with
  `PROPERTY [][Action1 <=> Action2]_vars`.
- **Implication,** as decided in section 3:
  - `Init == InitE` and `Next == ActionE \/ UNCHANGED vars`;
  - `Inv == (step = 0 => InitC) /\ (InvE <=> InvC)`;
  - `PROPERTY [][Step]_vars` with `Step == [ActionC]_vars`;
  - only M2's operators are renamed.

  *Forward* explores M1 and checks M2 (E = M1, C = M2). *Backward* explores the
  perturbed M2 and checks M1 (E = M2, C = M1).

| Relation | identity: pass | identity: fail | perturbed: violation | perturbed: pass | perturbed: fail |
| --- | ---: | ---: | ---: | ---: | ---: |
| Union | – | – | 110 | 119 | 72 |
| Implication, forward | 496 | 4 | 108 | 121 | 72 |
| Implication, backward | – | – | 108 | 122 | 71 |
| Implication, either direction | – | – | 111 | – | – |

- **The implication detects at least what the union detects.** Each direction alone
  detects 108 perturbations, and 105 are detected in both directions. Together, the
  two directions detect 111. That set contains every union detection. The one extra
  entry is `5bf681ad9426`, where the perturbed `Action2` indexes a tuple out of its
  domain. The union and the forward implication fail while evaluating it, but the
  backward implication, which generates successors from `Action2`, reports a
  violation.
- **The implication reports no false violation.** The identity implication passes
  496 entries and reports no violation on the other 4, which fail.
- **Failures are partial evaluations,** such as `CHOOSE` without a witness, a `CASE`
  with no true arm, or a function applied outside its domain. On its own, each base
  spec reports an invariant violation first and stops. A relation replaces the
  invariant with `InvE <=> InvC`, so it explores past that state and reaches the
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
entries whose `Action2` the perturbation changes. It read typed JSON, as it does in the
pipeline, ran with `--inv=Inv,Step --length=6 --no-deadlock`, and had a 300 s timeout.
The sample holds 30 entries whose perturbation a preliminary TLC check detected and
30 whose perturbation it missed. Apalache's log reports `Step` under "Checking 1
action invariants".

| Module | pass | violation | other |
| --- | ---: | ---: | --- |
| Identity | 58 | 0 | 2 timeouts |
| Perturbed, forward | 20 | 39 | 1 typecheck error |
| Perturbed, backward | 21 | 38 | 1 typecheck error |

- **Apalache and TLC agree** on every module that both decided, in both directions.
- **The typecheck error was not investigated.** Both errors are `245f6a134412`,
  whose identity module passes. Apalache reports "Error when computing the type of
  Lambda6".

**P3: recursion in the IR.** The facade IR carries all three recursive forms:
- a top-level `RECURSIVE` operator (`TlaOperDecl.isRecursive`);
- `LET RECURSIVE`;
- a recursive function definition (`TlaFunOper.recFunDef`/`recFunRef`).

`PrettyWriter` prints each form. TLC evaluates a recursive sum over a set, a
factorial as a recursive function, and a `LET RECURSIVE` counter correctly.
`TlaTypedScopeUncheckedBuilder` has no method for any of these forms: the probe set
the Scala field and built the operator applications directly.

**P4: temporal relation.** 133 of the 500 entries have a property. For each, `Prop2`
was a fresh copy of the property `Prop1`.
- Base spec with `Prop == Prop1`: 85 pass, 48 liveness violations.
- `Prop == Prop1 <=> Prop2`: 67 pass. The other 66 fail with "TLC cannot handle the
  temporal formula". Those entries include formulas as simple as
  `[]<>(\E q \in S : FALSE)`.
- `Prop == (Prop1 => Prop2) /\ (Prop2 => Prop1)`: 127 pass. The other 6 fail with "Temporal
  formulas containing actions must be of forms <>[]A or []<>A", because the
  implication negates a `[][A]_v` subformula.

TLC's rejection of `<=>` between formulas whose implications it accepts is a
candidate TLC finding. It is not triaged here.

**P5: placeholder rules.** On 200 of the entries, M2 received two rewrites:
`k → k + 0` on every integer literal, and `P → ~~P` on every conjunction and
disjunction. TLC generated successors from `Action2`, as it does when the implication
explores M2.
- **Applied everywhere: all 200 fail.** TLC reports "identifier var0_2 is either
  undefined or not an operator", because an equation under negation is not an
  assignment, so the primed variable stays undefined.
- **`~~` restricted to formulas without a prime: 198 pass.** The 2 failures are
  the entries that also fail without the rewrites.

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
is a decoder from bytes to a pair (M1, M2) and an orientation. The requirements of
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
  left unchanged, and M1 is explored.
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
M1 is rejected. The input stage already rejects a mutant clone through
`CandidateSource.Draft.isClone`; a metamorphic draft supplies a predicate that
compares the two sides.

**IR plumbing.**
- Rewriting uses the facade's `TlaExpressions.rewrite`, `forEach` and `deepCopy`.
- M2's generated operators are renamed; its state variables are not.
  Renaming, the binder layout, alpha-equivalence, substitution and beta-reduction
  live in `gen.ir`, which the library importer shares; they are not copied.
- The facade builder gains methods for recursive operators and recursive functions
  (probe P3), which the recursive rules of the catalog ADR need.

### 3. Relation module

`FuzzInputModule` assembles the relation. The entry points and the tool invocations
stay as they are ([fuzzing-workflows.md §1.1][workflows]), with one more entry point
for the action invariant.

**Orientation.** The orientation marker selects the explored side E and the checked
side C: (E, C) is (M1, M2) when the marker is even, and (M2, M1) when it is odd.
Exploring only M1 would check `M1 ⇒ M2` alone, and it would never run M2's actions in
TLC's successor-generating mode.

**Side definitions.** Each side's body is a definition of its own, named by its role
as in the notation table: `InitE`, `InitC`, `ActionE`, `ActionC`, `InvE`, `InvC`,
`PropE`, `PropC` and `FairnessE`. SANY scopes labels per definition, and a copied body
repeats its labels (probe P1). M2's generated operators are renamed injectively into a
namespace that the decoder never binds, and so are the side definitions.

**`module` under `mt`.** Over the variables `vars` of M1, which M2 shares:

```tla
Init         == InitE
Next         == ActionE \/ UNCHANGED vars
Inv          == (step = 0 => InitC) /\ (InvE <=> InvC)
Step         == [ActionC]_vars
StepProperty == [][Step]_vars
Spec         == Init /\ [][Next]_vars /\ FairnessE
```

- **Initial states.** `step = 0` holds exactly in the initial states, since every
  transition of `ActionE` increments `step`. `Inv` therefore checks `InitE ⇒ InitC`.
- **Transitions.** `Step` checks `ActionE ⇒ ActionC` on every transition that changes
  a variable. TLC generates successors from `ActionE` and evaluates `ActionC` as a
  predicate on each of them.
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

**`expr` under `mt`.** The single-state case: `Init == v = exprE`,
`Inv == v = exprC` and `Next == UNCHANGED v`. The orientation decides which side is evaluated as an
assignment.

**Temporal properties.** For a module with a property,
`Prop == (PropE => PropC) /\ (PropC => PropE)`. The conjunction of implications, not `<=>`, is
required (probe P4).
- `Spec` conjoins the explored side's fairness conditions, `FairnessE`.
- `Liveness == FairnessE => Prop` for Apalache, as before.

`PropE <=> PropC` holds on every behavior, so the specification decides only which
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
   - `fuzztla print` shows M1, M2, the orientation and the applied rules by replay;
   - a shrinker clears rewrite markers one at a time;
   - `export-db` stores the technique and the applied rules ([ADR 0009][adr-0009]
     schema bump).

The rule catalog follows in its own ADR.

## Alternatives considered

- **Union encoding.** Explore `Action1 \/ Action2` over one copy of the variables, and
  assert `[][Action1 <=> Action2]_vars`. It checks both directions in one entry and
  detected 110 of the 301 perturbations. Its behaviors, however, belong to neither module: a
  counterexample may mix steps of both sides, and the quality gate would rank a module
  that was never generated. The implication explores one generated module, and it
  shows the rest of the difference in entries with the other orientation.
- **History variables.** Keep the predecessor state in a copy `pvars` and check
  `step /= 0 => ActionC` as a state invariant, with `x` read as `px` and `x'` as `x`.
  This would be a state invariant on both checkers. However:
  - every reachable transition becomes a state;
  - the substitution must keep the primes that `ENABLED` binds, and it has no
    syntactic form for `\cdot`;
  - action operators need shifted copies.
- **Metamorphic input kinds.** Add the kinds `mt-expr` and `mt-module`, with
  `InputKind.base()` and `InputKind.oracle()`. That doubles every kind, lets one
  corpus mix oracles, and makes every consumer of `InputKind` aware of
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
- **The first TLC candidate finding already exists.** TLC rejects `Prop1 <=> Prop2`
  where it accepts `(Prop1 => Prop2) /\ (Prop2 => Prop1)` (probe P4). It needs triage and a findings
  document of its own.
- **Triage attributes a violation to the rules applied.** It replays the entry to
  list them and its orientation. Findings cite the rule module and the FuzzTLA
  commit, besides the TLC and Apalache commits.
- **Cost.** The relation adds no state. Per explored transition, it evaluates `ActionC`
  once more, and per initial state, `InitC`. The probe did not measure the time
  overhead.

[adr-0008]: 0008-exploration-metrics.md
[adr-0009]: 0009-corpus-database.md
[adr-0010]: 0010-mutation.md
[adr-0015]: 0015-diff-linked-libraries.md
[adr-0017]: 0017-rewrite-rule-library.md
[generators]: ../architecture/ir-generators.md
[workflows]: ../architecture/fuzzing-workflows.md
