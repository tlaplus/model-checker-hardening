# Guarded expansion of a function set

Observed share: 0.05% of Apalache crash outcomes.

Apalache refuses an analysis path that would expand a set of functions and
terminates with `Trying to expand a set of functions. This will blow up the
solver.` TLC can enumerate a small finite representative. This is an explicit
Apalache resource guard, not an unhandled implementation exception.

## Representative MWE

```tla
---- MODULE FunctionSetExpansionGuard ----
EXTENDS Integers, Sequences, Apalache
VARIABLE
\* @type: Seq((Int -> Int));
functions

\* @type: ((Seq((Int -> Int)), (Int -> Int)) => Seq((Int -> Int)));
Add(acc, f) == Append(acc, f)

Init == functions = ApaFoldSet(Add, <<>>, [{1} -> {0, 1}])
Next == UNCHANGED functions
Inv == TRUE
====
```

TLC enumerates the two functions while evaluating the fold. Apalache reaches
its expansion guard on the fold domain. The guard should remain a documented
capability boundary unless the encoding changes.
