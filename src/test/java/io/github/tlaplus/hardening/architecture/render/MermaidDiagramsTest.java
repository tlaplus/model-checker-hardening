package io.github.tlaplus.hardening.architecture.render;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tlaplus.hardening.architecture.model.ArchModel.ModuleEdge;
import io.github.tlaplus.hardening.architecture.model.ArchModel.Relation;
import io.github.tlaplus.hardening.architecture.model.ArchModel.TypeKind;
import io.github.tlaplus.hardening.architecture.model.ArchModel.TypeNode;
import io.github.tlaplus.hardening.architecture.model.RelationKind;
import java.util.List;
import org.junit.jupiter.api.Test;

class MermaidDiagramsTest {
    @Test
    void drawsDisallowedModuleEdgesInRedAndOmitsImpliedOnes() {
        var modules = List.of(
                RenderFixtures.module("common"), RenderFixtures.module("corpus"), RenderFixtures.module("workflow"));
        var diagram = MermaidDiagrams.modules(modules, List.of(
                new ModuleEdge("corpus", "common", 3, true, false, "a", "b"),
                new ModuleEdge("corpus", "workflow", 1, false, false, "a", "c"),
                new ModuleEdge("workflow", "common", 2, true, true, "c", "b"),
                new ModuleEdge("workflow", "corpus", 2, true, false, "c", "a")));
        assertFalse(diagram.contains("workflow --> common"));
        assertTrue(diagram.contains("  corpus --> common\n  corpus --> workflow\n  workflow --> corpus\n"
                + "  linkStyle 1 stroke:"), diagram);
    }

    @Test
    void drawsClassRelationsWithStereotypes() {
        var store = RenderFixtures.store("io.github.tlaplus.hardening.corpus.CorpusLock");
        var other = new TypeNode("io.github.tlaplus.hardening.corpus.CorpusLayout",
                "io.github.tlaplus.hardening.corpus", TypeKind.RECORD, null, false, "", "x", 1, List.of());
        var diagram = MermaidDiagrams.classes(List.of(store, other),
                List.of(new Relation(store.name(), other.name(), RelationKind.HAS)), true);
        assertTrue(diagram.contains("namespace corpus {"), diagram);
        assertTrue(diagram.contains("<<store>>"), diagram);
        assertTrue(diagram.contains("<<record>>"), diagram);
        assertTrue(diagram.contains("CorpusLock --> CorpusLayout : has"), diagram);
    }
}
