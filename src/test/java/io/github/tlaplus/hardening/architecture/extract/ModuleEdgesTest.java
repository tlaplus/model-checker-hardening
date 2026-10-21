package io.github.tlaplus.hardening.architecture.extract;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.tlaplus.hardening.architecture.extract.DependencyScanner.ClassEdge;
import io.github.tlaplus.hardening.common.ArchModule;
import java.util.List;
import org.junit.jupiter.api.Test;

class ModuleEdgesTest {
    private static final String ROOT = ArchModule.ROOT_PACKAGE;

    @Test
    void marksModuleEdgesThatALongerPathImplies() {
        var edges = ModuleEdges.of(List.of(
                new ClassEdge(ROOT + ".cli.A", ROOT + ".workflow.B"),
                new ClassEdge(ROOT + ".workflow.B", ROOT + ".corpus.C"),
                new ClassEdge(ROOT + ".cli.A", ROOT + ".corpus.C"),
                new ClassEdge(ROOT + ".cli.D", ROOT + ".corpus.C")));
        assertEquals(List.of("workflow->corpus:false", "cli->corpus:true", "cli->workflow:false"),
                edges.stream().map(e -> e.from() + "->" + e.to() + ":" + e.implied()).toList());
        assertEquals(2, edges.stream().filter(e -> e.from().equals("cli") && e.to().equals("corpus"))
                .findFirst().orElseThrow().typePairs());
    }
}
