# 0027: Metamorphic testing of temporal properties

**Authors:** Igor Konnov and Claude

**Status:** Accepted; phase 1 of section 7 is implemented

**Date:** 2026-11-02

**Implementation.** Phase 1 is implemented on the branch `igor/mt-temporal-rules`. It
settled these details:
- `gen.rewrite.RuleFamily` names the two kinds of rule module, and `RuleLevels` records,
  per rule, its formula parameters and whether it raises the level.
- **A rule raises the level** when its pattern applies no temporal operator and its
  replacement applies one, as `EventuallyWeaken` does. `LeadsToDef` does not.
- **Assignment preservation counts occurrences per action.** The action position of
  `[A]_v`, `<<A>>_v`, `WF_v(A)`, `SF_v(A)` or `ENABLED A` starts an action of its own. In
  each action, a parameter that a match binds to a primed subterm must occur at most
  once, in an assigning position. Counted over the whole rule instead, `WFDef`, which
  copies `A` into two actions, would never apply. `Rewrites.tla` has no action position,
  so its rules are analyzed as before.
- **When the matcher decides whether a binding assigns, it skips the actions of
  `[][A]_v`, `<><<A>>_v`, `WF` and `SF`,** which assign only within themselves. It still
  counts the primes under `ENABLED`, which generated guards contain. Existing corpora
  therefore decode unchanged.
- `run`, `print`, `export-db` and `shrink` load the temporal module when they prepare
  the decoders, and they refuse the settings of section 5. The rewriter uses the rules
  from phase 2 on.

## Context

[ADR 0016][adr-0016] proposes metamorphic testing (MT) and leaves its phase 4, the
temporal relation, unimplemented. [ADR 0017][adr-0017] defines the rule format and
leaves temporal rules out. The implementation follows both decisions:
- the loader rejects a rule whose pattern or replacement applies a temporal operator
  (`gen.rewrite.RuleContract`);
- `Rewriter.rewriteSpec` copies the base's property and fairness unchanged;
- the relation module defines `Fairness` and `Prop` as `TRUE`, and TLC is not asked
  about `Prop`.

As a result, no metamorphic entry exercises TLC's tableau construction, its liveness
checking, or its handling of `[][A]_v`, `<><<A>>_v`, `~>`, `WF` and `SF`.

**Scope.** This ADR proposes:
- temporal rewrite rules in a second, opt-in rule module;
- which bodies those rules rewrite;
- how TLC checks a rewritten property and fairness, and how the outcomes are judged.

It targets TLC. Apalache supports no fairness and is not tested by this relation.

**Why a second rule module.** A temporal relation costs TLC more time per entry
(section 4). A rule module that only some corpora configure keeps that cost, and the
change to the byte encoding, out of every other corpus. It also keeps temporal rules
out of corpora that Apalache checks.

**Why not ADR 0016's relation.** ADR 0016 §3 proposes
`Prop == (PropE => PropC) /\ (PropC => PropE)` under `FairnessE`. This has two
problems:
- TLC rejects the implication whenever it negates a `[][A]_v` subformula (ADR 0016
  probe P4, and P1 below).
- The relation compares no fairness: `FairnessC` occurs nowhere in the module, so a
  rewrite of the checked side's fairness is never checked.

This ADR checks each side's property under that side's specification instead, and
compares the verdicts.

### Notation

As in [ADR 0016][adr-0016]: an entry's orientation designates the explored side E and
the checked side C. For a side X, `InitX`, `ActionX`, `InvX`, `PropX` and `FairnessX`
are X's initial predicate, action without stuttering, invariant, property and
fairness conditions. This ADR adds:

```tla
NextX == ActionX \/ UNCHANGED vars
SpecX == InitX /\ [][NextX]_vars /\ FairnessX
```

The *relation run* is the TLC run of ADR 0016 §3: it explores E, checks `Inv` and the
action invariant `StepProperty`, and asks nothing about `Prop`.

### Probes

**Setup.**
- FuzzTLA `5b16596d`, TLC `56d37a9` (tla2tools `2026.10.28.014557`), Apalache 0.62.3
  (`3eb15b2`), TLAPM `b064bce`.
- Corpus: `fuzztla init --how=pbt` with the defaults (`kind = "module"`, `action` and
  `temporal` enabled), `checkers = ["tlc"]`, run for 25 minutes. Its gate kept 128 of
  2,982 entries in `04quality-pass`. Only 26 of these have a property, so the sample
  adds 474 entries drawn from `04quality-fail` (seed 27), for 500 entries with a
  property.
- Each relation module was assembled by `FuzzInputModule` and rendered by `SpecText`, as
  in the pipeline. The probe added `FairnessC`, `PropC`, `NextC`, `SpecC` and ADR 0016's
  `PropRel == (Prop => PropC) /\ (PropC => Prop)`, and filled `Fairness` and `Prop`
  from E.
- M2 is a copy of M1 with its operators renamed (the *identity* pair). Perturbed pairs
  change `PropC` or `FairnessC` textually, at the first applicable occurrence:
  - `[]F` to `<>F`, and `<>F` to `[]F`;
  - `WF` to `SF`, or the reverse;
  - `~>` to `=>`;
  - `FairnessC` replaced by `TRUE`;
  - `PropC` negated, as a positive control.
- TLC ran through the API that the worker uses (`new TLC()`, `handleParameters`,
  `process`), with one worker, `-deadlock`, `-fp 0`, `-seed 0`, a fresh JVM per run,
  and a 120 s timeout. A message recorder logged every message code with its
  parameters.

**P1: relations.** Four TLC configurations of the same module:

| Run | `SPECIFICATION` | Checked |
| --- | --- | --- |
| E | `Spec` (SpecE) | `Inv`, `StepProperty`, `Prop`: the relation run with `PropE` added |
| P_E | `Spec` (SpecE) | `Prop` (PropE) |
| P_C | `SpecC` | `PropC` |
| I | `Spec` (SpecE) | `Inv`, `StepProperty`, `PropRel`: ADR 0016's implication |

*Outcomes of the property run P_E:*
- **190 of the 500 entries decide P_E**: 110 pass and 80 violate `PropE`.
- **The rest fail**:
  - 199 evaluation errors;
  - 69 "The property of Prop is equal to FALSE";
  - 29 fingerprint errors;
  - 13 other, including one rejection of the base's own property.

  These are TLC's known restrictions and partial terms of the base (ADR 0016 probe P1).

*The two property runs, identity and perturbations*, on the entries where P_E decided:

| Pair | decided | verdicts equal | verdicts differ | P_C fails |
| --- | ---: | ---: | ---: | ---: |
| identity | 190 | 190 | 0 | 0 |
| `[]` ↔ `<>`, `WF` ↔ `SF`, `~>` to `=>`, `FairnessC` dropped | 63 | 61 | 0 | 2 |
| `PropC` negated | 190 | 28 | 125 | 37 |

*ADR 0016's implication*, run I, on the same pairs:

| | Property runs P_E, P_C | Implication, run I |
| --- | ---: | ---: |
| identity, of 500: TLC rejects the formula (2213, 2214) | 1 | 11 |
| negated `PropC`, of the 190: violation reported | 125 | 131 |
| negated `PropC`, of the 190: "equal to FALSE", which the oracle reads as `fail` | 27 | 27 |
| median wall time per run, s | 0.98 and 1.03 | 1.04 |

Run E took a median of 1.05 s. Each run starts a fresh JVM, which dominates these
times. Run E is used here as the stand-in for the relation run. On the 190 decided
entries, run E and both property runs together took 2.9 times as long as run E alone.

- **No false alarms.** Every identity pair reaches equal verdicts.
- **The textual perturbations change no verdict, under either relation.** Most
  properties are state predicates (P4). In a step-bounded module whose last state
  stutters, `WF` versus `SF` and the presence of fairness rarely decide a property.
  These perturbations measure the generated properties, not the relations.
- **The negation control detects in both relations.** Of the 28 negated pairs with
  equal verdicts:
  - 8 modules have no initial state, so every property holds vacuously, under the
    implication too;
  - in the other 20, both sides are violated: in 16 by different initial states, and in
    4 by a liveness violation.
- **The implication is rejected far more often:** 11 identity pairs against 1, and that
  one is the base property itself.

**P2: seed rules.** Section 6 lists the 22 seed rules.
- **SANY** parses the rule module.
- **Snowcat** (`typecheck --infer-poly=true`) types every rule. A formula parameter is
  typed `Bool`. The subscript `v` of `WF_v(A)`, `[A]_v` and `<<A>>_v` gets a type
  variable, so it matches a tuple of any shape.
- **TLAPM** proves every rule by `PTL`: 44 obligations. This includes the definitions of
  `WF` and `SF` in terms of `ENABLED`, under the hypotheses `ACTION A, STATE v`.
- **TLC** gives the same verdict for `WF_vars(A)` in `Spec` as for its two expansions,
  on a three-state counter with the property `<>(x = 2)`.
- **TLC takes another path for `[]F /\ <>F`.** On the same counter, TLC checks
  `PROPERTY [](x >= 0)` as an invariant and starts no liveness check. It checks
  `[](x >= 0) /\ <>(x >= 0)` with its liveness checker, and reaches the same verdict.
  It splits `[](x >= 0) /\ [](x >= 0)` into two invariants, and checks
  `[](x >= 0) \/ [](x >= 0)` with its liveness checker. A violated `[](x < 2)` is
  reported as an invariant violation, and its disjunction with itself as a temporal
  property violation. TLC rejects `[][x' >= x]_x \/ [][x' >= x]_x` with "Temporal formulas
  containing actions must be of forms <>[]A or []<>A". The known-defect signatures
  quarantine this shape ([temporal properties][temporal-manual] §5).
- **`<>F`, `<>F /\ <>F` and `<>F \/ <>F` agree** on the counter, all three through the
  liveness checker: `<>(x = 2)` holds and `<>(x = 3)` is violated.
- **SANY rejects a formula parameter bound to an action.** In `[][A]_x`, the operand
  of `[]` is the action `[A]_x`. `AlwaysUnfold` would turn it into `[A]_x /\ [][A]_x`,
  which SANY rejects: "\land has both temporal formula and action as arguments".
  `AlwaysAsNotEventually` would produce `~<>~[A]_x`, which SANY rejects: "<> followed by
  action not of form <<A>>_v".

**P3: what a TLC violation names.** The message codes identify the obligation kind:

| Code | Message | Raised by |
| --- | --- | --- |
| 2107, 2110 | invariant violated (initially, by a behavior) | an `INVARIANT`, or a `[]P` conjunct of a `PROPERTY` |
| 2108 | property violated by an initial state | the state-level conjuncts of a `PROPERTY` |
| 2112 | action property violated | an implied action, such as `[][A]_v` |
| 2116 | temporal property violated | a liveness conjunct |

- **The first parameter names the violated conjunct, not the `PROPERTY`.** For
  `PROPERTY PropConj` with `PropConj == (x = 0) /\ [][x' = x]_x`, TLC reports "Action
  property line 12, col 24 to line 12, col 35 of module FuzzInput is violated". In P1,
  run E named 3 of its 2108 violations by a conjunct's text or location.
- **A property can be reported as an invariant.** TLC checks a `[]P` conjunct of a
  `PROPERTY` as an invariant: "Invariant Prop is violated by the initial state" (code
  2107). Codes 2107 and 2110 therefore do not tell `Inv` from `Prop` either.
- **The returned code can disagree with the message.** In 16 of the 76 P1 runs that
  printed "Temporal property … was violated" (2116), `process()` returned 2110, which
  maps to exit status 12 (safety) rather than 13 (liveness). Both classify as a
  counterexample, so verdicts are unaffected. This is not triaged here.
- **Mixing obligations hides some violations.** With both `StepProperty` and a property
  conjunct violated on one transition, TLC reports whichever the configuration lists
  first. A 2108 also stops TLC before any transition, so run E's safety relation then
  covers only the initial states.

**P4: what the generated properties contain.** In the 500 sampled modules:
- 392 properties apply no temporal or fairness operator. They are state predicates,
  which TLC checks in the initial states only.
- Among the properties: `ENABLED` 52, `WF` 20, `~>` 14, `[][A]_v` 10, `<>F` 9, `SF` 8,
  `[]F` 7.
- 58 modules have fairness conditions: 28 have a `WF` condition and 32 an `SF`
  condition.

## Decision

### 1. Temporal rule module

A metamorphic corpus may name a second rule module, in `[metamorphic]`:

```toml
temporal_rules = { module = "TemporalRewrites", classpath = ["tla/rewrites"] }
temporal_weights = { WFDef = 4 }
```

The keys mean what `rules` and `weights` mean ([ADR 0017][adr-0017] §1). Without
`temporal_rules`, nothing in this ADR applies, and an `mt` corpus decodes and checks
exactly as before.

ADR 0017's format applies with these changes:
- **Shape.** A rule is `F(p1, ..., pn) == A <=> B`, since SANY rejects `=` between
  temporal formulas.
- **Names.** Rule names are unique across the two modules, so `appliedRules` keeps bare
  names.
- **Parameter levels.** The loader classifies each Boolean matched parameter by its
  position in A:
  - an *action parameter* occurs as the action of `[A]_v`, `<<A>>_v`, `WF_v(A)`,
    `SF_v(A)` or `ENABLED A`;
  - every other Boolean parameter is a *formula parameter*.

  The matcher binds a formula parameter only to a state predicate or a temporal
  formula. Such a subterm applies a prime, `UNCHANGED`, `[A]_v` or `<<A>>_v` only inside
  an action position of its own. Binding an action, such as the `[A]_v` operand of
  `[][A]_v`, yields formulas that SANY rejects (P2).
- **Base rules on temporal formulas.** Base rules apply in the property too (section 2),
  and a Boolean parameter of a base rule may bind to a temporal formula. A
  propositional law holds for temporal formulas as well. However, SANY rejects a
  temporal operand of `=`, of a tuple, of a set and of `CHOOSE`
  ([ir-generators.md §7.1][generators]). The matcher therefore binds a Boolean
  parameter to a temporal formula only if B keeps every occurrence of it in formula
  positions. This rules out `EquivAsEq`, `(P <=> Q) = (P = Q)`.
- **Level preservation** ([ADR 0017][adr-0017] §5.2) changes in two ways:
  - `WF` and `SF` in A count as action-level, so B may expand them with `ENABLED` and
    `<<A>>_v`.
  - A rule whose B applies a temporal operator while A applies none applies only at a
    *temporal position*. That is a node of the property or of a fairness condition that the walk
    reaches through formula operators only: the Boolean connectives, `IF` and `CASE`
    branches, `LET` bodies, quantifier bodies and temporal operators. This is where the
    generator draws at `LevelContext.TEMPORAL`.
- **Assignment preservation** ([ADR 0017][adr-0017] §5.3) stays in force for primed
  subterms. TLC evaluates `ENABLED` by generating successors, so an action under
  `WF`, `SF` or `ENABLED` keeps its assignment positions.
- **Proofs.** `TemporalRewritesProofs.tla` states one theorem per rule, with the
  hypotheses `TEMPORAL F` for a formula parameter, `ACTION A` for an action parameter
  and `STATE v` for a subscript, and proves it by `PTL`. `make proofs` runs it, and the
  unit test that requires a theorem per rule covers both modules.

The shipped module is `libraries/rewrites/TemporalRewrites.tla`. It sits next to
`Rewrites.tla`, so `init` copies it into `tla/rewrites` with the base rules.

### 2. Rewritten bodies

With `temporal_rules`, `Rewriter.rewriteSpec` walks two more kinds of body after the
invariant: each fairness condition in order, then the property formula. Each body:
- has its own budget of rewrites ([ADR 0016][adr-0016] §2);
- sees the invariant's scope;
- draws fresh operands at the `STATE` level.

In these bodies, the rules are the base rules followed by the temporal rules, in
declaration order, and one two-byte index selects among their weighted slots. Temporal
rules never apply in `Init`, the next-state action, the invariant or the operator
definitions. A corpus without `temporal_rules` stops reading after the invariant, as
it does today.

### 3. Relation module

The relation module of [ADR 0016][adr-0016] §3 keeps its definitions and adds the
checked side's specification. Only M2's operators are renamed:

```tla
Fairness == FairnessE
Spec     == Init /\ [][Next]_vars /\ Fairness   \* SpecE, as before
Prop     == PropE
FairnessC == ...                                 \* C's fairness conditions, conjoined
PropC     == ...                                 \* C's property
NextC     == ActionC \/ UNCHANGED vars
SpecC     == InitC /\ [][NextC]_vars /\ FairnessC
```

Each side's body is a definition of its own, as ADR 0016 probe P1 requires.

### 4. Three runs and one verdict

The TLC stage checks an entry whose base has a property in up to three runs of the
same module:

| Run | Configuration | Asks |
| --- | --- | --- |
| relation | `SPECIFICATION Spec`, `INVARIANT Inv`, `PROPERTY StepProperty` | `InitE ⇒ InitC`, `ActionE ⇒ ActionC`, `InvE ⇔ InvC` (ADR 0016) |
| P_E | `SPECIFICATION Spec`, `PROPERTY Prop` | `SpecE ⊨ PropE` |
| P_C | `SPECIFICATION SpecC`, `PROPERTY PropC` | `SpecC ⊨ PropC` |

If the rules are sound, SpecE ≡ SpecC and PropE ≡ PropC, so the two property runs
reach the same verdict. A property run checks only its property: any violation it
reports is that property's, whatever message code or conjunct name TLC prints (P3).
The relation run checks no property, so a property violation neither stops it nor
masks one of its violations (P3).

The stage folds the three outcomes into one, in this order:

| Relation run | P_E and P_C | Stage outcome |
| --- | --- | --- |
| counterexample | not run | counterexample |
| any other | both decided, verdicts differ | counterexample: "property verdicts differ" |
| pass | both decided, verdicts equal | pass |
| pass | either fails, crashes or times out | that outcome, P_E's first |
| fail, crash or timeout | otherwise | the relation run's outcome |

- **Decided** means pass or counterexample.
- **Order.** The property runs run only if the relation run reports no counterexample.
  P_C runs only if P_E decided.
- **Equal verdicts pass,** including two counterexamples. Whether the base property
  holds does not matter, as ADR 0016 §3 decides for the invariant.
- **Timeouts.** Each run has the stage's timeout.
- **Exploration metrics** come from the relation run, so the quality gate is
  unchanged.
- **The oracle** is ADR 0016 §4's, applied to the folded outcome.
- **Known-defect signatures** match the relation module, which now contains `PropC`
  and `FairnessC`. The shapes that TLC cannot check ([temporal
  properties][temporal-manual] §5) are therefore quarantined on either side.

An entry without a property has no property runs, and fairness exists only with a
property. `CheckRequest` gains an obligation for the property runs, and
`TlcCheckerBackend` runs and folds them. Both TLC stages, `tlc` and `tlcn`, run this
backend.

### 5. Configuration and `init`

`run` refuses `temporal_rules` when:
- `[workflow] checkers` lists `apalache`, which supports no fairness and would check
  neither property run; or
- `[generator] kind` is not `module`. Expressions and graph modules have no property.

`fuzztla init --how=mt --temporal` writes the shipped `temporal_rules` and
`checkers = ["tlc"]`. With `--base-corpus`, the base must generate properties, that
is, not ignore `temporal`.

### 6. Seed rules

`TemporalRewrites.tla` ships 22 rules, all proven (P2). `F` and `G` are formula
parameters, `A` an action parameter, `v` a subscript.

| Rule | A | B |
| --- | --- | --- |
| `AlwaysTwice` | `[]F` | `[][]F` |
| `EventuallyTwice` | `<>F` | `<><>F` |
| `AlwaysAndSplit` | `[](F /\ G)` | `[]F /\ []G` |
| `EventuallyOrSplit` | `<>(F \/ G)` | `<>F \/ <>G` |
| `AlwaysAsNotEventually` | `[]F` | `~<>~F` |
| `EventuallyAsNotAlways` | `<>F` | `~[]~F` |
| `AlwaysUnfold` | `[]F` | `F /\ []F` |
| `EventuallyUnfold` | `<>F` | `F \/ <>F` |
| `InfOftenIdem` | `[]<>F` | `[]<>[]<>F` |
| `EventuallyAlwaysIdem` | `<>[]F` | `<>[]<>[]F` |
| `LeadsToDef` | `F ~> G` | `[](F => <>G)` |
| `BoxActionUnchanged` | `[][A]_v` | `[][A \/ UNCHANGED v]_v` |
| `DiamondActionChanged` | `<><<A>>_v` | `<><<A /\ ~UNCHANGED v>>_v` |
| `WFDef` | `WF_v(A)` | `[]<>~ENABLED <<A>>_v \/ []<><<A>>_v` |
| `SFDef` | `SF_v(A)` | `<>[]~ENABLED <<A>>_v \/ []<><<A>>_v` |
| `AlwaysWithEventually` | `[]F` | `[]F /\ <>F` |
| `EventuallyAndIdem` | `<>F` | `<>F /\ <>F` |
| `EventuallyOrIdem` | `<>F` | `<>F \/ <>F` |
| `AlwaysAndIdem` | `[]F` | `[]F /\ []F` |
| `AlwaysOrIdem` | `[]F` | `[]F \/ []F` |
| `AlwaysOrBreak` | `F` | `[]F \/ (F /\ <>~F)` |
| `EventuallyWeaken` | `F` | `F /\ <>F` |

- **`AlwaysOrBreak` and `EventuallyWeaken`** raise the level. They apply only at
  temporal positions (section 1). Most generated properties are state predicates
  (P4); these two rules make TLC build a tableau for them.
- **`AlwaysWithEventually`** moves a property that TLC would check as an invariant onto
  its liveness checker (P2), and so does **`AlwaysOrIdem`**. **`AlwaysAndIdem`** gives
  TLC two invariants to split out of one property. **`EventuallyAndIdem`** and
  **`EventuallyOrIdem`** repeat an eventuality in the tableau, which `EventuallyTwice`
  nests instead.
- **`WFDef` and `SFDef`** replace a fairness operator, which TLC handles specially, by
  its definition, which TLC checks as a general temporal formula.
- **Default weights** follow ADR 0017's tiers: 8 for the rules that change how TLC
  checks a formula (`LeadsToDef`, `WFDef`, `SFDef`, the dualities,
  `AlwaysWithEventually` and `AlwaysOrIdem`), 2 for the splits, 1 for the rest.

### 7. Implementation phases

1. The rule module, the proofs, the loader changes of section 1, and the
   configuration keys of section 5.
2. The walk of section 2 and the relation module of section 3, with a test that a
   corpus without `temporal_rules` decodes a fixed payload as before.
3. The property runs and the fold of section 4, `print` showing the three
   configurations, and `init --temporal`.

## Alternatives considered

- **ADR 0016's implication**, `(PropE => PropC) /\ (PropC => PropE)` under `FairnessE`.
  It needs no extra run, and it detected the negation control as often (P1). However,
  TLC rejected it on 11 of 500 identity pairs, against 1 for the property runs. It also
  never reads `FairnessC`.
- **Two runs: `PropE` checked in the relation run.** This saves one JVM per entry.
  However, a property violation stops the relation run. In P1 that happened in the
  initial states of 55 of the 500 entries, before any transition was checked. The stage
  would also have to tell `Inv` and `StepProperty` from conjuncts of `PropE`, which TLC
  reports under the same codes and names by source location (P3).
- **Refinement, `PROPERTY SpecC` in the relation run.** This checks
  `SpecE ⇒ SpecC` including fairness in one run. It still compares no properties, and
  it checks `FairnessC` as a liveness property under `SpecE` rather than as fairness.
- **Temporal rules in `Rewrites.tla`.** Every `mt` corpus would pay for the property
  runs and change its encoding, including corpora that Apalache checks.
- **Temporal transformations in Java.** They would lose the TLAPS proofs of
  [ADR 0017][adr-0017] §7.

## Consequences

- **Cost.** An entry with a property costs up to three fresh TLC JVMs instead of one.
  In P1, each run took about a second, mostly JVM startup, and three runs took 2.9
  times as long as one. Only corpora with `temporal_rules` pay this.
- **Only TLC is tested.** Apalache's temporal encoding needs a relation without
  fairness, which this ADR does not propose.
- **The generator limits detection.** Most generated properties are state predicates
  (P4), and fairness rarely matters in step-bounded modules. No textual perturbation
  in P1 changed a decided verdict, and only 190 of 500 entries decided their property.
  The level-raising rules of section 6, and a property generator that favours temporal
  operators, would raise detection. The generator is outside this ADR.
- **Rewrites can create shapes TLC cannot check.** For example, `AlwaysAsNotEventually`
  on a formula that contains `[][A]_v` puts it under `~`. The known-defect signatures
  quarantine such entries, which costs candidates but no false verdicts.
- **A later extension:** SpecE ≡ SpecC, so the two property runs explore the same
  states. Comparing their distinct-state counts ([ADR 0019][adr-0019]) would test TLC's
  state space construction under rewritten fairness.
- **Deviations.** This ADR replaces:
  - [ADR 0016][adr-0016] §3, "Temporal properties", and §8 phase 4;
  - [ADR 0017][adr-0017]'s exclusion of temporal rules, its seed rule `AlwaysTwice`, and,
    for temporal rules, the level bullet of §4 and the temporal clause of §5.2.

  Those ADRs carry a note pointing here.

[adr-0016]: 0016-metamorphic-testing.md
[adr-0017]: 0017-rewrite-rule-library.md
[adr-0019]: 0019-state-count-agreement.md
[generators]: ../architecture/ir-generators.md
[temporal-manual]: ../manual/temporal-properties.md
