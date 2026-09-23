# 0015: Diff-linked libraries for recursion testing

**Authors:** Igor Konnov and Claude

**Status:** Implemented

**Date:** 2026-09-23

## Context

TLC evaluates `RECURSIVE` operators, `LET RECURSIVE` definitions, mutual recursion and
recursive function definitions `f[x \in S] == …`. Apalache has rejected all of them since
0.23.1 and expects folds (`ApaFoldSet`, `ApaFoldSeqLeft`) or closed forms instead. fuzztla
does not exercise TLC's recursion:

- the generator emits no recursive form
  ([ir-generators.md §5][generators]);
- the library importer rejects recursive declarations, recursive dependencies and
  recursive functions ([ir-generators.md §11][generators]), because every checker input
  would reach Apalache.

[ADR 0014][adr-0014] already lets the two sides of a check read different definitions
behind one mangled name. With `link = "instance"`:

- the parser and TLC call `<Module>` through a named `INSTANCE`;
- Apalache evaluates the definitions it imports for `EXTENDS <Module>`.

Both sides still name the same module. A user who needs recursion in TLC writes one
module for TLC and a second, equivalent module for Apalache. Whether TLC evaluates the
first as Apalache evaluates the second is a differential question that the aggregator
([fuzzing-workflows.md §1.3][workflows]) can answer.

### Probe

Probed with fuzztla `f08b942`, tla2tools 1.8.0-SNAPSHOT (tlaplus/tlaplus commit
`142d0ba`) and Apalache 0.62.2.

- SANY and TLC parse and check a module containing `I == INSTANCE ProbeTLC` and aliases
  `A(p) == I!Op(p)`. In `ProbeTLC`, the operators use:
  - top-level `RECURSIVE`;
  - `LET RECURSIVE`;
  - a recursive function over `SUBSET S`;
  - a higher-order `RECURSIVE` fold;
  - mutually recursive `SeqEven` and `SeqOdd`;
  - a recursive function over `0..Len(s)`.

  All values were as expected.
- `apalache typecheck --infer-poly=true` accepts the fold-based counterpart
  `ProbeApalache` as root module. Its six exports pass library preparation with inline
  linkage.
- TLC checks a module that instantiates both modules and asserts pointwise equality in
  `ASSUME` over `SUBSET (-2..3)` and all sequences of length at most 3 over `-2..3`. When
  one side is deliberately changed, TLC reports `Assumption … is false`.

## Decision

1. A `custom_operators` entry takes a third linkage, `link = "diff"`, with a required key
   `tlc_module`:

   ```toml
   { module = "RecursionApalache", link = "diff", tlc_module = "RecursionTLC",
     operators = ["SetSum", "SetSumLetRec", "SeqReverse"] }
   ```

   `tlc_module` is an identifier distinct from `module`. It is rejected with any other
   linkage.
2. `module` is the reference side. Apalache typechecks `<module>.tla` as root, as for
   inline linkage and without a wrapper, since the module is written for Apalache. The
   module therefore supplies:
   - the signatures;
   - the mangled names `Custom<hex(module)>N<hex(operator)>`;
   - the self-contained IR that Apalache reads, and that richness scoring and
     known-defect matching use.

   Library validation is unchanged: `module` and its closure are non-recursive and
   first-order.
3. fuzztla never imports `tlc_module`. The TLA+ source of the parser and TLC declares
   `Custom<hex(module)>I == INSTANCE <tlc_module>` and one alias per used export, as for
   instance linkage. `tlc_module` may therefore use any construct that SANY and TLC
   accept.
4. As with instance linkage, `generator.classpath` precedes the class path of the parser
   and TLC workers. `tlc_module` must be in the source snapshot.
5. Library preparation parses a probe module in one parser worker with that class path.
   The probe contains only the instance and alias declarations of every module that
   aliases the source, whether instance- or diff-linked. A missing module, a missing
   operator or an arity mismatch stops preparation, instead of failing every input that
   calls it.
6. The replay manifest records `operator <module>!<Op> diff <tlc_module>` and pins the
   classpath files, as for instance linkage. Inline and instance lines are unchanged.
   The source snapshot already digests `<tlc_module>.tla`.
7. `LibraryLinkage` carries the per-linkage facts that callers used to test with
   `== INSTANCE`:
   - `aliasesSource()`: true for instance and diff;
   - `typechecksThroughWrapper()`: true for instance only;
   - `namesTlcModule()`: true for diff only.

   The module a source alias instantiates travels with the configuration and with the
   export as `ModuleLink(linkage, sourceModule)`.
8. `libraries/recursion.toml` and `libraries/recursion/{RecursionApalache,RecursionTLC}.tla`
   ship a diff-linked library of 39 recursive operators
   ([manual](../manual/recursive-operators.md)). `RecursionLibraryIntegrationTest`:
   - prepares the library;
   - checks that `src/test/resources/recursion/RecursionPairs.tla` covers every operator;
   - runs TLC on that module, which asserts pairwise equality over small domains, like
     the probe.

### Authoring rules

A pair is admissible only if a disagreement can only mean a defect:

1. **Same interface.** Both modules export each selected name with the same arity. The
   TLC side is constant-level.
2. **Equal and total.** Both sides agree on every argument of the reference signature.
   Edge cases are explicit on both sides: no `Head(<<>>)`, no `CHOOSE` without a witness,
   and defined results for negative integers.
3. **Order-insensitive.** A `CHOOSE`-driven recursion over a set combines elements with a
   commutative and associative operation. Otherwise it reproduces
   [order-sensitive set folds][fold-order].
4. **Bounded cost.**
   - Recursion depth is bounded by the size of a generated collection
     ([ADR 0011][adr-0011]).
   - Integer-driven recursion clamps its argument to a small constant on both sides.
   - The Apalache side avoids `a..b` with nonconstant bounds
     ([conformance][nonconstant-range]).
5. **One name per mechanism.** One Apalache definition may back several TLC variants,
   each exported under its own name. The Apalache module defines each variant as a
   one-line alias. Known-defect signatures and triage then separate the mechanisms:
   - top-level `RECURSIVE`;
   - `LET RECURSIVE`;
   - recursive functions;
   - mutual recursion;
   - higher-order recursive helpers.

## Alternatives considered

- **An optional instance target on `link = "instance"`.** This is one linkage fewer.
  However, the pairing would be implicit, and a diff-linked module would still be
  typechecked through a wrapper whose purpose is Apalache's rewiring.
- **Generating `RECURSIVE` operators.** The generated module is also Apalache's input,
  so Apalache would reject every such input. [ir-generators.md §10][generators] rule 3
  would then call for a known-defect signature. That keeps the input, but TLC's value is
  never compared with anything, so TLC's recursion stays untested.
- **Unrolling recursion for Apalache.** This changes the evaluated definition, like a
  fold, but with a depth bound that must match the generated collections. A hand-written
  fold is exact.

## Consequences

- A disagreement on a diff-linked call has three possible causes:
  - the pair is not equivalent, which is a library defect: fix it and initialize a new
    corpus;
  - TLC evaluates recursion wrongly;
  - Apalache evaluates the fold or closed form wrongly.

  Triage reproduces the counterexample's arguments in the pairwise self-test, which
  compares both definitions under TLC:
  - if TLC finds them equal, the third cause remains, or the context of the call;
  - if TLC finds them unequal, the first two remain, and the expected value derived
    from the definitions decides between them.
- Findings cite the fuzztla commit, which versions the library, besides the TLC and
  Apalache commits.
- Inputs of diff-linked corpora are not self-contained. Replaying a TLC input needs
  `<corpus>/tla/recursion/` on TLC's class path.
- Each TLC variant takes one selection slot. The library therefore weights a semantic
  operator by its number of variants.
- Deep TLC recursion can overflow the stack
  ([tlc-performance-001][tlc-performance-001]). Collection bounds keep recursion depth
  small. Such crashes stay in `02tlc-crash` and are not aggregated.
- Without diff-linked modules, every output is byte-identical to the previous
  implementation. The source probe adds one parser worker to preparation for instance-
  and diff-linked libraries.
- Snowcat in Apalache 0.62.2 generalizes the type of a parameter that only `LET`
  definitions constrain: `F(n) == LET k == n + 1 IN k` gets `(a) => Int`
  ([apalache-typechecker-001][typechecker-001]). Every library is affected. The recursion library annotates the affected operators, and a test pins
  which of its exports are polymorphic. Before the annotations, a 10-minute campaign had
  13 Apalache crashes: `IF_THEN_ELSE cannot be applied to arguments of types (Bool, Int,
  …)`, on records passed to `IntDigitSum`.
- In two 10-minute campaigns, every disagreement matched an existing conformance or
  findings class. About 9% of the entries that reached Apalache timed out at 30 s; the
  timeouts track the number of library calls per input.
- The source probe also stops instance-linked libraries whose module the parser cannot
  resolve on `generator.classpath`. For example, instance-linking `SequencesExt` without
  the Community Modules on the classpath used to pass preparation, and then every input
  calling it failed in the parser.

[adr-0011]: 0011-collection-base-size.md
[adr-0014]: 0014-per-checker-library-linking.md
[fold-order]: ../../conformance/order-sensitive-set-fold.md
[generators]: ../architecture/ir-generators.md
[nonconstant-range]: ../../conformance/nonconstant-integer-range.md
[tlc-performance-001]: ../../findings/tlc-performance/tlc-performance-001.md
[typechecker-001]: ../../findings/apalache-typechecker/apalache-typechecker-001.md
[workflows]: ../architecture/fuzzing-workflows.md
