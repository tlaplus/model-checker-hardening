# Other model checkers

!!! note
    [ADR 0033](../decisions/0033-generic-checker.md) records the design and the probe of
    Manyan it is based on.

A corpus can run one additional model checker that claims compatibility with TLC or
Apalache. FuzzTLA compares its verdicts with those of the other checkers in the corpus.
The checker is called `generic` in the checker list and the corpus, and it is described
by the `[generic]` table of `config.toml`.

## 1. Building a checker

The repository builds two checkers at pinned revisions:

| checker | target | revision variable | needs |
| --- | --- | --- | --- |
| [Manyan](https://github.com/soasme/manyan), in C | `make manyan-build` | `MANYAN_REV` | a C11 compiler |
| [tla-rs](https://github.com/fabracht/tla-rs), in Rust | `make tla-rs-build` | `TLA_RS_REV` | cargo |

Each binary is staged as `target/checkers/<checker>-<rev12>`. To build another
revision, pass the variable, as in `make manyan-build MANYAN_REV=<sha-or-tag>`.

## 2. Initializing a corpus

A file with a `[generic]` table describes the checker. The repository ships two, for
Manyan:

| file | compares |
| --- | --- |
| `generic/manyan.toml` | Manyan with TLC |
| `generic/manyan-symbolic.toml` | Manyan's bounded mode with Apalache |
| `generic/tla-rs.toml` | tla-rs with TLC, by exit status alone |

Name one at `init`:

```sh
./bin/fuzztla init --how=pbt --corpus corpus-manyan --generic generic/manyan.toml
```

`init` copies the executable into the corpus's `tools/` directory, like the checker
JARs, and writes the table into `config.toml` with the copied path. Without
`--generic`, `config.toml` shows the table commented out. `generic/manyan.toml` reads:

```toml
[generic]
name = "Manyan"
compatibility = "tlc"
executable = "../target/checkers/manyan-a9ca4f38028f"
arguments = ["-workers", "1", "-deadlock", "-config", "{config}", "{module}"]
```

- **The first four keys go together.** A table that sets only some of them is an error.
  `errors`, `fairness` and `apalache` are optional.
- **`compatibility`** is `tlc` or `apalache`. It decides which configuration file
  FuzzTLA writes and how exit statuses become verdicts.
- **`executable`** is relative to the file that names it. A corpus with a native tool
  runs only on the platform that built it. `init` refuses an executable that is
  missing or not executable.
- **`arguments`** is the command line, one array element per argument. Have the tool
  use one worker: FuzzTLA runs several invocations in parallel itself.
- **`errors`** is `"exact"` when omitted: the exit statuses mean what they mean to the
  compatibility's checker. `"nonzero"` is for a tool with exit statuses of its own, such
  as tla-rs, which exits 0 or 1. With it, FuzzTLA compares only whether the tool exits 0:
  any error matches a counterexample or a failure of another checker. A fatal signal,
  such as status 139 for SIGSEGV, is still a crash. `gbt` corpora reject `"nonzero"`,
  because their oracle compares counterexamples
  ([ADR 0033](../decisions/0033-generic-checker.md) §7).
- **`fairness`** and **`apalache`** are `true` when omitted. Set `fairness = false` for
  a tool that does not check `WF_v(A)` and `SF_v(A)`. Set `apalache = false` for a tool
  that reads neither the `Apalache` nor the `Variants` module. While `generic` runs,
  every checker of the corpus then gets inputs without these features:
  - `fairness = false` sets `max_fairness` to 0 and adds `fairness` to
    `[generator] ignore`;
  - `apalache = false` adds `fold` and `variant` to `ignore`, and the generic checker's
    module does not extend `Apalache` and `Variants`
    ([ADR 0033](../decisions/0033-generic-checker.md) §8).

Then add `generic` to the checker list, and list Manyan's known-defect signatures
([Known-defect signatures](known-defect-signatures.md#73-manyan-database)):

```toml
[workflow]
checkers = ["tlc", "generic"]

[workflow.inputs]
known_defects = ["../signatures/all-defects.toml", "../signatures/manyan-defects.toml"]

[workflow.generic]
timeout_sec = 30
workers = 4          # concurrent invocations
```

Listing `generic` without a `[generic]` table is an error, as is listing it in a corpus
that links a library through `INSTANCE`.

FuzzTLA writes the module, the configuration file, and the `Apalache` and `Variants`
modules that every generated module extends into a fresh working directory for each
invocation, and removes the directory afterwards.

!!! warning
    In the bounded mode of `generic/manyan-symbolic.toml`, Manyan rejects most generated
    modules with a type error. Its type checker infers types from TLA<sup>+</sup>
    source, while Apalache receives typed JSON IR. The rendered source annotates only
    variables and constants ([ADR 0033](../decisions/0033-generic-checker.md#probe),
    P4). A tool that reads Apalache's JSON IR can use `{ir}` instead of `{module}`.

## 3. Placeholders

| placeholder | value |
| --- | --- |
| `{dir}` | the invocation's working directory |
| `{module}` | `{dir}/FuzzInput.tla` |
| `{module_name}` | `FuzzInput` |
| `{config}` | `{dir}/FuzzInput.cfg` |
| `{init}`, `{next}` | the initial predicate and the next-state action |
| `{inv}` | the invariants to check |
| `{temporal}` | the temporal property to check, if any |
| `{length}` | the number of steps a bounded checker unrolls |
| `{ir}` | `{dir}/FuzzInput.json`, the module as Apalache's JSON IR |

An unknown placeholder is a configuration error.

## 4. Results

The checker's results are stored under `02gen-*` in the corpus, and its verdicts take
part in every oracle. For a `tlc`-compatible checker that passes, FuzzTLA also reads
TLC's statistics line from its output,
`N states generated, M distinct states found, 0 states left on queue.`, and records
`phase = complete` and `distinctStates = M`. The
[state-count agreement](exploration-metrics.md) then compares that count with TLC's: an
entry whose completed searches reach different numbers of states fails, and the triager
labels it `STATE_COUNT`. A tool that does not print the line records no count, and an
`apalache`-compatible tool records none. FuzzTLA records no trace lengths, so the graph
oracle checks only the verdict.

A timeout is a crash, as it is for the other checkers. With `errors = "nonzero"`, every
error goes to `02gen-fail` with failure code 1 (`error`), and `02gen-counterexample` stays
empty.

Expect Manyan to differ from TLC on about one pbt entry in five on parsing alone, for
example on `[A]_FALSE` ([ADR 0033](../decisions/0033-generic-checker.md#probe), P2).
Known-defect signatures filter these out of the aggregator's failures.

tla-rs `v0.24.11` checks a subset of TLA<sup>+</sup>. `generic/tla-rs.toml` sets
`apalache = false`, because tla-rs cannot parse the bundled `Apalache.tla`. tla-rs still
rejects two constructs of generated modules:
- labels, which `[generator] ignore` can exclude with `label`;
- `UNCHANGED (<<x>>)` and `[]([Next]_(<<x>>))`, the printer's forms, although it parses
  `UNCHANGED <<x>>` and `[][Next]_<<x>>`.

Until the second is resolved, tla-rs reports an error on almost every input.

Findings cite the tool's revision, together with the FuzzTLA, TLC and Apalache versions.
