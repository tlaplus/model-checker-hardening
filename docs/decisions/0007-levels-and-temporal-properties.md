# 0007: Levels and temporal properties

**Authors:** Igor Konnov and Claude

**Status:** Accepted

**Date:** 2026-09-14

## Context

The expression catalog contains the TLA<sup>+</sup> action and temporal operators
(`BooleanExpressionKind`): prime, `UNCHANGED`, `[A]_v`, `<<A>>_v`, `ENABLED`,
`[]`, `<>`, `~>`, `-+->`, `WF`, `SF`, `\cdot`, `\EE` and `\AA`. None of them
reaches a checker in a meaningful form:

- The decoder ignores TLA<sup>+</sup> levels. `BooleanExprGenFactory` draws the
  operands of every temporal form as arbitrary expressions, and nothing relates
  them to state variables.
- In `expr` mode the scope contains no state variable. `PRIME_EQUAL` rejects
  its input, and the temporal forms produce constant formulas that the wrapper
  places in `Init` and `Inv`.
- In `module` mode, the `IrSpecGeneratorEngine` constructor always ignores the
  `action`, `temporal` and `exotic` categories, whatever the configuration
  says. A prime under a negation or a quantifier would break the accounting
  invariant of the action shape ([ir-generators.md §9.2][ir-9-2]).
- Neither checker is asked about a temporal property. TLC receives
  `INIT/NEXT/INVARIANT/CONSTRAINT`, and Apalache receives `--inv`.

This ADR makes generation level-aware, and adds primed guards, temporal
properties and fairness to generated modules. Both safety and liveness
properties are in scope.

### Checker support, measured

We ran hand-written modules with the skeleton proposed below through TLC
(tla2tools 1.8.0-SNAPSHOT) and Apalache 0.62.2. Every module has two
variables, `N == 3`, and `Next == Inc \/ UNCHANGED <<x, step>>` unless the table
says otherwise. Apalache runs with `--temporal=Liveness`. Exit status 255 is
classified as `crashed` by both `TlcOutcomeClassifier` and
`ApalacheOutcomeClassifier`.

| Property, or variation | TLC | Apalache |
| --- | --- | --- |
| `<>(x = N)` | 13 violated | 12 counterexample |
| `<>(x = N)`, `Next == Inc` (no stuttering disjunct) | 13 violated | 0 `ExecutionsTooShort` |
| `[](x <= N)` | 0 | 0 |
| `[][x' > x]_x`, with a decrementing disjunct | 13 | 12 |
| `[]<>(step < N)`, `--length=N` | 13 | 0 |
| `[]<>(step < N)`, `--length=N+1` | 13 | 12 |
| `(x = 1) ~> (x = 2)`; `(<>(x = 1)) ~> [](x >= 1)` | agree | agree |
| `[]<><<x' > x>>_x`; `<>[][x' > x]_x`; `~<>[][x' > x]_x` | agree | agree |
| `<>(x = N) => []<><<x' > x>>_x`; `[]([]<><<…>>_x)` | agree | agree |
| `[][A]_x /\ <>(x = N)`; `[][A]_x /\ (x = 0)` | agree | agree |
| `IF x = 0 THEN <>(x = N) ELSE []TRUE`; `LET F == <>… IN F` | agree | agree |
| `<><<x' > x>>_x`, standalone or under `[]` and `=>` | 255 "must be of forms <>[]A or []<>A" | 12 |
| `[][A]_x` under `=>`, `~`, `\/` or `[]` | 255, same message | 0 or 12 |
| `(x = 0) ~> []<><<A>>_x` | 255 | 255 assignment error |
| `\E k \in 0..1 : <>(x = k)`, and with `[]<><<…>>` | 0 / 13 | 255 "Variable k$1 is not assigned a value" |
| `WF_vars(Inc)` or `SF_vars(Inc)` in `Liveness` | 0 | 255 `NotImplementedError: Handling fairness is not supported yet!` |
| `WF_vars(Inc)` only in an unused `Spec`, no `--temporal` | 0 | 0 |
| `ENABLED Inc` in `Inv`, a guard or a property | 0 / 13 | 75 "unsupported expression: ENABLED" |
| `(x = 0) -+-> (x >= 0)` | 255 "cannot handle" | 255 `NotImplementedError` |
| `[](x' >= x)` (level error) | 150 | 255 |
| `<>(x = N) <=> FALSE`; `WF_vars(Inc) <=> FALSE`; `CASE x = 0 -> <>(x = N) [] OTHER -> []TRUE` | 255 "cannot handle" | 12 / 255 |
| `<>SF_vars(Inc)`; `[](~WF_vars(Inc))`; `[](<>[][A]_x)`; `<>([]<><<A>>_x)` | 255 "must be of forms <>[]A or []<>A" | 0, 12 or 255 |
| `~WF_vars(Inc)`; `[]WF_vars(Inc)`; `WF_vars(Inc) \/ <>(x = N)`; `<>(x = N) => WF_vars(Inc)` | 0 or 13 | 255 fairness |
| `~((x = 0) ~> (x = 9))`; `(<>(x = 1)) ~> [](x >= 1)`; `<>([](x = N))` | agree | agree |
| the parenthesized forms the IR printer emits, `[]([Next]_(vars))`, `[](<>(<<A>>_(x)))` | same as unparenthesized | same |

We also probed guards placed after the assignments of an action, the placement
proposed below: `x' = 1`, `\E k \in 0..5 : x' = k`, `~(x' = 2)`,
`IF x' > 1 THEN step' = 2 ELSE TRUE`, and `x' \in {1, 2}`. Both checkers returned
the same verdict for each, including one counterexample.

The rows after the level error were measured while implementing this ADR, when
generated properties exposed nestings the first probes had not covered. The
measurements establish five facts:

1. **Stuttering.** Apalache adds no implicit stuttering, which TLC does. With
   an explicit stuttering disjunct in `Next`, the two checkers agree on liveness.
2. **Lasso length.** If every non-stuttering step increments `step` and is
   guarded by `step < N`, then every cycle is a stuttering self-loop. Every
   lasso therefore fits in `N + 1` transitions, and `--length=N` misses some.
3. **Nesting accepted by both.** Actions may occur in a temporal formula only
   in these forms:
   - `[]<><<A>>_v`, `<>[][A]_v`, `WF_v(A)` and `SF_v(A)`, separated from the top
     of the formula by `~`, `/\`, `\/`, `=>`, `IF` branches and `LET` bodies alone.
     TLC rejects them under `[]`, `<>` or `~>` in general: `[]<><<A>>_v` under
     `[]` and `WF` under `[]` pass, but their negations and the dual nestings do
     not, so none is generated there;
   - `[][A]_v`, as a top-level conjunct only.

   A temporal formula passes neither `<=>` ([tlaplus/tlaplus#1029][tlc-1029]) nor
   a `CASE` arm in TLC, and neither checker accepts a quantifier over a temporal
   body. Apalache crashes on it even
   when the body contains no action.
4. **Unsupported by at least one checker.** `-+->`, `\EE`, `\AA` and `\cdot`
   crash both checkers. `WF` and `SF` crash Apalache, and `ENABLED` makes
   Apalache fail.
5. **Level errors fail in the parser.** SANY rejects them (exit 150), so a
   level mistake in the decoder shows up as a parser failure, not as a checker
   difference.

## Decision

### Levels

Every expression kind declares the level of the formula it builds:

| `Level` | Kinds |
| --- | --- |
| `STATE` | every kind not listed below, including `ENABLED` |
| `ACTION` | `PRIME`, `PRIME_EQUAL`, `UNCHANGED`, `STUTTER` (`[A]_v`), `NO_STUTTER` (`<<A>>_v`), `ACTION_THEN` |
| `TEMPORAL` | `ALWAYS`, `EVENTUALLY`, `LEADS_TO`, `GUARANTEES`, `TEMPORAL_EXISTS`, `TEMPORAL_FORALL` |
| `ACTION_TEMPORAL` | `WEAK_FAIR`, `STRONG_FAIR`, and the new `INFINITELY_OFTEN_ACTION` (`[]<><<A>>_v`) and `EVENTUALLY_ALWAYS_ACTION` (`<>[][A]_v`) |

Every expression request is drawn in a `LevelContext`. The context is a dynamic
ceiling kept in `GenerationContext`, which restores it on exit like
`withinExceptReplacement`. A kind is selectable only if its context admits its
level. `IrExprGenFactory` applies the admission check before it asks the kind for
its `selectionWeight`, so a kind states its level once and no kind repeats the
check.

| `LevelContext` | Admits | Used for |
| --- | --- | --- |
| `STATE` (default) | `STATE` | `Init`, `Inv`, auxiliary and action operators, shape predicates and assignments, existing guards, fairness subscripts |
| `ACTION` | `STATE`, `ACTION` | post-assignment guards, the operand of `ENABLED`, the action of `[A]_v`, `WF`, `SF` and the new kinds, top-level `[][A]_v` |
| `TEMPORAL` | `STATE`, `TEMPORAL`, `ACTION_TEMPORAL` | the property formula |
| `ACTION_FREE_TEMPORAL` | `STATE`, `TEMPORAL` | operands of `[]`, `<>`, `~>` and `-+->` |

Operands inherit a context by the following rules:

- **Transparent forms keep the context:** `NOT`, `AND`, `OR`, `IMPLIES`, the
  branches of `IF`, the body of `LET`, and `LABEL`. `EQUIVALENT` and `CASE` lower
  their operands (fact 3).
- **Level-changing forms set it explicitly:**
  - `PRIME`, `UNCHANGED`: `STATE`.
  - `ENABLED`: `ACTION`.
  - `[]`, `<>`, `~>`, `-+->`: `ACTION_FREE_TEMPORAL`.
  - `[A]_v`, `<<A>>_v`, `WF`, `SF` and the two new kinds: `ACTION` for the
    action and `STATE` for the subscript.
- **Every other operand is lowered:** a temporal context becomes `STATE`, and
  `ACTION` stays `ACTION`, so `x' + 1` remains reachable. Lowering is the
  default in `AbstractExprGenFactory.expression`, so a new form is safe unless
  it opts in to transparency.

These rules also cover:

- **Quantifier bodies:** they are lowered. TLA<sup>+</sup> allows a temporal
  body, but Apalache crashes on one (fact 3).
- **LET definitions, lambda bodies and operator arguments:** they are drawn in
  `STATE`, so every name in scope has state level and can be used in any
  context.

Consequences for the catalog:

- **`GUARANTEES` (`-+->`)** moves from category `temporal` to `exotic`. Its
  catalog position does not change (fact 4).
- **The two new kinds** form a new family, `TemporalActionExpressionKind`,
  appended after `ApplicativeExpressionKind`, so the existing selection indices
  keep their meaning. They belong to category `temporal`.
- **`PRIME_EQUAL`** has weight zero when no state variable is in scope, instead
  of rejecting the input.
- **`IrSpecGeneratorEngine`** stops overriding the ignore list. The `STATE`
  default context protects the accounting of §9.2, a job the category exclusion
  used to do.

### Module shape

A generated module becomes:

```tla
VARIABLES var0, ..., step
Init == ...                                  \* unchanged
Next == \/ step < MaxSteps /\ guards /\ shape /\ step' = step + 1 /\ postGuards
        \/ ...
        \/ UNCHANGED <<var0, ..., step>>     \* skeleton, byte-free
Inv == ...                                   \* unchanged
Fairness == /\ WF_v(A) /\ SF_v(A) ...        \* TRUE when there are none
Spec == Init /\ [][Next]_<<var0, ..., step>> /\ Fairness
Prop == /\ [][A]_v /\ ... /\ formula         \* TRUE without a property
Liveness == Fairness => Prop
```

- **Post-assignment guards.** A terminated list of Boolean expressions in
  context `ACTION` follows the step update; with `action` ignored the list spends
  no marker. By then the shape has determined
  every primed variable, so TLC's left-to-right evaluation and Apalache's
  assignment analysis both see the assignments first. The accounting invariant
  of §9.2 applies to the conjuncts up to and including the step update. The
  update is a fixed IR shape, so a test can tell the spine from the guards.
- **Closed bounding.** Each generated disjunct starts with the byte-free guard
  `step < maximumSteps`, and the skeleton adds the stuttering disjunct.
  `Bound`, `GeneratedSpec.boundPredicate` and TLC's `CONSTRAINT` are removed.
  Both checkers then explore the finite graph of states `0..maximumSteps`, and
  the off-by-one reasoning of §9.3 no longer applies. Apalache's length is
  `maximumSteps` for invariants and `maximumSteps + 1` when a property is
  checked (fact 2).
- **Property section.** `ModuleSection.PROPERTY` is appended with weight 3 and
  drawn with the state scope. It is present only when the `temporal` category is
  enabled; an absent section receives no bytes, so a corpus that ignores
  `temporal` keeps its section layout. It decodes, in order, each part under a
  node budget of its own:
  1. a marker saying whether the module has a property;
  2. one formula in context `TEMPORAL`, first so that a short section still
     decodes one;
  3. a terminated list of at most `max_fairness` fairness conditions, each
     `WF` or `SF` chosen by one Boolean;
  4. a terminated list of top-level `[][A]_v` conjuncts.

  `PropertyGenFactory` owns this section. `GeneratedSpec` gains
  `Optional<TemporalProperty>`, and `generated()` includes its expressions, so
  richness scoring and signature matching see them.
- **Expression wrapper.** It defines `Fairness == TRUE` and `Prop == TRUE`,
  and asks for no temporal check.

### Checker invocation

- **`ToolInput`.** It carries `CheckRequest(int length, boolean temporalProperty)`
  instead of a bare length, and `ToolWorkerProtocol` encodes both fields.
- **TLC.** The worker writes a configuration per input: `SPECIFICATION Spec`
  and `INVARIANT Inv`, plus `PROPERTY Prop` when `temporalProperty` holds.
  Fairness therefore constrains TLC's liveness check through `Spec`.
- **Apalache.** It receives `--init=Init --next=Next --inv=Inv`, plus
  `--temporal=Liveness` when `temporalProperty` holds. Passing `Liveness`
  rather than `Prop` asks Apalache the same question TLC answers: a module
  with fairness makes Apalache report its limitation instead of returning a
  counterexample that fairness would exclude. An unused `Spec` does not
  disturb Apalache (fact 4).
- **Unsupported forms reach the checkers.** No gating is added. `ENABLED`
  already classifies as `fail` (exit 75). For fairness,
  `ApalacheOutcomeClassifier` maps exit status 255 with the diagnostic
  `Handling fairness is not supported yet!` to `fail` with code `spec_eval`,
  as it already does for undefined arithmetic, and `ApalacheFailureDetail`
  records that message rather than the preceding "Unhandled exception". The
  aggregator then compares the pair, and the triager classifies it using
  conformance documents for fairness and `ENABLED`.
  Without this rule, every module with fairness would count as an Apalache crash
  and would never be aggregated.
- **Signature matching** ([ADR 0006][]). The evaluated roots become `Init`,
  `Next`, `Inv`, `Spec`, `Prop` and `Liveness`.

### Configuration

- `[generator] max_fairness`: an integer, default 2, bounding the fairness
  conditions per module.
- The default `ignore` list is unchanged:
  `["action", "temporal", "unbound", "exotic"]`. A corpus enables levels by
  removing `action` and `temporal`. `exotic` stays ignored because its forms
  crash both checkers (fact 4).

## Consequences

- **Stored inputs.** Under the default ignore list the byte layout is unchanged:
  the property section is absent and the post-assignment guards spend no marker,
  so stored `expr` and `module` inputs decode to the same choices, and a module
  gains only the byte-free step guard and the skeleton's stuttering disjunct.
  With `action` or `temporal` enabled, the guard markers, the property section
  and the level-dependent weights change what bytes mean. Per repository policy
  there is no migration.
- **Bounding changes without a bound change.** Removing the state constraint
  changes TLC's configuration and every module's `Next` without changing which
  states are explored.
- **More Apalache work for properties.** A module with a property costs Apalache
  one more transition and the loop encoding, which doubles the state variables.
  The property marker keeps modules without a property at the current cost.
- **Documents revised.** [ir-generators.md][ir] (§5, §7.1, §9.1–9.4 and rules 2,
  3 and 9), [fuzzing-workflows.md][fw], ADR 0006's list of evaluated roots and its
  manual, and two conformance documents with triager signatures.
- **Unexercised features.** Some TLA<sup>+</sup> features stay out of generated
  modules: quantifiers, `<=>` and `CASE` over temporal formulas, `[][A]_v` below
  the top level, actions under `[]`, `<>` and `~>`, and every `exotic` form. They
  are excluded because the checkers crash on them (facts 3 and 4). A future ADR
  can revisit this if the crashes should be reported as findings.

[ADR 0006]: 0006-known-defect-signatures.md
[tlc-1029]: https://github.com/tlaplus/tlaplus/issues/1029
[ir]: ../architecture/ir-generators.md
[ir-9-2]: ../architecture/ir-generators.md#92-action-shape
[fw]: ../architecture/fuzzing-workflows.md
