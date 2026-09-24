package io.github.tlaplus.hardening.config;

import io.github.tlaplus.hardening.common.EnumMaps;
import io.github.tlaplus.hardening.common.Preconditions;
import io.github.tlaplus.hardening.corpus.CheckerSet;
import io.github.tlaplus.hardening.corpus.CorpusStage;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Global and per-stage limits for the implemented fuzzing workflow.
 *
 * <p>Checker limits are keyed by {@link CorpusStage}, matching the {@code [workflow.<stage>]}
 * tables of the configuration file, so a new checker is a new key rather than a new field. Every
 * checker has limits, whether or not {@code enabledCheckers} runs it.
 *
 * @param enabledCheckers the checkers a run feeds and aggregates (ADR 0016 §5)
 */
public record WorkflowConfig(
        int maximumEntries,
        InputStageConfig inputs,
        ParserStageConfig parser,
        Map<CorpusStage, CheckerStageConfig> checkers,
        CheckerSet enabledCheckers) {
    public WorkflowConfig {
        Preconditions.requireNonnegative(maximumEntries, "maximumEntries");
        Objects.requireNonNull(inputs, "inputs");
        Objects.requireNonNull(parser, "parser");
        Objects.requireNonNull(enabledCheckers, "enabledCheckers");
        checkers = EnumMaps.requireKeys(
                CorpusStage.class, checkers, CorpusStage.checkerBranches(), "checkers");

        requireWithinTotal(inputs.maximumEntries(), maximumEntries, "workflow.inputs");
        for (var stage : CorpusStage.capacityLimitedStages()) {
            requireWithinTotal(
                    limits(stage, parser, checkers).maximumEntries(),
                    maximumEntries,
                    "workflow." + stage.metadataName());
        }
    }

    /** A configuration that runs every checker, as every configuration did before ADR 0016. */
    public WorkflowConfig(
            int maximumEntries,
            InputStageConfig inputs,
            ParserStageConfig parser,
            Map<CorpusStage, CheckerStageConfig> checkers) {
        this(maximumEntries, inputs, parser, checkers, CheckerSet.ALL);
    }

    /** Returns this configuration with the given input stage. */
    public WorkflowConfig withInputs(InputStageConfig replacement) {
        return new WorkflowConfig(maximumEntries, replacement, parser, checkers, enabledCheckers);
    }

    /** Returns this configuration running only {@code replacement}. */
    public WorkflowConfig withEnabledCheckers(CheckerSet replacement) {
        return new WorkflowConfig(maximumEntries, inputs, parser, checkers, replacement);
    }

    public static WorkflowConfig defaults() {
        var checkers = new EnumMap<CorpusStage, CheckerStageConfig>(CorpusStage.class);
        for (var profile : CheckerProfile.values()) {
            checkers.put(profile.stage(), profile.defaults());
        }
        return new WorkflowConfig(
                1_000, InputStageConfig.defaults(), ParserStageConfig.defaults(), checkers);
    }

    /** Returns the limits of one checker stage. */
    public CheckerStageConfig checker(CorpusStage stage) {
        var checker = checkers.get(Objects.requireNonNull(stage, "stage"));
        Preconditions.require(checker != null, stage + " is not a checker stage");
        return checker;
    }

    /** Returns the limits of one stage that has a configuration table. */
    public StageLimits limits(CorpusStage stage) {
        return limits(stage, parser, checkers);
    }

    /** Returns the result-directory occupancy limit of one stage. */
    public int maximumEntries(CorpusStage stage) {
        return limits(stage).maximumEntries();
    }

    private static StageLimits limits(
            CorpusStage stage,
            ParserStageConfig parser,
            Map<CorpusStage, CheckerStageConfig> checkers) {
        if (Objects.requireNonNull(stage, "stage") == CorpusStage.PARSER) {
            return parser;
        }
        var checker = checkers.get(stage);
        Preconditions.require(checker != null, stage + " has no configured limits");
        return checker;
    }

    private static void requireWithinTotal(int stageEntries, int maximumEntries, String table) {
        Preconditions.require(stageEntries <= maximumEntries,
                table + ".maximumEntries must not exceed workflow.maximumEntries");
    }
}
