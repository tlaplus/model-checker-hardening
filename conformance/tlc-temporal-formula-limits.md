# TLC cannot check some well-formed temporal formulas

Observed share: in a 1600-module smoke corpus generated with every category but
`exotic` enabled ([ADR 0007](../docs/decisions/0007-levels-and-temporal-properties.md)),
29 modules made TLC crash with one of the two diagnostics below; in the
1000-module corpus20, generated with the same categories and the first five
signatures below, another 6 did; corpus22, with all seven signatures, left 57
more, and corpus23 76 (see [Signature precision](#signature-precision)). SANY accepted all of them.

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
| Temporal formula under bounded `\A` or `\E` over `Int` or `Nat` | `\E q \in Nat : <>(x = q)` | cannot handle | crash, [apalache-bmc-005](../findings/apalache-bmc/apalache-bmc-005.md) | none |
| `[][A]_v` under `\/`, `=>` or `~` | `[][x' > x]_x \/ <>(x = 3)` | must be of forms | pass | `tlc-always-action-under-connective` |
| `[][A]_v` in an `IF` branch | `IF x = 0 THEN [][x' >= x]_x ELSE TRUE` | must be of forms | pass | none |
| A label directly on `[][A]_v` or on `[A]_v` | `lbl :: [][x' >= x]_x` | must be of forms | pass | none |
| `WF_v(A)` or `SF_v(A)` under `<>` or `~>` | `<>SF_x(A)`, `WF_x(A) ~> (x = 3)` | must be of forms | fail, fairness | `tlc-fairness-under-eventuality` |
| `SF_v(A)` under `[]` | `[]SF_x(x' = x + 1)` | must be of forms | fail, fairness | none |
| A negated `WF_v(A)` under `[]` | `[](~WF_x(A))` | must be of forms | fail, fairness | none |

TLC checks the unlabeled `[][x' >= x]_x` and `[]WF_x(x' = x + 1)`. A label is only
a name for a subexpression, so the labeled row is arguably a TLC defect rather
than a missing case; it is listed here with the other shapes TLC's liveness
translation does not handle.

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
remaining match failed an evaluation before TLC reached the property.

The corpus22 and corpus23 runs, generated with the same categories and the
shipped signatures, left 57 and 76 TLC crashes with either diagnostic. They are
classified by the property that TLC rejected:

| Shape | "Must be of forms", corpus22 | corpus23 | "Cannot handle", corpus22 | corpus23 |
| --- | ---: | ---: | ---: | ---: |
| A label directly on `[][A]_v` or `[A]_v` | 23 | 27 | | |
| `SF_v(A)` under `[]`, possibly negated or labeled | 19 | 34 | | |
| `[][A]_v` in an `IF` branch | 2 | 0 | | |
| `WF_v(A)` under `[]` inside a double negation, or with an action inside its action | 2 | 1 | | |
| `[]` or `[][A]_v` under a bounded `\E` over a constant set | 1 | 2 | | |
| A negated bounded quantifier over `{}` around a temporal formula | 0 | 2 | | |
| Bounded quantifier whose domain depends on the state | | | 8 | 10 |
| Bounded quantifier over `Int` or `Nat` | | | 2 | 0 |

The negated-quantifier row contains no action, yet TLC reports "must be of forms".
With `Init == x = 0`, `Next == UNCHANGED x` and `Spec == Init /\ [][Next]_x`:

| `Prop` | TLC |
| --- | --- |
| `~(\A q \in {}: []FALSE)` | "must be of forms", exit 255 |
| `~(\A q \in {}: <>(x = 1))` | "must be of forms", exit 255 |
| `~(\E q \in {}: []FALSE)` | "must be of forms", exit 255 |
| `(\A q \in {}: []FALSE) => FALSE` | "must be of forms", exit 255 |
| `\A q \in {}: []FALSE` | rejected as a tautology |
| `\E q \in {}: []FALSE` | property violated |
| `(\A q \in {0}: [](x = q)) => FALSE` | property violated |
| `~(\A q \in {}: (x = 0))` | property violated in the initial state |

The corpus23 examples are `d94fbfd5`, whose property is
`(\A q9 \in {}: []FALSE) => FALSE`, and `6e9f1514`, `~(\A q53 \in {}: P ~> FALSE)`.
In the bounded-`\E` row, `\E q \in BOOLEAN: [][x' = x]_x` alone reproduces the
crash; its corpus23 examples are `07a0fee5` and `70e1b933`.

None of these rows has a signature yet. The label rows cannot have one: the
pattern language skips labels
([known-defect signatures](../docs/manual/known-defect-signatures.md), section 3.4).
The `WF` row was not reduced to a reproduction; the bounded-`\E` and
negated-quantifier rows were reduced as above. The negated-`WF` row of the shape table did not occur in the
smoke corpus or corpus20, and a signature for it could not be measured.
