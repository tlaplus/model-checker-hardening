# Architecture atlas

> **Status:** Implemented.
> [ADR 0022](../decisions/0022-architecture-atlas.md) records the design.

The architecture atlas is a generated map of the FuzzTLA code, with three zoom
levels: modules and the packages in them, with their dependencies; the key types
of each package; and the relations of one type. Annotations in the code decide what the atlas shows. This
page describes how to build and read the atlas and how to annotate the code.

## 1. Building the atlas

```sh
make architecture
```

The target compiles the main and test sources and runs the atlas doclet over
`src/main/java`. It writes into `target/architecture`:

| File | Content |
|---|---|
| `index.html` | the interactive explorer; open it in a browser |
| `README.md` | the module graph, in Mermaid |
| `modules/<module>.md` | one module's package graph, and each package's key types and all its types |
| `types/<qualified name>.md` | one type and its relations |
| `model.json` | the extracted model that both views draw |

Source links point to the commit that `HEAD` names. A build from a working tree
with uncommitted changes links to code that differs from what it shows.

The `architecture` workflow publishes the atlas of every push to `main` on the
`gh-pages` branch: <https://tlaplus.github.io/model-checker-hardening/> serves the
explorer, and GitHub's view of the branch renders the Markdown pages. The site
needs GitHub Pages enabled once, with `gh-pages` as its source branch, in the
repository settings.

## 2. Reading the views

**Modules.** One node per top-level package. An arrow `A → B` means that code in
`A` refers to code in `B`. An arrow that a longer path implies, such as
`cli → corpus` next to `cli → workflow → corpus`, is not drawn; the table under
the graph lists every dependency and each module's summary, the first sentence of
its `package-info.java`. A red arrow is a dependency that `ArchModule` does not
allow, and `mvn verify` fails on it.

**Module.** A module with several packages starts with their graph, reduced as
the module graph is; an arrow counts the relations other than `depends`. Then,
per package, a class diagram of its key types, each with its «role», and a table
of every type with its role and summary.

**Type.** One type and the types it relates to:

| Arrow | Meaning |
|---|---|
| `EXTENDS`, `IMPLEMENTS` | a supertype |
| `HAS` | a field or record component of that type, possibly inside `Optional`, a collection, or a map |
| `USES` | a constructor or method parameter or return type, a static field, or a nested type mentions it |
| `DEPENDS` | only a method body refers to it |

A type that relates to another in several ways shows the strongest relation only,
in the order of this table. Thrown exceptions count as `DEPENDS`.

**Explorer.** `index.html` has four views: the module graph, a module's package
graph, a package's key types, and one type. A module with one package opens at
the package. Click a node to see its details in the side panel, and double-click
it, or use a link in the panel, to open its view. Esc goes up one level, and the
browser's back button works as usual. The checkboxes select the relation kinds
to draw (`depends` is off by default), all types instead of key types, arrows to
`common` in a package view, and implied arrows. A type view draws at most 30
relations, the strongest kinds and key types first; the panel lists all of them.
The search box takes a module, package, or type name.

## 3. Annotating the code

The atlas shows what the annotations in `io.github.tlaplus.hardening.common`
declare:

```java
/**
 * The stages that run one workflow invocation, wired to their queues, result capacities, and shared
 * collaborators, and the order in which they start and stop.
 */
@ArchRole(Role.PROCESS)
@Key
public final class StageGraph { ... }
```

- **Summary.** The first sentence of the Javadoc. Write it as the purpose of the
  type, since the atlas shows nothing else about it. A mention of "ADR 0010"
  links to that decision.
- **`@ArchRole`.** One of `ENTRY_POINT`, `FACADE`, `CONTRACT`, `PROCESS`,
  `ADAPTER`, `STORE`, `CODEC`, `POLICY`, `MODEL`, `CONFIG`, `VIEW`, `UTILITY`; the
  explorer's legend gives their meaning. Pick the role by what callers rely on: a
  record that is also the package's entry point is a `FACADE`, not a `MODEL`.
- **`@Key`.** Mark the types a reader needs in order to understand the package:
  its facade, its processes, and the model types that cross its boundary. Three to
  six per package keeps the diagram readable. Do not mark helpers.

Every package needs a `package-info.java` whose first sentence states what the
package is for, and at least one `@Key` type. Every `@Key` type needs Javadoc and
an `@ArchRole`. `ArchitectureAnnotationsTest` fails otherwise. The annotations
have source retention, so they add no dependency on `common` to the bytecode.

## 4. Changing the layering

`io.github.tlaplus.hardening.common.ArchModule` lists, per module, the modules it
may depend on directly. To allow a new dependency, add it to the constant's list,
and say why in the change. A constant may list only constants declared before it,
so a cycle cannot be declared. To add a top-level package, add a constant at its
place in the order. `LayeringTest` reports each disallowed dependency with one
class pair that causes it:

```text
corpus -> workflow is not allowed by ArchModule.CORPUS:
  io.github.tlaplus.hardening.corpus.CorpusLock -> io.github.tlaplus.hardening.workflow.WorkflowException
```
