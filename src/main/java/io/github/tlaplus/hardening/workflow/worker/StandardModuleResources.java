package io.github.tlaplus.hardening.workflow.worker;

import java.io.IOException;

/** Verifies that generated specifications can resolve their packaged standard modules. */
public final class StandardModuleResources {
    /** Where SANY looks for the modules bundled with a TLA+ distribution. */
    public static final String PREFIX = "tla2sany/StandardModules/";

    private StandardModuleResources() {}

    public static void require(Class<?> owner, String... names) throws IOException {
        for (var name : names) {
            var resource = PREFIX + name;
            if (owner.getClassLoader().getResource(resource) == null) {
                throw new IOException("missing standard module: " + resource);
            }
        }
    }
}
