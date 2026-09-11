package io.github.tlaplus.hardening.workflow.worker;

import java.io.IOException;
import java.util.List;

/** Verifies that generated specifications can resolve their packaged standard modules. */
public final class StandardModuleResources {
    /** Where SANY looks for the modules bundled with a TLA+ distribution. */
    public static final String PREFIX = "tla2sany/StandardModules/";

    /** The bundled standard modules a rendered specification may extend. */
    public static final List<String> BUNDLED = List.of("Integers.tla", "Apalache.tla", "Variants.tla");

    private StandardModuleResources() {}

    public static void require(Class<?> owner, String... names) throws IOException {
        for (var name : names) {
            var resource = PREFIX + name;
            if (owner.getClassLoader().getResource(resource) == null) {
                throw new IOException("missing standard module: " + resource);
            }
        }
    }

    /** Requires every module of {@link #BUNDLED} on the class path of {@code owner}. */
    public static void requireBundled(Class<?> owner) throws IOException {
        require(owner, BUNDLED.toArray(String[]::new));
    }
}
