package io.github.tlaplus.hardening.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.tlaplus.hardening.config.FuzzTlaConfig;
import io.github.tlaplus.hardening.corpus.CheckerSet;
import io.github.tlaplus.hardening.corpus.CorpusStage;
import io.github.tlaplus.hardening.corpus.Technique;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ApalacheOnlySettingsTest {
    private static final Path PROJECT = Path.of("/work/fuzztla");

    private final InitialConfig initial = new InitialConfig(PROJECT.resolve("corpus"), Optional.of(PROJECT), 42);

    /** What init writes for a temporal mt corpus: Apalache among the checkers (ADR 0029 §3). */
    private FuzzTlaConfig temporalWithApalache() {
        return initial.withTemporalRules(initial.create(Technique.MT));
    }

    private static FuzzTlaConfig withCheckers(FuzzTlaConfig config, CheckerSet checkers) {
        return config.withWorkflow(config.workflow().withEnabledCheckers(checkers));
    }

    @Test
    void aCorpusThatChecksWithApalacheHasNoWarning() {
        assertEquals(List.of(), ApalacheOnlySettings.warnings(temporalWithApalache()));
    }

    /** corpus80: init's Apalache settings, then checkers edited to TLC and TLC-n. */
    @Test
    void droppingApalacheFromTheCheckersLeavesThreeWarnings() {
        var config = withCheckers(temporalWithApalache(), CheckerSet.of(CorpusStage.TLC, CorpusStage.TLCN));

        assertEquals(List.of(
                        "known_defects lists metamorphic-defects.toml, which quarantines inputs only for Apalache,"
                                + " but checkers omit apalache",
                        "known_defects lists temporal-apalache-defects.toml, which quarantines inputs only for"
                                + " Apalache, but checkers omit apalache",
                        "temporal_weights turns off SFDef and WFDef, which only Apalache needs, but checkers omit"
                                + " apalache"),
                ApalacheOnlySettings.warnings(config));
    }

    @Test
    void initWithoutApalacheHasNoWarning() {
        var mt = withCheckers(initial.create(Technique.MT), CheckerSet.of(CorpusStage.TLC, CorpusStage.TLCN));

        assertEquals(List.of(), ApalacheOnlySettings.warnings(initial.withTemporalRules(mt)));
    }
}
