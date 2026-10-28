package io.github.tlaplus.hardening.cli;

import io.github.tlaplus.hardening.config.FuzzTlaConfig;
import io.github.tlaplus.hardening.config.MetamorphicConfig;
import io.github.tlaplus.hardening.config.RuleLibraryConfig;
import io.github.tlaplus.hardening.corpus.CorpusStage;
import io.github.tlaplus.hardening.corpus.Technique;
import io.github.tlaplus.hardening.signature.KnownDefectDatabase;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The configuration {@code fuzztla init} writes (ADR 0025 §1): the defaults, the shipped
 * known-defect database, and the settings that follow from the technique. A path is written
 * relative to the corpus when both lie in the project, so the corpus keeps working when the
 * project moves, and absolutely otherwise, since a corpus elsewhere does not move with it.
 */
final class InitialConfig {
    /** The rule module {@code --how=mt} rewrites with, and its directory in the project. */
    static final RuleLibraryConfig.RuleModule SHIPPED_RULES =
            new RuleLibraryConfig.RuleModule("Rewrites", List.of(Path.of("libraries", "rewrites")));

    private final Path corpus;
    private final Optional<Path> project;

    /**
     * @param corpus the corpus directory
     * @param project the project directory that ships the signature databases and rules, if found
     */
    InitialConfig(Path corpus, Optional<Path> project) {
        this.corpus = Objects.requireNonNull(corpus, "corpus").toAbsolutePath().normalize();
        this.project = Objects.requireNonNull(project, "project").map(path -> path.toAbsolutePath().normalize());
    }

    /** Whether the project, and so the shipped databases and rules, was found. */
    boolean hasProject() {
        return project.isPresent();
    }

    /** Returns the configuration of a new corpus that runs {@code technique}. */
    FuzzTlaConfig create(Technique technique) {
        var config = FuzzTlaConfig.defaults();
        if (project.isPresent()) {
            config = config.withKnownDefects(List.of(shipped(KnownDefectDatabase.SHIPPED)));
        }
        return forTechnique(config, technique);
    }

    /**
     * Returns {@code config} running {@code technique}: a kind the technique generates and, for
     * {@code mt}, the shipped rewrite rules and, with Apalache, the metamorphic signatures.
     */
    FuzzTlaConfig forTechnique(FuzzTlaConfig config, Technique technique) {
        var result = config.withTechnique(technique).withGeneratedKind(technique.kindFor(config.generatedKind()));
        if (technique != Technique.MT || project.isEmpty()) {
            return result;
        }
        var metamorphic = result.metamorphic();
        if (metamorphic.rules().isEmpty()) {
            // Absolute, since init copies the rules into the corpus (ADR 0025 §2).
            var classpath = SHIPPED_RULES.classpath().stream().map(project.orElseThrow()::resolve).toList();
            result = result.withMetamorphic(new MetamorphicConfig(
                    RuleLibraryConfig.of(new RuleLibraryConfig.RuleModule(SHIPPED_RULES.module(), classpath)),
                    metamorphic.limits(), metamorphic.adoption()));
        }
        var signatures = shipped(KnownDefectDatabase.METAMORPHIC);
        var databases = result.workflow().inputs().knownDefects();
        if (result.workflow().enabledCheckers().contains(CorpusStage.APALACHE)
                && databases.stream().noneMatch(database -> sameFile(database, signatures))) {
            var extended = new ArrayList<>(databases);
            extended.add(signatures);
            result = result.withKnownDefects(extended);
        }
        return result;
    }

    /** Names a file of the project as this corpus's configuration should. */
    private Path shipped(Path relativeToProject) {
        return path(project.orElseThrow().resolve(relativeToProject));
    }

    /** Names {@code target} relative to the corpus when both lie in the project, else absolutely. */
    Path path(Path target) {
        var absolute = target.toAbsolutePath().normalize();
        return project.filter(root -> corpus.startsWith(root) && absolute.startsWith(root))
                .map(root -> corpus.relativize(absolute))
                .orElse(absolute);
    }

    private boolean sameFile(Path configured, Path written) {
        return corpus.resolve(configured).normalize().equals(corpus.resolve(written).normalize());
    }

    /** Returns the project directory if it holds the shipped known-defect database. */
    static Optional<Path> locateProject() {
        return ProjectDirectory.locate()
                .filter(directory -> Files.isRegularFile(directory.resolve(KnownDefectDatabase.SHIPPED)));
    }
}
