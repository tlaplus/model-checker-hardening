package io.github.tlaplus.hardening.workflow.worker;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * How to launch one isolated tool worker: where it may write, how long it may take to come up,
 * which class it runs, what goes in front of this JVM's class path, which JVM options it needs, and
 * what to call it in diagnostics.
 */
public record WorkerSpec(
        Path scratchDirectory,
        Duration startupTimeout,
        Class<?> workerMain,
        List<Path> classpathPrefix,
        List<String> jvmArguments,
        String description) {
    public WorkerSpec {
        Objects.requireNonNull(scratchDirectory, "scratchDirectory");
        Objects.requireNonNull(startupTimeout, "startupTimeout");
        Objects.requireNonNull(workerMain, "workerMain");
        classpathPrefix = List.copyOf(classpathPrefix);
        jvmArguments = List.copyOf(jvmArguments);
        if (Objects.requireNonNull(description, "description").isBlank()) {
            throw new IllegalArgumentException("description must not be blank");
        }
    }

    /** A worker that needs no extra class-path entries and no extra JVM options. */
    public WorkerSpec(
            Path scratchDirectory,
            Duration startupTimeout,
            Class<?> workerMain,
            String description) {
        this(scratchDirectory, startupTimeout, workerMain, List.of(), List.of(), description);
    }
}
