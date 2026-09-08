package io.github.tlaplus.hardening.workflow.worker;

import java.nio.file.Path;

/** How this project spells a child JVM launch, so every caller spells it the same way. */
public final class JavaLaunch {
    private JavaLaunch() {}

    /** The {@code java} binary of the running JVM, so a child never depends on {@code PATH}. */
    public static String executable() {
        return Path.of(System.getProperty("java.home"), "bin", "java").toString();
    }

    public static String maximumHeap(int megabytes) {
        return "-Xmx" + megabytes + "m";
    }
}
