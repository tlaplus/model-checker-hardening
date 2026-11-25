# Counterexample replay

!!! warning "Proposed"
    This page describes a proposed feature that is not implemented yet.
    [ADR 0034](../decisions/0034-counterexample-replay.md) records the design.

When two checkers both report a counterexample, the aggregator counts that as agreement,
even if one of the traces is not a behavior of the specification. Counterexample replay
feeds every recorded trace back into the corpus as an input of its own. Every checker then
has to confirm that the trace is a behavior, including the checker that reported it.

## 1. Enabling replay

Replay is off by default, because it adds entries to every generation. To enable it, set
the following in `config.toml`:

```toml
[replay]
enabled = true
```

The following are configuration errors:
- replay in an `mt` corpus;
- replay together with a generic checker whose `errors = "nonzero"`.

## 2. What is replayed

With replay enabled, TLC, TLC-n and Apalache record the trace of every counterexample in
the entry, under `stages.<checker>.trace`. After a generation settles, each recorded trace
of its aggregated entries becomes a `trace` entry of the next generation. Equal traces from
different checkers become one entry. These entries come on top of `generation_size`.
After the last generation, one generation of replays alone follows.

A `trace` entry checks a module that allows exactly the recorded behavior. Its invariant
fails in the last state exactly when that state is what the checker claimed:

| The checker reported | The last state must |
| --- | --- |
| a violation of `Inv` | violate `Inv` |
| a lasso, for a temporal property | equal the state the loop returns to |
| another finite violation | only be reachable |

Fairness and the property are not checked again. Whether the lasso violates the property
is already compared between the checkers of the parent entry.

A generic checker checks every `trace` entry, but its own counterexamples are not
recorded.

## 3. Results

Every checker must report a counterexample of exactly the replayed length. Otherwise the
`trace` entry moves to `03aggregator-fail`:

- **A `pass`** means that the trace is not a behavior, or that its last state does not
  satisfy the claim. Either the checker that reported the trace or the checker that
  passed is wrong.
- **A counterexample of another length,** or a disagreement between checkers, means the
  same.

`fuzztla print` shows the parent's module, the trace and the replay module. The parent's
digest is in `gen.parent`. A `trace` entry is never a parent for mutation, and the quality
gate drops it.

## 4. Cost

Each replay is one path of at most `max_steps` transitions, so each check is short. But
every configured checker checks every replay, and a generation can add up to one replay
per recorded counterexample. A corpus in which most entries end in a counterexample nearly
doubles its checker work.
