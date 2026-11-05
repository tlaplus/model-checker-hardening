package io.github.tlaplus.hardening.cli;

import io.github.tlaplus.hardening.config.FuzzTlaConfig;
import io.github.tlaplus.hardening.config.MetamorphicConfig;
import io.github.tlaplus.hardening.config.RuleLibraryConfig;
import io.github.tlaplus.hardening.config.SeedConfig;
import io.github.tlaplus.hardening.corpus.CorpusStage;
import io.github.tlaplus.hardening.corpus.Technique;
import io.github.tlaplus.hardening.signature.KnownDefectDatabase;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The configuration {@code fuzztla init} writes (ADR 0025 §1): the defaults, the shipped
 * known-defect database, the settings that follow from the technique, and fresh seeds (ADR 0026). A path is written
 * relative to the corpus when both lie in the project, so the corpus keeps working when the
 * project moves, and absolutely otherwise, since a corpus elsewhere does not move with it.
 */
final class InitialConfig {
    /** The rule module {@code --how=mt} rewrites with, and its directory in the project. */
    static final RuleLibraryConfig.RuleModule SHIPPED_RULES =
            new RuleLibraryConfig.RuleModule("Rewrites", List.of(Path.of("libraries", "rewrites")));

    /** The temporal rule module {@code --temporal} adds (ADR 0027 §5), beside the rewrite rules. */
    static final RuleLibraryConfig.RuleModule SHIPPED_TEMPORAL_RULES =
            new RuleLibraryConfig.RuleModule("TemporalRewrites", List.of(Path.of("libraries", "rewrites")));

    /**
     * The temporal rule weights with Apalache among the checkers (ADR 0029 §3): the rules that
     * expand {@code WF} and {@code SF} by {@code ENABLED}, which Apalache does not support, are off.
     */
    static final Map<String, Integer> APALACHE_TEMPORAL_WEIGHTS = Map.of("WFDef", 0, "SFDef", 0);

    private final Path corpus;
    private final Optional<Path> project;
    private final SeedConfig seeds;

    /**
     * @param corpus the corpus directory
     * @param project the project directory that ships the signature databases and rules, if found
     * @param generatorSeed the generator seed, from which the other seeds follow (ADR 0026)
     */
    InitialConfig(Path corpus, Optional<Path> project, long generatorSeed) {
        this.corpus = Objects.requireNonNull(corpus, "corpus").toAbsolutePath().normalize();
        this.project = Objects.requireNonNull(project, "project").map(path -> path.toAbsolutePath().normalize());
        this.seeds = SeedConfig.derive(generatorSeed);
    }

    /** Draws an unpredictable generator seed, clearing the sign bit. */
    static long randomSeed() {
        return new SecureRandom().nextLong() & Long.MAX_VALUE;
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
     * Returns the configuration of an {@code mt} corpus that adopts from {@code baseCorpus} (ADR
     * 0025 §5): the base's whole configuration {@code base}, whose paths are absolute, with the
     * {@code mt} settings and {@code base_corpus}. The libraries, rules and tools it names stay
     * absolute, since init copies them into the corpus. The seeds are this corpus's own, not the
     * base's.
     */
    FuzzTlaConfig inherit(FuzzTlaConfig base, Path baseCorpus) {
        var adoption = base.metamorphic().adoption();
        var config = base
                .withKnownDefects(base.workflow().inputs().knownDefects().stream().map(this::path).toList())
                .withMetamorphic(base.metamorphic().withAdoption(
                        new MetamorphicConfig.Adoption(Optional.of(path(baseCorpus)), adoption.ratio())));
        return forTechnique(config, Technique.MT);
    }

    /**
     * Returns {@code config} running {@code technique} with this corpus's seeds: a kind the
     * technique generates and, for {@code mt}, the shipped rewrite rules and, with Apalache, the
     * metamorphic signatures.
     */
    FuzzTlaConfig forTechnique(FuzzTlaConfig config, Technique technique) {
        var result = config.withTechnique(technique).withGeneratedKind(technique.kindFor(config.generatedKind()))
                .withSeeds(seeds);
        if (technique != Technique.MT || project.isEmpty()) {
            return result;
        }
        var metamorphic = result.metamorphic();
        if (metamorphic.rules().isEmpty()) {
            // Absolute, since init copies the rules into the corpus (ADR 0025 §2).
            var classpath = SHIPPED_RULES.classpath().stream().map(project.orElseThrow()::resolve).toList();
            result = result.withMetamorphic(metamorphic.withRules(
                    RuleLibraryConfig.of(new RuleLibraryConfig.RuleModule(SHIPPED_RULES.module(), classpath))));
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

    /**
     * Returns {@code config}, an {@code mt} corpus's, rewriting and checking temporal properties
     * too (ADR 0027 §5, ADR 0029 §2, §3): with the shipped temporal rules and the same checkers.
     * Without Apalache, the metamorphic signatures, which quarantine {@code CHOOSE} for Apalache,
     * are not needed. With Apalache, the temporal Apalache signatures quarantine fairness and
     * {@code ENABLED}, and {@code WFDef} and {@code SFDef}, which introduce {@code ENABLED}, are off.
     *
     * @throws IllegalStateException if the project, which ships the rules, was not found
     */
    FuzzTlaConfig withTemporalRules(FuzzTlaConfig config) {
        var root = project.orElseThrow(() -> new IllegalStateException("the shipped temporal rules were not found"));
        // Absolute, since init copies the rules into the corpus (ADR 0025 §2).
        var classpath = SHIPPED_TEMPORAL_RULES.classpath().stream().map(root::resolve).toList();
        var apalache = config.workflow().enabledCheckers().contains(CorpusStage.APALACHE);
        var weights = apalache ? APALACHE_TEMPORAL_WEIGHTS : Map.<String, Integer>of();
        var result = config.withMetamorphic(config.metamorphic().withTemporalRules(new RuleLibraryConfig(
                Optional.of(new RuleLibraryConfig.RuleModule(SHIPPED_TEMPORAL_RULES.module(), classpath)), weights)));
        var metamorphic = shipped(KnownDefectDatabase.METAMORPHIC);
        var databases = new ArrayList<>(result.workflow().inputs().knownDefects());
        if (!apalache) {
            databases.removeIf(database -> sameFile(database, metamorphic));
            return result.withKnownDefects(databases);
        }
        for (var required : List.of(metamorphic, shipped(KnownDefectDatabase.TEMPORAL_APALACHE))) {
            if (databases.stream().noneMatch(database -> sameFile(database, required))) {
                databases.add(required);
            }
        }
        return result.withKnownDefects(databases);
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
