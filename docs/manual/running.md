# Running campaigns

```sh
fuzztla run --how=TECHNIQUE [--corpus=DIR] [--seed=SEED] [--max-cpus=N]
```

| Option | Meaning |
|---|---|
| `--how=pbt` | Property-based testing: generate random modules, compare the checkers, and mutate good inputs ([Generations and mutation](mutation.md)). |
| `--how=mt` | Metamorphic testing: check one checker against equivalent rewrites of its input ([Metamorphic testing](metamorphic-testing.md)). |
| `--how=gbt` | Graph-based testing, proposed: check each checker against the verdict that a generated control graph predicts ([Graph-based testing](graph-based-testing.md)). |
| `--corpus=DIR` | The corpus directory. Default: `corpus`. |
| `--seed=SEED` | A nonnegative seed. A random seed is used when it is omitted. The seed is printed first in either case. |
| `--max-cpus=N` | Maximum number of jobs that run at the same time. Default: all processors. |

A run admits inputs until the corpus holds `workflow.max_entries` entries, then
finishes the work in flight and prints a summary. See
[Getting started](getting-started.md#your-first-campaign) for an example.

## Choosing a campaign

| Goal | Setup |
|---|---|
| Compare TLC and Apalache on the standard operators | `init`, then `run --how=pbt`. This is the default. |
| Compare them on the TLA<sup>+</sup> Community Modules | [Community Modules](community-modules.md) |
| Compare TLC's recursion with Apalache's folds | [Recursive operators](recursive-operators.md) |
| Generate calls to your own operators | [Custom operators](custom-operators.md) |
| Test one checker against itself | [Metamorphic testing](metamorphic-testing.md) |
| Check verdicts against a predicted outcome (proposed) | [Graph-based testing](graph-based-testing.md) |
| Compare TLC with one worker against TLC with several | [Comparing TLC with itself](#comparing-tlc-with-itself) |

## Stopping and resuming

Press ++ctrl+c++ to stop. The run stops its workers and saves the corpus. The next
`run` on the same corpus validates it, finishes moves that were interrupted, and
continues where the last run stopped. The counters in the summary add up over all
runs of a corpus.

Only one process uses a corpus at a time. `run` and `export-db` take a lock on it.

## CPU use

`--max-cpus` limits the jobs that run at the same time across all stages. A TLC job
uses `workflow.tlc.workers` CPUs, so with `--max-cpus=8` and `workers = 2`, at most
four TLC processes run. Apalache runs `workflow.apalache.workers` long-lived
processes, one CPU each. Checking has priority over parsing, and parsing has
priority over generation, so a slow checker does not pile up unchecked inputs.

## The live display

On an interactive terminal, `run` redraws a flow diagram of the stages once per
second. It shows the queue and verdict counters of each stage. A changed value is bold
for one second and marked `↑` or `↓`. After the workers stop, `FINALIZING` means that
the corpus is being validated. Set `NO_COLOR` to turn off colors. When output is
redirected to a file, only the final summary is printed.

## Reproducibility

Every entry can be replayed exactly. Its input bytes and the corpus's generator
settings determine the module, and `fuzztla print` regenerates it
([Inspecting a corpus](inspecting.md)).

A whole run is reproducible only approximately. Each generator worker has its own
random stream derived from `--seed`, so the same seed, configuration, starting corpus
and `--max-cpus` produce the same candidate inputs. Which candidates become entries,
however, depends on timing between workers.

## Comparing TLC with itself

With `checkers = ["tlc", "tlcn"]` in `[workflow]`, every input is checked by TLC with
one worker and by TLC with `workflow.tlcn.workers` workers. The two must report the
same verdict, and when both searches complete, the same number of distinct states.
TLC-n can be added to a TLC and Apalache campaign as a third checker.

The triager reports a disagreement in which one TLC run finds a counterexample and the
other an evaluation error as `FIRST_ERROR`. Multi-threaded TLC may reach either error
first, so this is not a defect.

!!! warning "Older corpora"
    A corpus created before TLC-n existed lacks its directories. Create them with
    `mkdir corpus/02tlcn-{inputs,pass,counterexample,fail,crash}`.

## When a run stops with an error

| Message | What to do |
|---|---|
| `could not generate corpus entry … for richness cohort …` | The generator cannot make inputs rich enough for that cohort. Lower `richness_threshold_base` or `richness_cohorts` in `[pbt]`, or ignore fewer categories. |
| `parser worker did not connect within …` | The machine is overloaded, often by another run. Lower `--max-cpus`. |
| A generator crash with a path under `.work/generator-crash/` | A FuzzTLA bug. The `.cbor` and `.stacktrace` files there reproduce it, so please report them. |
