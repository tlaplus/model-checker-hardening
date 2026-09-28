# Metamorphic testing

> **Status:** Proposed. Nothing on this page is implemented yet.
> [ADR 0016](../decisions/0016-metamorphic-testing.md) records the design and the probes
> behind it, and [ADR 0017](../decisions/0017-rewrite-rule-library.md) the rule format.
> The rule catalog will be specified in a later ADR.

A metamorphic campaign checks one model checker against itself. Each input pairs a
generated module M1 with a rewrite M2 that is equivalent in TLA<sup>+</sup>, for example
`x` rewritten as `x + y - y`. The checker explores one of the two and checks that its
initial states and transitions satisfy the other. A violation means one of two things:
- the checker evaluates the two sides differently;
- a rewrite rule is unsound.

Unlike a [recursion campaign](recursive-operators.md), no second checker is needed. The
relation can be checked by TLC alone, including on recursive definitions that Apalache
rejects.

## 1. Setting up a corpus

Lifting elite entries of an existing conformance corpus gives the richest inputs:

```sh
./bin/fuzztla run --how=pbt --corpus corpus-conf          # conformance generations
./bin/fuzztla init --corpus corpus-mt
# edit corpus-mt/config.toml as below
./bin/fuzztla run --how=mt --corpus corpus-mt
```

```toml
[generator]
kind = "module"               # or "expr"

[workflow]
checkers = ["tlc"]            # default ["tlc", "apalache"]

[metamorphic]
rules = { module = "Rewrites", classpath = ["rewrites"] }
weights = { AddSub = 3 }      # default 1 per rule; 0 disables a rule
base_corpus = "../corpus-conf"
lift_ratio = 0.5
max_rewrites = 16
max_rewrite_depth = 4
```

- **The technique is fixed.** The first run records `mt` in `.technique`. Later runs
  must pass `--how=mt`, and `print` and `export-db` read the file. A corpus without it
  is a `pbt` corpus.
- **Lifted parents.** A generation takes `lift_ratio` of its entries from lifted
  parents: the `04quality-pass` entries of `base_corpus`, each combined with random
  rewrite bytes. `[mutator] feedback_ratio` still sets the share of byte mutants, and
  PBT fills the rest.
- **The base corpus is read-only.** It must be a `pbt` corpus. It is never written
  to, and an entry keeps its parent's bytes, so `base_corpus` is not needed to replay
  it.
- **The checker set is fixed.** The first run records `checkers` in `.checkers`, and
  later runs must use the same list.
- **The rules are fixed.** The first run records the rule module's sources and the
  weights in `.rewrite-library`. Editing a rule requires a new corpus.

## 2. What the checker sees

`fuzztla print --spec` shows the relation module. An orientation bit in the entry
chooses the explored side E and the checked side C:

| Orientation | E (explored) | C (checked) |
| --- | --- | --- |
| even | M1 | M2 |
| odd | M2 | M1 |

Each side's parts are definitions of their own, named by the side X ∈ {E, C}:

| Definition | Meaning |
| --- | --- |
| `InitX` | initial predicate |
| `ActionX` | next-state action without its stuttering disjunct |
| `InvX` | state invariant |
| `PropX` | temporal property |
| `FairnessX` | fairness conditions |
| `exprX` | the expression of an `expr` entry |

The relation module combines them:

| Entry point | `expr` | `module` |
| --- | --- | --- |
| `Init` | `v = exprE` | `InitE` |
| `Next` | `UNCHANGED v` | `ActionE \/ UNCHANGED vars` |
| `Inv` | `v = exprC` | `(step = 0 => InitC) /\ (InvE <=> InvC)` |
| `Step` | none | `[ActionC]_vars`, an action invariant |
| `Prop` | none | `(PropE => PropC) /\ (PropC => PropE)` when the base has a property |

- **One copy of the variables.** M2 shares M1's variables. Only its operators are
  renamed.
- **The action invariant.** TLC checks it as `PROPERTY StepProperty`, where
  `StepProperty == [][Step]_vars`, and reports "Action property StepProperty is
  violated". Apalache checks it with `--inv=Inv,Step`.
- **Deadlock detection stays off,** as in a conformance corpus.
- **Only the relation is checked.** Whether the base invariant or property holds does
  not matter.
- **The explored module is unchanged.** The relation reaches exactly the states of E,
  so the exploration metrics and the quality gate rank E.

## 3. Verdicts

The aggregator applies the metamorphic oracle:

| Verdicts | Directory |
| --- | --- |
| every checker `pass`, or every checker `fail` | `03aggregator-pass` |
| any `counterexample`, or checkers disagree | `03aggregator-fail` |

A `fail` is not a metamorphic violation. A checker's evaluation error on a partial
term, such as `CHOOSE` without a witness, fails both sides alike. The quality gate,
known-defect signatures and the mutator work as in a conformance corpus.

## 4. Triage

A counterexample has two possible causes:

- **An unsound rule.** The rule is not an equivalence in TLA<sup>+</sup>, or it breaks
  the rule contract (section 5).
- **A checker defect in evaluating one side.**

To tell them apart:

1. **List the rules and the orientation.** `fuzztla print` replays them. A violation of
   `Inv` at `step = 0` is an initial state of E that `InitC` rejects. A violation of
   `Step` is a transition of E that `ActionC` rejects.
2. **Reduce the stack of rewrites.** Clear rewrite markers until the violation
   disappears. The last rule cleared is the culprit or part of it.
3. **Evaluate both sides of that rule on the counterexample's last state or
   transition** under TLC and, where possible, Apalache.
   - If a checker disagrees with the expected value, reduce the input to an MWE and
     file a finding.
   - If the rule is at fault, fix the rule. Rule changes reinterpret stored entries,
     so initialize a new corpus.

Findings cite the FuzzTLA commit, which versions the rules, besides the TLC and
Apalache commits.

## 5. Writing rules

A rule is an operator of the rule module. Its body relates a pattern A to its
replacement B:

```tla
------------------------------ MODULE Rewrites ------------------------------
EXTENDS Integers, FiniteSets

PlusZero(x) == x = x + 0
AddSub(x, y) == x = x + (y - y)
DoubleNeg(P) == P = ~~P
UnionSelf(S) == S = S \union S
UnchangedPrime(x) == (UNCHANGED x) = (x' = x)
ForallNotExists(S, P(_)) == (\A e \in S : P(e)) = ~(\E e \in S : ~P(e))
AlwaysTwice(F) == []F <=> [][]F
=============================================================================
```

- **Every top-level definition is a rule.** Put helpers in a module that the rule
  module `EXTENDS`. B may apply them, A may not.
- **Rewriting goes from A to B only.** Add a second rule for the other direction.
- **Use `<=>` for temporal formulas.** SANY rejects `=` between them.
- **Parenthesize.** A pattern matches what SANY parses. `x + y - y` is
  `x + (y - y)`, because binary `-` binds tighter than `+`, and `UNCHANGED x = e` is a
  precedence error.

Each parameter is one of three kinds:

| Kind | Where it occurs | At rewrite time | Example |
| --- | --- | --- | --- |
| matched | in A | bound to a subterm of the node | `x` in `PlusZero` |
| fresh | only in B | drawn by the generator at its type, in the node's scope | `y` in `AddSub` |
| higher-order | in A, applied to variables that A binds | bound to `LAMBDA e : body` | `P(_)` in `ForallNotExists` |

Snowcat infers each rule's signature, and a rule applies only at a node of a matching
type. `UnionSelf` applies to sets of any element type.

The loader rejects a rule that:
- applies a prime, `UNCHANGED`, `ENABLED` or another action-level operator in B when A
  applies none, or any temporal operator;
- names anything other than its parameters, bound variables, built-in operators and
  helpers.

The matcher skips a rule where it would move a primed equation out of an assignment
position, or reorder primed conjuncts. For example, `DoubleNeg` does not apply to
`x' = 1`: under `~~`, TLC no longer treats the equation as an assignment.

**Validity is the author's job.** Check each new rule with TLC before using it:
- constant- and state-level rules by `ASSUME` over small domains, in a module that
  `EXTENDS` the rule module:

  ```tla
  ASSUME \A x, y \in -3..3 : AddSub(x, y)
  ASSUME \A S \in SUBSET SUBSET {1, 2} : UnionSelf(S)
  ASSUME \A S \in SUBSET (-3..3), k \in -3..3 : ForallNotExists(S, LAMBDA e : e # k)
  ```

- action rules as `PROPERTY [][UnchangedPrime(v)]_v` of a small specification with a
  variable `v`;
- temporal rules as the two implications `([]F => [][]F) /\ ([][]F => []F)`, because
  TLC rejects `<=>` between temporal formulas.

## 6. Limitations

- **One direction per entry.** An entry checks only that E's initial states and
  transitions satisfy C. A rewrite that only adds initial states or successors is
  detected only by entries that explore M2. In ADR 0016's probe, one direction
  detected 108 of 301 perturbations, and both directions together detected 111.
- **Temporal properties are not rewritten yet.** The first implementation leaves a
  module's property and fairness out of the relation and loads no temporal rule.
  Once they are added, TLC will reject some temporal relations: formulas that negate
  `[][A]_v` fail with "Temporal formulas containing actions must be of forms <>[]A or
  []<>A".
- **TLC-only corpora.** A corpus with `checkers = ["tlc"]` compares no checkers, so
  its entries are not conformance tests.
