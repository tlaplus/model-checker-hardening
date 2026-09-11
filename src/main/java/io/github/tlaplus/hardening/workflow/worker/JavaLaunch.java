package io.github.tlaplus.hardening.workflow.worker;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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

    /**
     * Returns the options of a tool JVM whose heap is bounded, followed by {@code additional}. The
     * JVM exits on its first out-of-memory error, which the parent reports as a crash.
     */
    public static List<String> boundedHeap(int megabytes, String... additional) {
        var options = new ArrayList<String>();
        options.add(maximumHeap(megabytes));
        options.add("-XX:+ExitOnOutOfMemoryError");
        options.addAll(List.of(additional));
        return List.copyOf(options);
    }
}
