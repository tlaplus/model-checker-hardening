# Running campaigns

```sh
fuzztla init --how=TECHNIQUE [--corpus=DIR] [--library=FILE | --base-corpus=DIR]
fuzztla run [--corpus=DIR] [--continue] [--max-cpus=N]
```

`init` chooses the technique once and writes it into the corpus's `config.toml` as
`[workflow] technique`. Every `run` follows it
([ADR 0025](../decisions/0025-self-contained-corpora.md)). `init` also draws the
corpus's random seeds and writes them as `[seeds]`, so a run of a fresh corpus is
determined by its configuration
([ADR 0026](../decisions/0026-reproducible-runs.md)). To choose the seeds, edit
`[seeds]` before the first run.

| `init --how` | Technique |
|---|---|
| `pbt` | Property-based testing: generate random modules, compare the checkers, and mutate good inputs ([Generations and mutation](mutation.md)). |
| `mt` | Metamorphic testing: check one checker against equivalent rewrites of its input ([Metamorphic testing](metamorphic-testing.md)). `init` also sets the shipped rewrite rules and, with Apalache, `metamorphic-defects.toml`. With `--base-corpus=DIR`, the corpus starts from a previous `pbt` or `gbt` run and adopts its selected entries. |
| `gbt` | Graph-based testing: check each checker against the verdict that a generated control graph predicts ([Graph-based testing](graph-based-testing.md)). `init` also sets `kind = "graph"`. |

| `run` option | Meaning |
|---|---|
| `--corpus=DIR` | The corpus directory. Default: `corpus`. |
| `--continue` | Extend a corpus that an earlier run has written. Without it, `run` refuses such a corpus. See [Stopping and resuming](#stopping-and-resuming). |
| `--max-cpus=N` | Maximum number of jobs that run at the same time. Default: all processors. |

A run admits inputs until the corpus holds `workflow.max_entries` entries, then
finishes the work in flight and prints a summary. See
[Getting started](getting-started.md#your-first-campaign) for an example.

## Choosing a campaign

| Goal | Setup |
|---|---|
| Compare TLC and Apalache on the standard operators | `init --how=pbt`, then `run`. |
| Compare them on the TLA<sup>+</sup> Community Modules | [Community Modules](community-modules.md) |
| Compare TLC's recursion with Apalache's folds | [Recursive operators](recursive-operators.md) |
| Generate calls to your own operators | [Custom operators](custom-operators.md) |
| Test one checker against itself | [Metamorphic testing](metamorphic-testing.md) |
| Check verdicts against a predicted outcome | [Graph-based testing](graph-based-testing.md) |
| Compare TLC with one worker against TLC with several | [Comparing TLC with itself](#comparing-tlc-with-itself) |

## Stopping and resuming

Press ++ctrl+c++ to stop. The run stops its workers and saves the corpus. A later
`run --continue` on the same corpus validates it, finishes moves that were interrupted,
and continues where the last run stopped. The counters in the summary add up over all
runs of a corpus. Raising `max_entries` to grow a finished corpus also needs
`--continue`.

`run` without `--continue` accepts only a fresh corpus: one without entries and without
`.workflow-stats.cbor`. On any other corpus it stops and names `--continue`. A continued
run depends on where the earlier runs stopped, so `run --continue` prints a warning and
appends its start time to `.not-reproducible` in the corpus. The
[campaign report](campaign-report.md) shows how many runs continued the corpus.

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
random stream derived from `[seeds] generator`, and every TLC check uses
`[seeds] tlc_fp` and `tlc_seed`, so two fresh corpora with the same configuration and
`--max-cpus` draw the same candidate inputs and check them alike. Which candidates
become entries, however, depends on timing between workers. A corpus with a
`.not-reproducible` file was continued, so its configuration does not determine it.

## Comparing TLC with itself

With `checkers = ["tlc", "tlcn"]` in `[workflow]`, every input is checked by TLC with
one worker and by TLC with `workflow.tlcn.workers` workers. The two must report the
same verdict, and when both searches complete, the same number of distinct states.
TLC-n can be added to a TLC and Apalache campaign as a third checker.

The triager reports a disagreement in which one TLC run finds a counterexample and the
other an evaluation error as `FIRST_ERROR`. Multi-threaded TLC may reach either error
first, so this is not a defect.

The triager reports two completed searches with different numbers of distinct states
as `STATE_COUNT`. Rerun such an entry many times with several workers: in corpus68,
one was [tlc-020](../../findings/TLC/tlc-020.md), a race that corrupts values shared
by TLC's workers.

!!! warning "Older corpora"
    A corpus created before TLC-n existed lacks its directories. Create them with
    `mkdir corpus/02tlcn-{inputs,pass,counterexample,fail,crash}`.

## When a run stops with an error

| Message | What to do |
|---|---|
| `could not generate corpus entry … for richness cohort …` | The generator cannot make inputs rich enough for that cohort. Lower `richness_threshold_base` or `richness_cohorts` in `[pbt]`, or ignore fewer categories. |
| `parser worker did not connect within …` | The machine is overloaded, often by another run. Lower `--max-cpus`. |
| A generator crash with a path under `.work/generator-crash/` | A FuzzTLA bug. The `.cbor` and `.stacktrace` files there reproduce it, so please report them. |
