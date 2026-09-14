# TLC cannot check some well-formed temporal formulas

Observed share: in a 1600-module smoke corpus generated with every category but
`exotic` enabled ([ADR 0007](../docs/decisions/0007-levels-and-temporal-properties.md)),
29 modules made TLC crash with one of the two diagnostics below. SANY accepted
all of them.

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
| `[][A]_v` under `\/`, `=>` or `~` | `[][x' > x]_x \/ <>(x = 3)` | must be of forms | pass | none |
| `SF_v(A)` under `<>`, or a negated `WF_v(A)` under `[]` | `<>SF_x(A)` | must be of forms | fail, fairness | none |

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

The signatures cover every crash with either diagnostic in the smoke corpus:
13 "cannot handle" and 16 "must be of forms". No matched module passed in TLC or
produced a counterexample.

| Signature | Matched | TLC crashed with the diagnostic |
| --- | ---: | ---: |
| `tlc-temporal-equivalence` | 6 | 6 |
| `tlc-temporal-case` | 1 | 1 |
| `tlc-temporal-unbounded-quantifier` | 7 | 7 |
| `tlc-eventually-action` | 17 | 16 |
| `tlc-always-action-under-temporal` | 2 | 2 |

`tlc-eventually-action` also matches `[]<><<A>>_v`, which TLC checks; the
remaining match failed an evaluation before TLC reached the property. The last
three rows of the shape table had no occurrence in the corpus, so they have no
signature. The action rule depends on TLC's normalization, and a signature for
them could not be measured.
