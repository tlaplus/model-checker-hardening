package io.github.tlaplus.hardening;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FuzzTlaLauncherTest {
    @Test
    void runsFromAnImmutableJarSnapshotAndCleansItAfterFailure(@TempDir Path directory)
            throws Exception {
        Assumptions.assumeFalse(System.getProperty("os.name").startsWith("Windows"));

        var project = directory.resolve("project");
        var bin = Files.createDirectories(project.resolve("bin"));
        var target = Files.createDirectories(project.resolve("target"));
        var fakeBin = Files.createDirectories(directory.resolve("fake-bin"));
        var runtime = Files.createDirectories(directory.resolve("runtime"));
        var launcher = bin.resolve("fuzztla");
        Files.copy(Path.of("bin", "fuzztla"), launcher);
        assertTrue(launcher.toFile().setExecutable(true));

        var packagedJar = target.resolve("fuzztla.jar");
        Files.writeString(packagedJar, "original", StandardCharsets.UTF_8);
        var packagedApalacheJar = target.resolve("apalache.jar");
        Files.writeString(packagedApalacheJar, "apalache-original", StandardCharsets.UTF_8);

        var observedJar = directory.resolve("observed-jar");
        var observedApalacheJar = directory.resolve("observed-apalache-jar");
        var ready = directory.resolve("ready");
        var proceed = directory.resolve("proceed");
        var fakeJava = fakeBin.resolve("java");
        Files.writeString(
                fakeJava,
                """
                #!/bin/sh
                set -eu
                printf '%s\n' "$2" > "$OBSERVED_JAR"
                runtime_dir=$(dirname "$2")
                printf '%s\n' "$runtime_dir/apalache.jar" > "$OBSERVED_APALACHE_JAR"
                : > "$READY_FILE"
                while [ ! -f "$PROCEED_FILE" ]; do
                    sleep 0.01
                done
                cat "$2"
                printf '|'
                cat "$runtime_dir/apalache.jar"
                exit 23
                """,
                StandardCharsets.UTF_8);
        assertTrue(fakeJava.toFile().setExecutable(true));

        var processBuilder = new ProcessBuilder(launcher.toString());
        var environment = processBuilder.environment();
        environment.put(
                "PATH", fakeBin + System.getProperty("path.separator") + environment.get("PATH"));
        environment.put("TMPDIR", runtime.toString());
        environment.put("OBSERVED_JAR", observedJar.toString());
        environment.put("OBSERVED_APALACHE_JAR", observedApalacheJar.toString());
        environment.put("READY_FILE", ready.toString());
        environment.put("PROCEED_FILE", proceed.toString());

        var process = processBuilder.start();
        try {
            awaitFile(ready, process, Duration.ofSeconds(5));
            Files.writeString(packagedJar, "replacement", StandardCharsets.UTF_8);
            Files.writeString(
                    packagedApalacheJar, "apalache-replacement", StandardCharsets.UTF_8);
            Files.writeString(proceed, "", StandardCharsets.UTF_8);
            assertTrue(process.waitFor(5, TimeUnit.SECONDS));
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                process.waitFor();
            }
        }

        assertEquals(23, process.exitValue());
        assertEquals(
                "original|apalache-original",
                new String(
                        process.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
        var snapshot = Path.of(Files.readString(observedJar, StandardCharsets.UTF_8).strip());
        assertTrue(snapshot.startsWith(runtime));
        assertFalse(snapshot.equals(packagedJar));
        assertFalse(Files.exists(snapshot));
        var apalacheSnapshot =
                Path.of(Files.readString(observedApalacheJar, StandardCharsets.UTF_8).strip());
        assertTrue(apalacheSnapshot.startsWith(runtime));
        assertFalse(apalacheSnapshot.equals(packagedApalacheJar));
        assertFalse(Files.exists(apalacheSnapshot));
        try (var paths = Files.list(runtime)) {
            assertEquals(0, paths.count());
        }
    }

    private static void awaitFile(Path path, Process process, Duration timeout) throws Exception {
        var deadline = System.nanoTime() + timeout.toNanos();
        while (!Files.exists(path) && process.isAlive() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertTrue(Files.exists(path), "launcher did not start the fake Java process");
    }
}
