package io.github.tlaplus.hardening.architecture.extract;

import java.nio.file.Path;

/**
 * What one atlas build reads and writes.
 *
 * @param root the repository root, which source and decision paths are relative to
 * @param sources the main source tree
 * @param classes the compiled main classes, which {@code jdeps} reads
 * @param decisions the directory of architecture decision records
 * @param output the directory the atlas is written to
 * @param repository the GitHub URL that source links point to
 * @param commit the commit or branch that source links name
 */
public record AtlasSettings(
        Path root,
        Path sources,
        Path classes,
        Path decisions,
        Path output,
        String repository,
        String commit) {

    /** The settings of a build from the repository root, with its usual layout. */
    public static AtlasSettings defaults(Path root) {
        return new AtlasSettings(
                root,
                root.resolve("src/main/java"),
                root.resolve("target/classes"),
                root.resolve("docs/decisions"),
                root.resolve("target/architecture"),
                "https://github.com/tlaplus/model-checker-hardening",
                "main");
    }

    public AtlasSettings withOutput(Path output) {
        return new AtlasSettings(root, sources, classes, decisions, output, repository, commit);
    }

    public AtlasSettings withRepository(String repository) {
        return new AtlasSettings(root, sources, classes, decisions, output, repository, commit);
    }

    public AtlasSettings withCommit(String commit) {
        return new AtlasSettings(root, sources, classes, decisions, output, repository, commit);
    }
}
