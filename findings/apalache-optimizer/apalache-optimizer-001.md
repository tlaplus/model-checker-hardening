---
state: open
---

# `ConstSimplifier` leaves an undeclared local operator

## Summary

Apalache's optimization pipeline turns valid scoped IR into an expression that
references an undeclared local operator. The input passes parsing, type
checking, preprocessing, and transition extraction. After `ConstSimplifier`
runs, the entry watchdog of `ExprOptimizer` rejects `LocalOp2$1` as undeclared.

Observed with Apalache 0.62.2, build `f0dec98`, in one `corpus2` input. The
failure reproduces in a fresh Apalache process and is therefore not persistent
worker contamination.

## Reproduction

Use model-checker-hardening revision `2e17c0d`, which preserves the generator
mapping used by the corpus run, and render the recorded typed IR:

```sh
./bin/fuzztla print --apalache-ir --corpus=corpus2 \
  corpus2/02apa-crash/26d3b3e5484b586dd821e5398e682ff3011395c8e9a977fb62ebfa95d5da725e.cbor \
  > FuzzInput.json

java -Xmx1g -jar target/apalache.jar check \
  --init=Init --next=Next --inv=Inv --length=0 --no-deadlock \
  FuzzInput.json
```

Apalache reports:

```text
PASS #9: PreprocessingPass
PASS #10: TransitionFinderPass
PASS #11: OptimizationPass
 > Applying optimizations:
  > ConstSimplifier
  > ExprOptimizer
<unknown>: unexpected expression: undeclared operator LocalOp2$1 [FlatLanguagePred]
Unexpected expressions in the specification (see the error messages)
EXITCODE: ERROR (255)
```

With `--write-intermediate=true`, `10_OutTransitionFinderPass` still contains
the declaration and its scoped use. The relevant shape is:

```tla
LET LocalOp2 == {}
IN {t \in {}:
  ~(t \in SUBSET (UNION (DOMAIN [arg \in LocalOp2 |-> ...])))
}
```

`ExprOptimizer.apply` invokes `LanguageWatchdog(FlatLanguagePred())` before
performing any transformation. Consequently, its diagnostic describes the
output of the preceding `ConstSimplifier` step.

## Expected behavior

`ConstSimplifier` must preserve lexical scope when simplifying an expression
under `LET`. It may remove the declaration together with every dependent
expression, or retain both the declaration and its references, but it must not
emit a dangling operator application.

## Impact

A well-typed module can fail before bounded checking because optimization
produces invalid internal IR. The status-255 result is recorded as a checker
crash and prevents analysis of the specification.
