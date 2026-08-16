package io.github.tlaplus.hardening.workflow;

import java.io.IOException;

/** Verifies that generated specifications can resolve their packaged standard modules. */
final class StandardModuleResources {
    private StandardModuleResources() {}

    static void require(Class<?> owner, String... names) throws IOException {
        for (var name : names) {
            var resource = "tla2sany/StandardModules/" + name;
            if (owner.getClassLoader().getResource(resource) == null) {
                throw new IOException("missing standard module: " + resource);
            }
        }
    }
}
