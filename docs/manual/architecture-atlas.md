# Architecture atlas

> **Status:** Proposed, not implemented yet.
> [ADR 0022](../decisions/0022-architecture-atlas.md) records the design.

The architecture atlas is a generated map of the FuzzTLA code, with three zoom
levels: modules and their dependencies, the key types of each module, and the
relations of one type. Annotations in the code decide what the atlas shows. This
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
| `modules/<module>.md` | one module's packages, key types, and all its types |
| `types/<qualified name>.md` | one key type and its relations |
| `model.json` | the extracted model that both views draw |

Source links point to the commit that `HEAD` names. A build from a working tree
with uncommitted changes links to code that differs from what it shows.

The published atlas of `main` is on the repository's GitHub Pages site. The
`gh-pages` branch holds its Markdown pages, and the branch history records how
the architecture changed.

## 2. Reading the views

**Modules.** One node per top-level package, labelled with the first sentence of
its `package-info.java`. An arrow `A → B` means that code in `A` refers to code in
`B`. A red arrow is a dependency that `ArchModule` does not allow, and
`mvn verify` fails on it.

**Module.** The module's packages and their key types, each with its «role». The
remaining types are counted per package. The table at the end lists every type,
its role, and its summary.

**Type.** One type and the types it relates to:

| Arrow | Meaning |
|---|---|
| `EXTENDS`, `IMPLEMENTS` | a supertype |
| `HAS` | a field or record component of that type, possibly inside `Optional`, a collection, or a map |
| `USES` | a constructor or method signature mentions it |
| `DEPENDS` | only a method body refers to it |

In the explorer, click a module to expand it, and click a type to focus on its
neighbours. Use the side panel for the summary, linked ADRs, and source link, and
the filters to hide relation kinds or roles. Search finds a type by name.

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
- **`@ArchRole`.** One of `ENTRY_POINT`, `FACADE`, `PROCESS`, `ADAPTER`, `STORE`,
  `CODEC`, `POLICY`, `MODEL`, `CONFIG`, `VIEW`, `UTILITY`. Pick the role by what
  callers rely on: a record that is also the package's entry point is a
  `FACADE`, not a `MODEL`.
- **`@Key`.** Mark the types a reader needs in order to understand the package:
  its facade, its processes, and the model types that cross its boundary. Three to
  six per package keeps the diagram readable. Do not mark helpers.

Every package needs a `package-info.java` whose first sentence states what the
package is for, and at least one `@Key` type with Javadoc. `mvn verify` fails
otherwise.

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
