# 0022: Architecture atlas

**Authors:** Igor Konnov and Claude

**Status:** Proposed

**Date:** 2026-10-21

The [manual][manual] describes how to annotate the code, regenerate the atlas,
and read its views.

## Context

FuzzTLA has about 300 top-level types in 27 packages and 31,000 lines. The
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
  | `PROCESS` | a loop or orchestrator that owns threads or a lifecycle | `StageGraph`, `GenerationLoop` |
  | `ADAPTER` | wraps an external tool, process, or protocol | `TlcProcess`, `ToolWorkerConnection` |
  | `STORE` | layout, locking, persistence | `CorpusEntryStore`, `CorpusLock` |
  | `CODEC` | a wire or storage format | `CorpusEnvelopeCodec` |
  | `POLICY` | a decision rule | `TlcOutcomeClassifier`, `CheckingPolicy` |
  | `MODEL` | a domain value or enum | `CorpusStage`, `GeneratedSpec` |
  | `CONFIG` | configuration and its schema | `FuzzTlaConfig` |
  | `VIEW` | rendering and formatting | `RunView` |
  | `UTILITY` | a helper with no domain meaning | `FileTrees` |

- `@Key` marks a type as one of the few that carry its package. Key types appear
  in the package diagrams, and other types are counted but not drawn.

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
| `USES` | a constructor or method signature that mentions the target, where no `HAS` exists |
| `DEPENDS` | a `jdeps` class edge that none of the above explains |

The doclet writes `model.json` and renders two views of it into
`target/architecture`:

- **Markdown with Mermaid.** `README.md` is the module graph. Each node shows the
  package summary, and an undeclared edge is drawn in red. Each module page
  `modules/<module>.md` has the module's subpackages, a `classDiagram` of its key
  types with «role» stereotypes, and a table of all its types with role and
  summary. Each key type has a page `types/<qualified name>.md` that shows its
  relations to other types. GitHub renders these pages with their diagrams.
- **Interactive explorer.** `index.html` is a self-contained page that embeds the
  model and draws it with Cytoscape.js and an ELK layered layout, loaded from a
  CDN at pinned versions. Modules are compound nodes that expand into packages and
  key types. A toggle shows all types, and selecting a type focuses on its
  neighbourhood. Relation kinds and roles can be filtered, and a search box finds
  types by name. A side panel shows the summary, role, linked ADRs, and source
  link.

Type names link to the source on GitHub at the commit the atlas is built from. A
mention of "ADR NNNN" in a summary links to that decision. `make architecture`
builds the atlas.

`ArchitectureAnnotationsTest` runs the doclet in-process and requires a
`package-info.java` with Javadoc for every package, class Javadoc on every key
type, and at least one key type per package, so that no package view is empty.

### 4. Publishing on `gh-pages`

On every push to `main`, the `architecture` workflow builds the atlas, adds
`.nojekyll`, and commits it to the `gh-pages` branch with plain `git`, naming the
source commit in the message. When the atlas has not changed, the workflow makes
no commit. GitHub Pages serves `index.html`. GitHub's view of the branch renders
`README.md` and the Mermaid pages, and the branch history is a diffable record of
architecture changes.

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
- **Build cost.** `ArchitectureAnnotationsTest` adds one Javadoc pass over the
  main sources to `mvn verify`.
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
