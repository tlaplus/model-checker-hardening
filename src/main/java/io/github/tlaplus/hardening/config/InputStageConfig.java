package io.github.tlaplus.hardening.config;

import io.github.tlaplus.hardening.common.Preconditions;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * The input stage's occupancy limit and the known-defect databases its admission consults.
 *
 * <p>This record only names the databases; the workflow reads them. An empty list admits every
 * candidate, as before signatures existed. {@code knownDefectSamples} caps how many quarantined
 * candidates are kept per signature.
 */
public record InputStageConfig(int maximumEntries, List<Path> knownDefects, int knownDefectSamples) {
    public static final int DEFAULT_KNOWN_DEFECT_SAMPLES = 100;

    public InputStageConfig {
        Preconditions.requireNonnegative(maximumEntries, "maximumEntries");
        knownDefects = List.copyOf(Objects.requireNonNull(knownDefects, "knownDefects"));
        Preconditions.requireNonnegative(knownDefectSamples, "knownDefectSamples");
    }

    public static InputStageConfig defaults() {
        return of(1_000);
    }

    /** Limits the input stage without consulting a known-defect database. */
    public static InputStageConfig of(int maximumEntries) {
        return new InputStageConfig(maximumEntries, List.of(), DEFAULT_KNOWN_DEFECT_SAMPLES);
    }

    /** Resolves the database paths against the directory of the file they were read from. */
    InputStageConfig relativeTo(Path directory) {
        return new InputStageConfig(
                maximumEntries, ConfigPaths.relativeTo(knownDefects, directory), knownDefectSamples);
    }
}
