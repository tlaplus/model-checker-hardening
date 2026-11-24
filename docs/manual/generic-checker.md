# Other model checkers

!!! note
    [ADR 0033](../decisions/0033-generic-checker.md) records the design and the probe of
    Manyan it is based on.

A corpus can run one additional model checker that claims compatibility with TLC or
Apalache. FuzzTLA compares its verdicts with those of the other checkers in the corpus.
The checker is called `generic` in the checker list and the corpus, and it is described
by the `[generic]` table of `config.toml`.

## 1. Building Manyan

[Manyan](https://github.com/soasme/manyan) is a TLC-compatible checker written in C.
To build it at the pinned revision, run:

```sh
make manyan-build
```

The binary is staged as `target/checkers/manyan-<rev12>`. To build another revision,
pass `MANYAN_REV=<sha-or-tag>`. The build needs a C11 compiler.

## 2. Initializing a corpus

A file with a `[generic]` table describes the checker. The repository ships two, for
Manyan:

| file | compares |
| --- | --- |
| `generic/manyan.toml` | Manyan with TLC |
| `generic/manyan-symbolic.toml` | Manyan's bounded mode with Apalache |

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

- **The four keys go together.** A table that sets only some of them is an error.
- **`compatibility`** is `tlc` or `apalache`. It decides which configuration file
  FuzzTLA writes and how exit statuses become verdicts.
- **`executable`** is relative to the file that names it. A corpus with a native tool
  runs only on the platform that built it. `init` refuses an executable that is
  missing or not executable.
- **`arguments`** is the command line, one array element per argument. Have the tool
  use one worker: FuzzTLA runs several invocations in parallel itself.

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
part in every oracle. FuzzTLA records no state counts or trace lengths for it, so:
- the [state-count agreement](exploration-metrics.md) does not apply to it;
- the graph oracle checks only its verdict.

A timeout is a crash, as it is for the other checkers.

Expect Manyan to differ from TLC on about one pbt entry in five on parsing alone, for
example on `[A]_FALSE` ([ADR 0033](../decisions/0033-generic-checker.md#probe), P2).
Known-defect signatures filter these out of the aggregator's failures.

Findings cite the tool's revision, together with the FuzzTLA, TLC and Apalache versions.
