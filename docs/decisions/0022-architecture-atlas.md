# 0022: Architecture atlas

**Authors:** Igor Konnov and Claude

**Status:** Implemented

**Date:** 2026-10-21

**Implementation.** Stacked branches `igor/arch-atlas-adr` to
`igor/arch-atlas-annotations` (FuzzTLA `b59a5a6`). The atlas of `b59a5a6` has 371
types, 129 of them key, 2,095 relations, and 34 module edges, of which the module
graph draws 14. Every edge conforms to `ArchModule`. The package graph of
`workflow` shows a cycle between `workflow` and `workflow.execution`.

The [manual][manual] describes how to annotate the code, regenerate the atlas,
and read its views.

## Context

FuzzTLA has 371 top-level types in 358 files, 27 packages, and 31,000 lines. The
architecture documents ([fuzzing workflows][fuzzing-workflows],
[IR generators][ir-generators]) describe the pipeline and the generator. They do not
show which types implement it, or how the packages depend on each other. The
CLAUDE.md layering rule is prose, and no test checks it.

Generic UML browsers do not fill this gap. They give every class and every
dependency the same weight, so a package diagram of `corpus` shows 50 types, and a
class diagram of `StageGraph` shows every record it touches. What a reader needs
is a curated view: the few types that carry a package, their roles, and their
one-sentence purpose. The source does not record which types those are. The
class Javadoc already holds the purposes, but 13 packages have no
`package-info.java`: the root, `checker`, `cli`, `common`, `config`, `corpus`,
`gen` and its four subpackages, `workflow`, and `workflow.library`.

`jdeps` on `target/classes` gives the package graph. At FuzzTLA `17b54e5` it
conforms to the CLAUDE.md layering, and it shows that `signature` depends only on
`common`.

## Decision

### 1. Annotations declare role and importance; Javadoc holds the text

Two source-retention annotations in `common`, the leaf package:

- `@ArchRole(Role)` on a top-level type states what the type does in its package.
  `Role` constants each carry a one-line description:

  | Role | Meaning | Example |
  |---|---|---|
  | `ENTRY_POINT` | a command or a process main | `RunCommand`, `TlcWorkerMain` |
  | `FACADE` | the type other packages call | `CorpusDirectory`, `IrGenerators` |
  | `CONTRACT` | an interface or base type that other types implement | `ToolBackend`, `CandidateSource` |
  | `PROCESS` | a loop or orchestrator that owns threads or a lifecycle | `StageGraph`, `GenerationLoop` |
  | `ADAPTER` | wraps an external tool, process, or protocol | `TlcProcess`, `WorkerChannel` |
  | `STORE` | layout, locking, persistence | `CorpusEntryStore`, `CorpusLayout` |
  | `CODEC` | a wire or storage format | `CorpusEnvelopeCodec` |
  | `POLICY` | a decision rule | `TlcOutcomeClassifier`, `CheckingPolicy` |
  | `MODEL` | a domain value or enum | `CorpusStage`, `GeneratedSpec` |
  | `CONFIG` | configuration and its schema | `FuzzTlaConfig` |
  | `VIEW` | rendering and formatting | `RunView` |
  | `UTILITY` | a helper with no domain meaning | `FileTrees` |

- `@Key` marks a type as one of the few that carry its package. Key types appear
  in the package diagrams, and other types are counted but not drawn. At the
  implementation, 129 of the 371 types are key, one to ten per package.

Untagged types have role "other". The annotations hold no text. A type's
summary is the first sentence of its Javadoc, and a package's summary is the first
sentence of its `package-info.java`. Source retention keeps the annotations out of
the bytecode, so they add no dependency that `jdeps` would report.

### 2. `ArchModule` declares the layering, and a test enforces it

`common.ArchModule` has one constant per top-level package and one for the root
package. Each constant names its package and lists the modules it may depend on
directly. A constant may list only constants declared before it, so the declared
graph is acyclic by construction:

```java
public enum ArchModule {
    COMMON("common"),
    CHECKER("checker", COMMON),
    GEN("gen", COMMON),
    SIGNATURE("signature", COMMON),
    MUTATION("mutation", COMMON),
    CORPUS("corpus", COMMON, CHECKER, GEN, MUTATION),
    CONFIG("config", COMMON, CORPUS, GEN, MUTATION),
    DATABASE("database", COMMON, CHECKER, CORPUS, GEN, MUTATION),
    WORKFLOW("workflow", COMMON, CHECKER, CONFIG, CORPUS, GEN, MUTATION, SIGNATURE),
    CLI("cli", /* every module above */),
    LAUNCHER("", CLI);
}
```

A package belongs to the constant named by its first segment below
`io.github.tlaplus.hardening`, so `workflow.tlc` belongs to `WORKFLOW`. Only the
root package itself belongs to `LAUNCHER`, and a new top-level package belongs to
no constant until one is added. The allowed sets are the dependencies at `17b54e5`, not their
transitive closure. A new edge between modules is therefore a one-line diff of
the enum that a reviewer sees.

`LayeringTest` runs `jdeps -verbose:class` through `java.util.spi.ToolProvider` on
`target/classes`. It folds the class edges into module edges and fails on every
edge the enum does not allow, naming one class pair per edge. It also fails on a
package that no constant covers. `ArchModule` replaces the prose rule in CLAUDE.md
as the authoritative layering, and CLAUDE.md points to it.

Layering between subpackages of one module, such as the `gen.engine` constraints
of [IR generators][ir-generators], is out of scope.

### 3. A doclet extracts one model, and two renderers draw it

`ArchitectureDoclet`, a `jdk.javadoc.doclet.Doclet` in the test sources, reads
`src/main/java` in one pass. The type-attributed element model and `DocTrees` give
it each type's kind, annotations, supertypes, field and signature types, Javadoc,
and source line. Through the same `ToolProvider`, it adds the body-only class
dependencies that `jdeps` reports. Nested types are folded into their top-level
type. The result is a model of modules, packages, types, and relations:

| Relation | Source |
|---|---|
| `EXTENDS`, `IMPLEMENTS` | supertypes declared in FuzzTLA |
| `HAS` | an instance field or record component whose type mentions the target, including inside `Optional`, collection, and map type arguments |
| `USES` | a parameter or return type of a non-private constructor or method, a static field, or a declaration of a nested type, where no `HAS` exists |
| `DEPENDS` | a `jdeps` class edge that none of the above explains |

Each type pair keeps only its strongest relation, in the order of the table.
Thrown exceptions count as `DEPENDS`, so that the exception types that most
signatures mention do not crowd the diagrams. A module edge counts the type pairs
behind it. It is *implied* when a path through other modules connects its ends,
as `cli → corpus` is implied by `cli → workflow → corpus`.

The doclet writes `model.json` and renders two views of it into
`target/architecture`:

- **Markdown with Mermaid.** `README.md` is the module graph. It draws the edges
  that are not implied, which is the transitive reduction of the module graph: 14
  of 34 edges at `78bb8ff`. An undeclared edge is always drawn, in red. A table
  lists every module with its summary and every dependency. Each module page
  `modules/<module>.md` has a graph of the module's packages, reduced in the same
  way, when it has more than one. For each package it has a `classDiagram` of the
  key types with «role» stereotypes and a table of all types with role and
  summary. Each type has a page `types/<qualified name>.md` that draws up to 30
  of its relations and lists all of them. GitHub renders these pages with their
  diagrams.
- **Interactive explorer.** `index.html` is a self-contained page that embeds the
  model and draws it with Cytoscape.js and an ELK layered layout, loaded from a
  CDN at pinned versions. It has four views, each with its own URL fragment, so
  that a view can be linked: the module graph; a module's package graph
  (`#module/workflow`), whose nodes name each package's first key types; a
  package's key types (`#package/<name>`), with the other types counted as "+N
  more" and one node per other package or module they relate to; and one type
  with up to 30 neighbours (`#type/<qualified name>`), the strongest relations and
  key types first. A module with one package opens at the package. Graphs of
  modules and packages hide implied edges. Checkboxes select the relation kinds, all types instead of key
  types, edges to `common`, and implied edges, and a search box finds a module,
  package, or type by name. A side panel shows the summary, role, linked ADRs,
  source link, and every relation. Roles colour the nodes, and the page follows
  the browser's light or dark theme.

Type names link to the source on GitHub at the commit the atlas is built from. A
mention of "ADR NNNN" in a summary links to that decision. `make architecture`
builds the atlas.

The tool lives in `io.github.tlaplus.hardening.architecture` of the test
sources, so it stays out of `fuzztla.jar`, in three packages. `model` holds the
records, their JSON form, and the transitive reduction. `extract` runs the doclet,
which writes `model.json`. `render` turns a model into pages. `AtlasMain` wires
extraction to rendering through the JSON model, and `ToolLayeringTest` checks that
`extract` and `render` depend on `model` and not on each other.

`ArchitectureAnnotationsTest` requires a `package-info.java` with Javadoc for
every package, Javadoc and `@ArchRole` on every key type, and at least one key
type per package, so that no package view is empty. It reads the model of one
in-process doclet run, which `AtlasTest` shares.

### 4. Publishing on `gh-pages`

On every push to `main`, the `architecture` workflow builds the atlas, places it
under `dev/` next to the hand-written site index from `docs/site`, adds
`.nojekyll`, and commits the result to the `gh-pages` branch with plain `git`,
naming the source commit in the message. A rerun for a commit whose site is
published makes no commit. GitHub Pages serves the site index at the root and the
explorer at `dev/index.html`; GitHub's view of the branch renders `dev/README.md`
and the Mermaid pages. Since source links name the commit,
every push to `main` adds one commit to `gh-pages`, which keeps the atlas of every
commit of `main`.

## Consequences

- **Curation is code.** A new type does not enter the overview until someone marks
  it `@Key`. A reviewer sees role and importance next to the code they describe,
  and the atlas cannot drift from the source.
- **Layering is checked.** A dependency that breaks the layering fails `mvn
  verify`, and widening the layering is an explicit enum change.
- **New obligations.** Every new package needs a `package-info.java` with Javadoc
  and at least one key type.
- **No new dependencies.** The doclet API, `jdeps`, and Jackson are already
  available. The explorer's scripts load from a CDN only when the page is viewed.
- **Build cost.** The atlas tests add one Javadoc pass over the main sources to
  `mvn verify`, about two seconds.
- **Signature-level relations.** `HAS` and `USES` come from declarations.
  Dependencies inside method bodies appear only as `DEPENDS`, without a reason.

## Alternatives considered

- **IDE or standalone UML tools.** They show the code without curation, and their
  views cannot be reviewed or published with the code.
- **PlantUML or Graphviz rendering.** Better static layout, but each needs an
  installed tool. Static diagrams also cannot expand or filter.
- **A package annotation that declares the layer.** It spreads one fact, the
  layering, over 27 files, and string-valued package references fail only at run
  time. The enum keeps per-module facts on the constant.
- **Javadoc block tags such as `@arch.role`.** No compile-time checking. A typo
  silently drops a type from the overview.
- **jMolecules annotations.** A standard vocabulary, but it is oriented to
  domain-driven design, whose terms (aggregate, repository) fit FuzzTLA poorly, and
  it adds a dependency.
- **ArchUnit for the layering test.** More expressive rules, but a new dependency
  for a check that `jdeps` already supports.
- **Committing the atlas to `main`.** Visible next to the code, but every change
  would also need regenerated pages, and a CI check that they are current.

[fuzzing-workflows]: ../architecture/fuzzing-workflows.md
[ir-generators]: ../architecture/ir-generators.md
[manual]: ../manual/architecture-atlas.md
