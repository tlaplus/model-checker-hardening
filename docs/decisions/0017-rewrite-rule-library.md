# 0017: Rewrite rules as a TLA+ library

**Authors:** Igor Konnov and Claude

**Status:** Proposed

**Date:** 2026-09-24

## Context

[ADR 0016][adr-0016] proposes metamorphic testing: a byte-directed rewriter maps a
module M to an equivalent module M2, and one checker checks that the two agree. That
ADR fixes the walk, the byte encoding and the relation module, but not how a rewrite
rule is defined.

Its first draft made every rule a constant of a Java enum. Each constant carried its
name, weight, applicability, a TLC-only flag, and the rewrite as code. The equivalence
behind each rule would then exist only as Java. It could be neither model-checked nor
proven, and a reviewer would have to read code to see which law a rule applies.

This ADR defines rules in TLA<sup>+</sup> instead. A rule is an operator
`F(x, y) == A = B`, written in an ordinary module. Wherever the rewriter finds an
instance of `A` under a substitution for the parameters, it may replace that instance
with the same instance of `B`. The same definitions can be checked by TLC on small
domains today, and stated as TLAPS theorems later.

**Scope.** This ADR fixes:
- the rule format;
- how a rule module is loaded, matched and applied;
- the rule contract, most of which the loader checks mechanically;
- how rules are validated and pinned for replay.

It proposes seven seed rules to exercise the format. The rule catalog, that is, which
equivalences exist and with what weights, remains the subject of a later ADR.

### Existing infrastructure

The custom operator libraries ([ir-generators.md §11][generators], ADRs
[0014][adr-0014] and [0015][adr-0015]) already import typed TLA<sup>+</sup>
definitions:
- `workflow/library/LibraryTypechecker` runs Snowcat, Apalache's type checker, as
  `typecheck --infer-poly=true`, and decodes the result through `ApalacheIrJson` and
  `LibraryJson.prune`;
- `gen/library/OperatorLibrary` keeps an immutable snapshot, and returns fresh copies
  and the dependency-ordered closure of used definitions;
- custom kinds match a polymorphic signature against a requested type with the
  facade's `TlaTypeUnifier` and `TlaTypeSubstitution`;
- `LibraryManifest` pins the imported sources in `.operator-library` and refuses a
  later run whose sources differ.

The rule loader reuses all four.

### Probes

**Setup.**
- Probed with FuzzTLA `f96d466`, TLC commit `4260e47` (tla2tools
  `1.8.0-20260923.155048-80`) and Apalache 0.62.2 (build `f0dec98`).
- TLC ran through the API that the worker uses, with one worker.
- The seed module:

```tla
------------------------------ MODULE Rewrites ------------------------------
EXTENDS Integers, FiniteSets

PlusZero(x) == x = x + 0

AddSub(x, y) == x = x + y - y

DoubleNeg(P) == P = ~~P

UnionSelf(S) == S = S \union S

UnchangedPrime(x) == (UNCHANGED x) = (x' = x)

ForallNotExists(S, P(_)) == (\A e \in S : P(e)) = ~(\E e \in S : ~P(e))

AlwaysTwice(F) == []F <=> [][]F
=============================================================================
```

**P1: SANY.** SANY accepts the module, including the primed parameter of
`UnchangedPrime` and the temporal body of `AlwaysTwice` in a module without variables.
Without parentheses, `UNCHANGED x = (x' = x)` fails with "Precedence conflict between
ops UNCHANGED … and =".

**P2: Snowcat.** `typecheck --infer-poly=true` types every expression. The inferred
signatures are:

| Rule | Signature | Body in the IR |
| --- | --- | --- |
| `PlusZero` | `(Int) => Bool` | `EQ(x, PLUS(x, 0))` |
| `AddSub` | `(Int, Int) => Bool` | `EQ(x, PLUS(x, MINUS(y, y)))` |
| `DoubleNeg` | `(Bool) => Bool` | `EQ(P, NOT(NOT(P)))` |
| `UnionSelf` | `(Set(o)) => Bool` | `EQ(S, SET_UNION2(S, S))` |
| `UnchangedPrime` | `(h) => Bool` | `EQ(UNCHANGED(x), EQ(PRIME(x), x))` |
| `ForallNotExists` | `(Set(c), (c => Bool)) => Bool` | `EQ(FORALL3(e, S, OPER_APP(P, e)), NOT(EXISTS3(e, S, NOT(OPER_APP(P, e)))))` |
| `AlwaysTwice` | `(Bool) => Bool` | `EQUIV(GLOBALLY(F), GLOBALLY(GLOBALLY(F)))` |

- **Signatures are polymorphic where the law is.** `UnionSelf` holds for sets of any
  element type, and `UnchangedPrime` for any type.
- **A higher-order parameter has an operator type.** The parameter `P(_)` of
  `ForallNotExists` is typed `(c => Bool)` and occurs as `OPER_APP(P, e)`, applied to
  the bound variable.
- **The IR follows TLA<sup>+</sup> precedence, not textual left-to-right order.**
  `x + y - y` is `x + (y - y)`, because binary `-` (11–11) binds tighter than `+`
  (10–10). SANY's semantic tree has the same shape, and so does `1 + 2 - 3`. A pattern
  therefore matches only the shape that its text denotes.

**P3: TLC on constant-level rules.** A module that `EXTENDS Rewrites` asserts each
constant-level rule over small domains:

```tla
D == -3..3
ASSUME \A x, y \in D : AddSub(x, y)
ASSUME \A S \in SUBSET SUBSET {1, 2} : UnionSelf(S)
ASSUME \A S \in SUBSET D, k \in D : ForallNotExists(S, LAMBDA e : e # k)
```

- **All seven assumptions hold.** They cover every constant-level rule, with
  `UnionSelf` at two element types and `ForallNotExists` with two predicates.
- **A wrong rule is caught.** `PlusOne(x) == x = x + 1` fails with "Assumption … is
  false".
- **An action-level rule cannot be assumed.** SANY rejects
  `ASSUME \A x \in 1..2 : UnchangedPrime(x)` with "Level error: assumptions must be
  level 0 (Constant), but this one has level 2."

**P4: TLC on action and temporal rules.** These rules are checked as properties of a
small specification, `VARIABLE v`, `Init == v \in -1..1`, `Next == v' \in -1..1`:
- **Action rule:** `PROPERTY [][UnchangedPrime(v)]_v` holds. The wrong
  `[][(UNCHANGED v) = (v' = v + 1)]_v` is reported as "Action property … is violated".
- **Temporal rule:** `PROPERTY AlwaysTwice(v > 0)` fails with "TLC cannot handle the
  temporal formula". This is the `<=>` limitation of [ADR 0016][adr-0016] probe P4.
  The two implications `([]F => [][]F) /\ ([][]F => []F)`, with `F == v > 0`, hold.

## Decision

### 1. Rule module

A metamorphic corpus names one rule module:

```toml
[metamorphic]
rules = { module = "Rewrites", classpath = ["rewrites"] }
weights = { AddSub = 3, UnionSelf = 0 }
```

- **Every top-level definition of the module is a rule,** in declaration order.
  Definitions of the modules it `EXTENDS` are helpers, never rules. A rule author puts
  helpers in a module of their own.
- **Weights are keyed by rule name.** The default weight is 1, and weight 0 disables a
  rule. A name that is not a rule is a configuration error.
- **Classpath entries resolve as in `[generator] classpath`.** The seed library would
  live in `libraries/rewrites/Rewrites.tla`, next to `libraries/recursion`.

### 2. Rule shape

A rule is `F(p1, …, pn) == A = B` or `F(p1, …, pn) == A <=> B`.

- **Direction.** The rewriter replaces an instance of A with the same instance of B,
  never the reverse. A law used in both directions is two rules.
- **`=` or `<=>`.** Either may relate two non-temporal formulas. `<=>` is required when
  A is temporal, because SANY rejects a temporal operand of `=`
  ([ir-generators.md §7.1][generators]).
- **A** is built from the parameters, literals, bound variables and built-in operators.
  It applies no helper and names no constant or variable.
- **B** may also apply helpers. The relation module links their closure, as
  `OperatorLibrary.declarationsFor` does for custom operators.
- **Precedence.** A and B mean what SANY parses (probe P2). Authors parenthesize
  whenever the grouping is not obvious.

The loader rejects every other shape with a message that names the rule.

### 3. Parameters

The loader classifies each parameter once:

- **Matched.** It occurs in A. A match binds it to a subterm of the rewritten node.
- **Fresh.** It occurs only in B, like `y` in `AddSub`. At rewrite time, the expression
  engine draws it from the rewrite payload ([ADR 0016][adr-0016] §2):
  - at its type under the match's substitution;
  - in the node's lexical scope;
  - in the `STATE` level context, so a fresh operand adds no prime.

  A residual type variable is drawn as custom kinds draw it
  ([ir-generators.md §11][generators]).
- **Higher-order.** `P(_, …, _)` with arity k. In A, it occurs only as `P(e1, …, ek)`,
  where `e1, …, ek` are distinct variables that A binds (Miller's pattern fragment).
  It binds to `LAMBDA e1, …, ek : body`, and each application in B is beta-reduced. A
  higher-order parameter must be matched: it cannot be fresh.

### 4. Matching and instantiation

Matching is byte-free. The walk of [ADR 0016][adr-0016] §2 spends bytes only to choose
among the rules that match a node.

- **Structure.** A literal and a built-in operator match themselves. A matched
  parameter matches any subterm. A parameter that occurs twice in A requires
  alpha-equivalent subterms.
- **Binders.** A binder in A matches a binder of the same form, and its bound variable
  matches the node's bound variable, up to renaming. A higher-order parameter applied
  to bound variables matches any body. That body may mention outer names, and it may
  mention the bound variables only through that application.
- **Types.** A's type scheme, alpha-normalized, is unified with the node's concrete type
  and with the types of the bound subterms. This uses the `TlaTypeUnifier` of custom
  kinds. A match whose unification fails does not apply.
- **Level.** A matched parameter binds to a subterm of any level. The rule's validity
  (section 5) does not depend on the level.
- **Selection.** The rules applicable at a node are those that match it and pass the
  contract checks, in declaration order. The two-byte index of [ADR 0016][adr-0016]
  selects among their weighted slots.
- **Instantiation.** B's bound variables are renamed apart from the node's scope with
  the shared renaming helper. Matched and fresh parameters are then substituted, and
  higher-order applications are beta-reduced. The result has the node's type.

A rule whose A is a bare parameter, such as `PlusZero`, matches every node of the
parameter's type. The loader indexes the rules by the head operator of A, and it
always tries these generic rules.

### 5. Rule contract

This contract replaces the four-item contract of [ADR 0016][adr-0016] §2. The loader
checks items 2–4.

1. **Valid in TLA<sup>+</sup>.** `\A p1, …, pn : F(p1, …, pn)` holds for all values of
   the parameters' types. This is the author's obligation, checked as described in
   section 7. A silly argument, such as `Head(<<>>)`, may make one side fail to
   evaluate. [ADR 0016][adr-0016] classifies that outcome as `fail`, not as a
   counterexample.
2. **Level-preserving.** B uses an action-level operator (`'`, `UNCHANGED`, `[A]_v`,
   `<<A>>_v`, `\cdot`) or `ENABLED` only if A uses one, so a rule may trade
   `UNCHANGED x` for `x' = x` but cannot raise a state-level pattern. B uses a temporal
   operator only if A does.
3. **Assignment-preserving.** TLC treats `x' = e` as an assignment only in certain
   positions: under `/\` and `\/`, in an `IF` branch, in an `\E` body, and in a `LET`
   body. TLC evaluates conjuncts from left to right, so their order matters as well.
   The loader records, for each Boolean matched parameter, whether B keeps it in such
   a position, exactly once. It also records whether B preserves the left-to-right
   order of those parameters. The rewriter skips a match that binds a parameter to a
   primed subterm unless both facts hold. This rules out:
   - `DoubleNeg` on `x' = 1` ([ADR 0016][adr-0016] probe P5);
   - a commutation `(P /\ Q) = (Q /\ P)` on `x' = 1 /\ x' > 0`.
4. **Closed.** Every free name of B is a parameter, a built-in operator, or a helper.

A rule is **TLC-only** when its helper closure includes a definition that Snowcat
cannot type, such as a recursive one. Such a helper is typed through an Apalache-side
twin, as in [ADR 0015][adr-0015]'s diff linkage. The loader derives the flag and
disables the rule unless `[workflow] checkers = ["tlc"]`. The recursion family of the
catalog ADR will rely on this mechanism.

### 6. Encoding and replay

The rule module is part of the byte encoding. Declaration order fixes the slots,
parameter types fix applicability, and the text of B fixes the result. The loader
therefore writes a manifest to the new corpus file `.rewrite-library`, next to
`.operator-library`. The manifest names:
- the Apalache distribution;
- a digest of every rule and helper source file;
- the weights.

Later runs verify it as `LibraryManifest.verify` verifies `.operator-library`.
Editing a rule therefore requires a new corpus. This replaces the enum-order test of
the first draft of [ADR 0016][adr-0016]. Findings cite the rule module and the FuzzTLA
commit, besides the TLC and Apalache commits.

### 7. Validation

- **Constant- and state-level rules** are asserted in a check module,
  `libraries/rewrites/RewritesCheck.tla`, which `EXTENDS` the rule module and states
  `ASSUME \A … \in D : F(…)` over small domains (probe P3). Polymorphic rules are
  asserted at two or more element types. A unit test runs it under TLC, as for
  `src/test/resources/recursion/RecursionPairs.tla`.
- **Action rules** are asserted as action properties `[][F(v)]_v` of a small
  specification with variables (probe P4).
- **Temporal rules** keep `<=>` in the rule module, which is what a proof needs. The
  check module states them as two implications, because TLC rejects `<=>` between
  temporal formulas (probe P4).
- **Proofs (later).** A module `RewritesProofs.tla` would state
  `THEOREM \A x \in Int : PlusZero(x)`, deriving the domain hypotheses from the
  signatures. TLAPS proofs are out of scope here.

### 8. Package placement

- **`workflow.rewrite.RewritePreparation`** runs Snowcat, writes and verifies the
  manifest, and hands over the typed module. It is shaped like `LibraryPreparation`.
- **`gen.rewrite.RewriteLibrary`** is the immutable result: the rules in declaration
  order, the parameter classes, the head-operator index, and the precomputed contract
  facts.
- **The matcher and the rewriter** live beside `RewriteLibrary` in `gen.rewrite`.

`gen` stays free of I/O and external processes ([ir-generators.md §11][generators]).

## Alternatives considered

- **Rules as a Java enum** (the first draft of ADR 0016). The law would exist only in
  code: it could not be model-checked or proven, and a reviewer would have to read the
  Java to see it.
- **Bidirectional rules.** Most laws are useful in one direction only. `x → x + 0`
  grows a term, while `x + 0 → x` matches only terms that the generator rarely
  produces. A reverse rule costs one line.
- **Rules as `THEOREM`s.** A theorem has no parameters by which weights, parameter
  classes and diagnostics can name the parts. An operator can be applied by a theorem
  later, so proofs lose nothing.
- **An explicit list of rules in TOML,** as for `custom_operators`. It would repeat the
  module's declarations and could fall out of step with them.
- **First-order parameters only.** They cannot express laws under binders:
  quantifiers, `CHOOSE`, set comprehensions and function constructors.
- **Full higher-order matching.** It is undecidable in general, and a pattern may match
  in more than one way. In Miller's fragment, matching is decidable and yields at most one
  match.

## Consequences

- **Rules are reviewable and checkable as TLA<sup>+</sup>.** A rule's law is its text.
  TLC checks it over small domains, and TLAPS can prove it later.
- **Matching costs time.** Every node is matched against the rules indexed by its head
  operator and against every generic rule. The cost is byte-free, and ADR 0016's
  `max_rewrites` bounds the rewrites.
- **A rule edit starts a new corpus** (section 6).
- **Rule authors must know TLC's assignment semantics** only for rules with Boolean
  parameters. The loader and rewriter enforce section 5.3 either way.
- **Precedence surprises are visible.** A pattern that does not match what its author
  expected shows up in `fuzztla print`, which lists the applied rules.

[adr-0014]: 0014-per-checker-library-linking.md
[adr-0015]: 0015-diff-linked-libraries.md
[adr-0016]: 0016-metamorphic-testing.md
[generators]: ../architecture/ir-generators.md
