# TLC cannot check some well-formed temporal formulas

Observed share: in a 1600-module smoke corpus generated with every category but
`exotic` enabled ([ADR 0007](../docs/decisions/0007-levels-and-temporal-properties.md)),
29 modules made TLC crash with one of the two diagnostics below; in the
1000-module corpus20, generated with the same categories and the first five
signatures below, another 6 did. SANY accepted all of them.

TLC checks a temporal property by translating it into its liveness formulas
(`tlc2.tool.liveness.Liveness`). The translation has no case for several
constructs that TLA<sup>+</sup> allows around a temporal formula, and after
normalizing the formula it accepts actions only in the forms `<>[]A` and `[]<>A`.
Both failures exit with status 255 and are recorded as crashes:

- `TLC cannot handle the temporal formula ...` (error 2213) when a temporal
  formula occurs under an operator the translation does not handle;
- `Temporal formulas containing actions must be of forms <>[]A or []<>A.` when an
  action remains in another form.

Apalache checks most of these shapes. The shipped known-defect signatures
quarantine them, so neither checker spends time on them.

| Shape | Example `Prop` | TLC | Apalache | Signature |
| --- | --- | --- | --- | --- |
| Temporal formula under `<=>` ([tlaplus/tlaplus#1029](https://github.com/tlaplus/tlaplus/issues/1029)) | `(x = 0) <=> <>(x = 3)` | cannot handle | counterexample | `tlc-temporal-equivalence` |
| Temporal formula in a `CASE` arm | `CASE x = 0 -> <>(x = 3) [] OTHER -> TRUE` | cannot handle | counterexample | `tlc-temporal-case` |
| Temporal formula under unbounded `\A` or `\E` | `\E k : <>(x = 3)` | cannot handle | fail, unsupported | `tlc-temporal-unbounded-quantifier` |
| `<><<A>>_v` | `<><<x' > x>>_x` | must be of forms | counterexample | `tlc-eventually-action` |
| `[][A]_v` under `<>`, `~>` or `[]` | `[](<>[][x' > x]_x)` | must be of forms | pass | `tlc-always-action-under-temporal` |
| Temporal formula under bounded `\A` or `\E` whose domain depends on the state | `\A k \in {x} : <>(x >= k)` | cannot handle | crash | none |
| `[][A]_v` under `\/`, `=>` or `~` | `[][x' > x]_x \/ <>(x = 3)` | must be of forms | pass | `tlc-always-action-under-connective` |
| `WF_v(A)` or `SF_v(A)` under `<>` or `~>` | `<>SF_x(A)`, `WF_x(A) ~> (x = 3)` | must be of forms | fail, fairness | `tlc-fairness-under-eventuality` |
| A negated `WF_v(A)` under `[]` | `[](~WF_x(A))` | must be of forms | fail, fairness | none |

The examples use the module below with `SPECIFICATION Spec` and `PROPERTY Prop`
for TLC, and `--temporal=Liveness --length=4` for Apalache.

## Representative MWE

```tla
---- MODULE TlcTemporalFormulaLimits ----
EXTENDS Integers
VARIABLES
  \* @type: Int;
  x,
  \* @type: Int;
  step
Init == x = 0 /\ step = 0
Inc == step < 3 /\ x' = x + 1 /\ step' = step + 1
Next == Inc \/ UNCHANGED <<x, step>>
Inv == TRUE
\* @type: <<Int, Int>>;
vars == <<x, step>>
Fairness == TRUE
Spec == Init /\ [][Next]_vars /\ Fairness
Prop == (x = 0) <=> <>(x = 3)
Liveness == Fairness => Prop
====
```

TLC reports `TLC cannot handle the temporal formula` for `Prop` and exits 255.
Apalache reports a counterexample, since stuttering at `x = 0` never reaches
`x = 3`.

## Signature precision

The first five signatures cover every crash with either diagnostic in the smoke
corpus: 13 "cannot handle" and 16 "must be of forms". The last two were added
after corpus20, where the first five had quarantined the rest; they cover its 6
remaining "must be of forms" crashes. No matched module in either corpus passed in
TLC or produced a counterexample.

| Signature | Corpus | Matched | TLC crashed with the diagnostic |
| --- | --- | ---: | ---: |
| `tlc-temporal-equivalence` | smoke | 6 | 6 |
| `tlc-temporal-case` | smoke | 1 | 1 |
| `tlc-temporal-unbounded-quantifier` | smoke | 7 | 7 |
| `tlc-eventually-action` | smoke | 17 | 16 |
| `tlc-always-action-under-temporal` | smoke | 2 | 2 |
| `tlc-always-action-under-connective` | corpus20, smoke | 4 | 3 |
| `tlc-fairness-under-eventuality` | corpus20, smoke | 4 | 3 |

`tlc-eventually-action` also matches `[]<><<A>>_v`, and
`tlc-always-action-under-connective` also matches `<>[][A]_v` under a connective;
TLC checks both, and neither occurred among the matches. Each signature's
remaining match failed an evaluation before TLC reached the property. The last
two rows of the shape table have no signature, because they did not occur in
either corpus and a signature for them could not be measured.
