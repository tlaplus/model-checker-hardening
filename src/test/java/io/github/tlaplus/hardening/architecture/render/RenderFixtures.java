package io.github.tlaplus.hardening.architecture.render;

import io.github.tlaplus.hardening.architecture.model.ArchModel.ModuleNode;
import io.github.tlaplus.hardening.architecture.model.ArchModel.TypeKind;
import io.github.tlaplus.hardening.architecture.model.ArchModel.TypeNode;
import io.github.tlaplus.hardening.common.ArchModule;
import io.github.tlaplus.hardening.common.Role;
import java.util.List;

/** Small model parts for the renderer tests. */
final class RenderFixtures {
    private RenderFixtures() {}

    static ModuleNode module(String name) {
        return new ModuleNode(name, ArchModule.ROOT_PACKAGE + "." + name, "", List.of());
    }

    static TypeNode store(String name) {
        return new TypeNode(name, ArchModule.ROOT_PACKAGE + ".corpus", TypeKind.CLASS, Role.STORE, true, "",
                "src/CorpusLock.java", 12, List.of());
    }
}
