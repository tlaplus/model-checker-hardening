# TLC cannot check some well-formed temporal formulas

Observed share: in a 1600-module smoke corpus generated with every category but
`exotic` enabled ([ADR 0007](../docs/decisions/0007-levels-and-temporal-properties.md)),
29 modules made TLC crash with one of the two diagnostics below; in the
1000-module corpus20, generated with the same categories and the first five
signatures below, another 6 did; corpus22, with all seven signatures, left 57
more, corpus23 76, corpus24 69, corpus25 125, corpus26 66, corpus28 34 and
corpus29 42 (see [Signature precision](#signature-precision)). SANY accepted all
of them. corpus29's four `cannot handle` crashes are all the row with a
state-dependent bounded quantifier domain.

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
| A negated `IF` around a temporal formula whose constant condition selects a constant branch | `~(IF FALSE THEN <>(x = 3) ELSE FALSE)` | must be of forms | pass | none |

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

The corpus22, corpus23, corpus24, corpus25, corpus26 and corpus28 runs, generated
with the same categories and the shipped signatures, left 57, 76, 69, 125, 66 and
34 TLC crashes with either diagnostic. corpus25 uses the same generator settings as
corpus24 with twice its entry budget, so its larger count is the corpus size, not
a new shape. corpus26 uses corpus25's generator settings with half its entry
budget. corpus28 adds generational mutation
([ADR 0010](../docs/decisions/0010-mutation.md)) to the same categories.
They are classified by the property that TLC rejected:

| Shape | "Must be of forms", corpus22 | corpus23 | corpus24 | corpus25 | corpus26 | corpus28 | "Cannot handle", corpus22 | corpus23 | corpus24 | corpus25 | corpus26 | corpus28 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| A label directly on `[][A]_v` or `[A]_v` | 23 | 27 | 29 | 38 | 26 | 13 | | | | | | |
| `SF_v(A)` under `[]`, possibly negated, labeled or nested | 19 | 34 | 30 | 48 | 27 | 15 | | | | | | |
| `[][A]_v` in an `IF` branch | 2 | 0 | 1 | 8 | 0 | 0 | | | | | | |
| `WF_v(A)` under `[]` inside a double negation, or with an action inside its action | 2 | 1 | 0 | 0 | 0 | 0 | | | | | | |
| A negated `WF_v(A)` under `[]` | | | 3 | 1 | 1 | 1 | | | | | | |
| `WF_v(A)` under `[]`, plain or doubly nested, or under a negated `IF` | | | | 3 | 0 | 0 | | | | | | |
| `[]` or `[][A]_v` under a bounded `\E` over a constant set | 1 | 2 | 1 | 0 | 1 | 0 | | | | | | |
| A negated bounded quantifier over `{}` around a temporal formula | 0 | 2 | 0 | 0 | 1 | 0 | | | | | | |
| A negated `IF` whose constant condition selects a constant branch | | | | | 1 | 0 | | | | | | |
| Bounded quantifier whose domain depends on the state | | | | | | | 8 | 10 | 3 | 13 | 5 | 4 |
| Bounded quantifier over `Int` or `Nat` | | | | | | | 2 | 0 | 1 | 5 | 4 | 1 |
| Bounded quantifier whose constant domain fails to evaluate, [tlc-011](../findings/TLC/tlc-011.md) | | | | | | | | | 1 | 9 | 0 | 0 |

The negated-`WF` and failing-domain rows were first separated in corpus24; the
earlier columns may count such crashes under a neighboring row.

The corpus24 negated-`WF` crashes are `15e8eb25`, `a89fe219` and `9ddcedb6`,
whose property is
`[](WF_FALSE(A) => FALSE)`. The nested `SF` shapes of corpus24 include
`[]([]SF_v(A))`, `<>TRUE => []SF_v(A)` and `[]SF_v(A) => FALSE`.

For a bounded `\E`, TLC reports "cannot handle" without a cause for any
exception raised while it enumerates the domain or translates the body, so a
domain that depends on the state and a constant domain that fails to evaluate
print the same line. The latter is an incorrect specification, not a shape the
translation lacks, and is filed as [tlc-011](../findings/TLC/tlc-011.md). Its
corpus24 example is `c2a07f51`.

The corpus25 rows were classified by the outermost shape of `Prop`, so an entry
with more than one rejected construct is counted once. Three observations:

- The plain-`WF` row is new. Its three crashes are `17d3fbcd`, `[](WF_e(P))`;
  `e81662be`, `[]([](WF_e(P)))`; and `ae5ec647`, `~(IF p THEN WF_e(P) ELSE q)`.
  None has an action inside the fairness action, which is what separates them
  from the corpus24 double-negation row. The one negated-`WF` crash is
  `9b35d44e`, `[](~(WF_e(P)))`.
- No fairness subscript among the 125 is a variable: every `WF` and `SF` is
  written `WF_FALSE(A)`, `SF_("")(A)` or with another non-variable expression in
  the subscript. `[]WF_x(A)`, which TLC checks, did not occur.
- The `\A` binder reaches the failing-constant-domain row for the first time.
  Seven of the nine are `\E`, as in tlc-011; `fe734055` and `36032bed` are `\A`
  over a constant domain that raises the same way. Their domains apply `Head` to
  an empty sequence or `VariantGetUnsafe` to a mismatched tag.

The corpus26 rows were classified the same way, with TLC2 version
2026.09.16.090156. Four observations:

- The negated-`IF` row is new. Its one crash, `1d3f1339`, has the property
  `C => (IF FALSE THEN <>(P <=> FALSE) ELSE FALSE)` for a constant `C`. It
  contains no action and is reduced [below](#negated-constant-if).
- `524e2c6c` is counted in the label row, its outermost shape. Its property is
  `lbl :: \E q \in {c}: lbl(q) :: [][A]_v`, so it also has the bounded-`\E` shape.
- The negated-`WF` crash is `7750e2e6`, `[](WF_e(A) => FALSE)`, the corpus24
  shape. The bounded-`\E` crash is `316acaaa`, `\E q \in BOOLEAN: [][q]_e`, and
  the negated-quantifier crash is `55766806`, `(\E q \in D: P ~> FALSE) => FALSE`
  where `D` is a `VariantGetOrElse` that evaluates to `{}`.
- Two of the five state-dependent domains also fail to evaluate: `17a6f2f8`
  quantifies over `{<<>>[step]}` and `ea0d75c5` over `<<>>[step]`. They are
  counted as state-dependent, since the domain reads `step`.

The corpus28 rows were classified the same way, with TLC2 version
2026.09.16.232117 (commit `957faa0`) and FuzzTLA `41bda26`. The triager left 44
TLC crashes unclassified; 34 have one of the two diagnostics and are counted
above. The other 10 report only `TLC worker exited while processing the input`;
all 10 ended in the same second, and each passes or fails an ordinary
evaluation when rerun, so they are not TLC diagnoses. Four observations:

- No row is new. Three of the 34 crashes are `splice` mutants, `38781e3c`,
  `42e70b20` and `52ea398d`; all three are in the label row. The other 31 were
  generated by PBT.
- The `SF` row contains 13 plain `[](SF_e(A))`, the nested `c9ea3f8e`,
  `[](SF_FALSE(A) => FALSE)`, and the negated `fc148b97`, `~([](SF_FALSE(A)))`.
  As in corpus25, no `SF` subscript under `[]` is a variable; the subscripts are
  `FALSE`, a `CHOOSE`, an `IF` or a tuple.
- The negated-`WF` crash is `530b6b05`, `[](~(WF_FALSE(A)))`, the corpus25 shape.
- The state-dependent domains are `{step}` in `8c8be5f0` and `dd52ddc6`,
  `{0, step}` under a label in `c5432df5`, and a two-element set that reads
  `step` in `6bd5915d`, whose body `SF_var2(var2)` is the only fairness formula
  with a variable subscript. That domain also contains `CHOOSE b \in {}: P`, so
  it fails to evaluate as well; it is counted as state-dependent. The `Int` row
  is `df2fd897`, `\A q \in Int: SF_FALSE(A)`.

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

<a id="negated-constant-if"></a>
The negated-`IF` row has no action either. It needs both a negation, or a
position that implies one, and a constant `IF` condition that selects a branch
without a temporal operator. With the module above:

| `Prop` | TLC |
| --- | --- |
| `~(IF FALSE THEN <>(x = 1) ELSE FALSE)` | "must be of forms", exit 255 |
| `~(IF FALSE THEN <>(x = 1) ELSE TRUE)` | "must be of forms", exit 255 |
| `~(IF TRUE THEN FALSE ELSE <>(x = 1))` | "must be of forms", exit 255 |
| `~(IF 1 = 2 THEN <>(x = 1) ELSE FALSE)` | "must be of forms", exit 255 |
| `~(IF FALSE THEN [](x = 1) ELSE FALSE)` | "must be of forms", exit 255 |
| `TRUE => (IF FALSE THEN <>(x = 1) ELSE FALSE)` | "must be of forms", exit 255 |
| `<>(x = 0) \/ ~(IF FALSE THEN <>(x = 1) ELSE FALSE)` | "must be of forms", exit 255 |
| `[](~(IF FALSE THEN <>(x = 1) ELSE FALSE))` | "must be of forms", exit 255 |
| `IF FALSE THEN <>(x = 1) ELSE FALSE` | property violated |
| `IF FALSE THEN <>(x = 1) ELSE TRUE` | rejected as a tautology |
| `~(IF TRUE THEN <>(x = 1) ELSE FALSE)` | no error |
| `~(IF x = 0 THEN <>(x = 1) ELSE FALSE)` | no error |
| `~(IF FALSE THEN <>(x = 1) ELSE x = 0)` | property violated |
| `~(IF FALSE THEN <>(x = 1) ELSE <>FALSE)` | no error |
| `~(IF FALSE THEN x = 1 ELSE FALSE)` | no error |

Like the negated quantifier over `{}`, the failing formulas reduce to a constant
under a negation, which suggests the same cause in the liveness translation;
the translation code was not inspected. With the representative MWE and
`Prop == ~(IF FALSE THEN <>(x = 3) ELSE FALSE)`, Apalache 0.62.2 reports
`NoError` for `--temporal=Liveness --length=4`.

None of these rows has a signature yet. The label rows cannot have one: the
pattern language skips labels
([known-defect signatures](../docs/manual/known-defect-signatures.md), section 3.4).
The `WF` row was not reduced to a reproduction; the bounded-`\E`,
negated-quantifier and negated-`IF` rows were reduced as above. The negated-`WF` row of the shape table did not occur in the
smoke corpus or corpus20, and a signature for it could not be measured.
