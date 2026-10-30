package io.github.tlaplus.hardening.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tlaplus.hardening.config.RuleLibraryConfig;
import io.github.tlaplus.hardening.corpus.CheckerSet;
import io.github.tlaplus.hardening.corpus.CorpusStage;
import io.github.tlaplus.hardening.corpus.Technique;
import io.github.tlaplus.hardening.gen.InputKind;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class InitialConfigTest {
    private static final Path PROJECT = Path.of("/work/fuzztla");
    private static final Path SHIPPED = Path.of("..", "signatures", "known-defects.toml");
    private static final Path METAMORPHIC = Path.of("..", "signatures", "metamorphic-defects.toml");

    private final InitialConfig inside = new InitialConfig(PROJECT.resolve("corpus"), Optional.of(PROJECT));

    @Test
    void aPbtCorpusNamesTheShippedDatabaseRelativeToItself() {
        var config = inside.create(Technique.PBT);

        assertEquals(Technique.PBT, config.workflow().technique());
        assertEquals(InputKind.MODULE, config.generatedKind());
        assertEquals(List.of(SHIPPED), config.workflow().inputs().knownDefects());
        assertTrue(config.metamorphic().rules().isEmpty());
    }

    @Test
    void aGbtCorpusGeneratesGraphs() {
        var config = inside.create(Technique.GBT);

        assertEquals(Technique.GBT, config.workflow().technique());
        assertEquals(InputKind.GRAPH, config.generatedKind());
    }

    /** An mt corpus rewrites with the shipped rules and, with Apalache, filters its known duplicates. */
    @Test
    void anMtCorpusUsesTheShippedRulesAndMetamorphicSignatures() {
        var config = inside.create(Technique.MT);

        assertEquals(Technique.MT, config.workflow().technique());
        assertEquals(Optional.of(new RuleLibraryConfig.RuleModule("Rewrites", List.of(Path.of("..", "libraries", "rewrites")))),
                config.metamorphic().rules().module());
        assertEquals(List.of(SHIPPED, METAMORPHIC), config.workflow().inputs().knownDefects());
        assertEquals(config, inside.forTechnique(config, Technique.MT), "applying the settings twice changes nothing");
    }

    @Test
    void anMtCorpusWithoutApalacheListsNoMetamorphicSignatures() {
        var pbt = inside.create(Technique.PBT);
        var tlcOnly = pbt.withWorkflow(pbt.workflow().withEnabledCheckers(CheckerSet.of(CorpusStage.TLC)));

        assertEquals(List.of(SHIPPED), inside.forTechnique(tlcOnly, Technique.MT).workflow().inputs().knownDefects());
    }

    @Test
    void aCorpusOutsideTheProjectNamesProjectFilesAbsolutely() {
        var outside = new InitialConfig(Path.of("/elsewhere/corpus"), Optional.of(PROJECT));

        assertEquals(List.of(PROJECT.resolve("signatures/known-defects.toml")),
                outside.create(Technique.PBT).workflow().inputs().knownDefects());
    }

    @Test
    void withoutTheProjectNothingShippedIsNamed() {
        var config = new InitialConfig(PROJECT.resolve("corpus"), Optional.empty()).create(Technique.MT);

        assertEquals(List.of(), config.workflow().inputs().knownDefects());
        assertTrue(config.metamorphic().rules().isEmpty());
    }
}
