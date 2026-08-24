# Apalache does not support `Seq(S)`

Observed share: 1.92% of aggregator deviations; TLC passed and Apalache failed.

`Seq(S)` is an infinite set of unbounded sequences. Apalache rejects the
operator even when TLC can avoid enumerating it in the surrounding expression.
This is a documented Apalache limitation confirmed by the corpus.

## Representative MWE

```tla
---- MODULE SequenceSetUnsupported ----
EXTENDS Sequences
VARIABLE
\* @type: Seq(Int);
seq
Init == seq = <<>>
Next == UNCHANGED seq
Inv == TRUE \/ seq \in Seq({1})
====
```

TLC short-circuits the true disjunct. Apalache rejects `Seq({1})` during
rewriting. Bounded models can use `Apalache!Gen` with an explicit length bound.
