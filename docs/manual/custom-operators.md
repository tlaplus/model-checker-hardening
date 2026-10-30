# Custom operators

FuzzTLA can generate calls to operators from your own TLA<sup>+</sup> modules,
alongside the standard operators. A library campaign therefore tests both checkers
on the operators that your specifications use. The
[Community Modules](community-modules.md) and [recursive-operator](recursive-operators.md)
campaigns are prepared libraries of this kind.

## Adding a module

Put the module in the corpus, for example in `my-corpus/tla/MyOperators.tla`:

```tla
---- MODULE MyOperators ----
Singleton(value) == {value}
Contains(values, value) == value \in values
====
```

Then list it in the `[generator]` table of `config.toml`:

```toml
classpath = ["./tla"]
custom_operators = [
  { module = "MyOperators", operators = ["Singleton", "Contains"] },
]
weights = { name = 8, enum_set = 16, "MyOperators!Contains" = 8 }
```

- `classpath` lists directories and JARs, in search order, relative to `config.toml`.
  A JAR may hold `Module.tla` at its root or under `tla2sany/StandardModules/`.
- `operators` names the operators that become expression forms. The helpers they use
  are included automatically.
- Each operator has weight one unless `weights` names it as `"Module!Operator"`.

Before generating, FuzzTLA runs Apalache's type checker on each module once to infer
the operators' types. `@type` annotations are respected, and polymorphic operators get
new type instances at each call.

## What an operator may use

An operator must be first-order and state-free. It cannot use:

- operator parameters or operator results;
- `CONSTANT`s, `VARIABLE`s or `ASSUME`;
- recursion, except with `link = "diff"` (below);
- actions or temporal operators.

Its types must be ones the generator can produce. Reals, old-style records, sparse
tuples, and empty records, variants or tuples are not supported. Overriding a standard
module is not supported.

## How the checkers see the operators

By default, every checker receives the operator definitions inlined into the generated
module. A module entry can change this with `link`:

| `link` | Parser and TLC | Apalache |
|---|---|---|
| none | inlined definitions | inlined definitions |
| `"instance"` | the module, through `INSTANCE`, including its Java overrides | inlined definitions, or its own built-in version of the module |
| `"diff"`, with `tlc_module = "M"` | module `M`, which defines the same operators | the entry's own module |

`"instance"` tests TLC's Java overrides against Apalache. `"diff"` compares two
implementations of the same operators. Only the Apalache side is validated, so the
TLC side may be recursive.

## Starting from a library file

A library file is a TOML file with a `[generator]` table that holds `classpath` and
`custom_operators`:

```sh
fuzztla init --corpus my-corpus --library libraries/community-modules.toml
```

`init --library` copies the classpath into `my-corpus/tla/` and writes both keys into
`config.toml`. The repository ships `libraries/community-modules.toml` and
`libraries/recursion.toml`.

## The library is fixed per corpus

`init` copies the library's classpath into `<corpus>/tla/`, and the Apalache JAR that
types it into `<corpus>/tools/`. Every run, and `print --corpus`, uses these copies,
so a newer library or Apalache in the repository does not reach the corpus. Edit the
copies only before the first run: the old entries would no longer replay, and
FuzzTLA does not detect the edit. To change the library, initialize a new corpus.
