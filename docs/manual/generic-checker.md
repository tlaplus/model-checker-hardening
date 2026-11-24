# Other model checkers

!!! note
    Proposed in [ADR 0033](../decisions/0033-generic-checker.md). This is not
    implemented yet.

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

## 2. Configuring the checker

`init` writes a commented `[generic]` table that is pre-filled for Manyan. Uncomment
it and add `generic` to the checker list:

```toml
[workflow]
checkers = ["tlc", "generic"]

[workflow.generic]
timeout_sec = 30
workers = 4          # concurrent invocations

[generic]
name = "Manyan"
compatibility = "tlc"
executable = "../target/checkers/manyan-a9ca4f38028f"
arguments = ["-workers", "1", "-deadlock", "-config", "{config}", "{module}"]
```

- **`compatibility`** is `tlc` or `apalache`. It decides which configuration file
  FuzzTLA writes and how exit statuses become verdicts.
- **`executable`** is copied into the corpus's `tools/` directory, like the checker
  JARs. A corpus with a native tool runs only on the platform that built it.
- **`arguments`** is the command line, one array element per argument. Have the tool
  use one worker: FuzzTLA runs `workers` invocations in parallel itself.

FuzzTLA writes the module, the configuration file, and the `Apalache` and `Variants`
modules that every generated module extends into the invocation's working directory.
A corpus that links a library through `INSTANCE` cannot run the generic checker yet.

To compare Manyan's bounded mode with Apalache, use the configuration below.

!!! warning
    In this mode Manyan rejects most generated modules with a type error. Its type
    checker infers types from TLA<sup>+</sup> source, while Apalache receives typed
    JSON IR. The rendered source annotates only variables and constants
    ([ADR 0033](../decisions/0033-generic-checker.md#probe), P4). A tool that reads
    Apalache's JSON IR can use `{ir}` instead of `{module}`.

```toml
[workflow]
checkers = ["apalache", "generic"]

[generic]
name = "Manyan"
compatibility = "apalache"
executable = "../target/checkers/manyan-a9ca4f38028f"
arguments = ["-symbolic", "-length", "{length}", "-config", "{config}", "{module}"]
```

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
