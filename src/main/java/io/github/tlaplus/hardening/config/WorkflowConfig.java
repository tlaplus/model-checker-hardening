package io.github.tlaplus.hardening.config;

import io.github.tlaplus.hardening.common.Preconditions;
import io.github.tlaplus.hardening.corpus.CorpusStage;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Global and per-stage limits for the implemented fuzzing workflow.
 *
 * <p>Checker limits are keyed by {@link CorpusStage}, matching the {@code [workflow.<stage>]}
 * tables of the configuration file, so a new checker is a new key rather than a new field.
 */
public record WorkflowConfig(
        int maximumEntries,
        InputStageConfig inputs,
        ParserStageConfig parser,
        Map<CorpusStage, CheckerStageConfig> checkers) {
    public WorkflowConfig {
        Preconditions.requireNonnegative(maximumEntries, "maximumEntries");
        Objects.requireNonNull(inputs, "inputs");
        Objects.requireNonNull(parser, "parser");
        Objects.requireNonNull(checkers, "checkers");
        var copy = new EnumMap<CorpusStage, CheckerStageConfig>(CorpusStage.class);
        copy.putAll(checkers);
        for (var checker : CorpusStage.checkerBranches()) {
            Preconditions.require(copy.containsKey(checker), "checkers is missing stage " + checker);
        }
        checkers = Map.copyOf(copy);

        requireWithinTotal(inputs.maximumEntries(), maximumEntries, "workflow.inputs");
        requireWithinTotal(parser.maximumEntries(), maximumEntries, "workflow.parser");
        for (var entry : checkers.entrySet()) {
            requireWithinTotal(
                    entry.getValue().maximumEntries(),
                    maximumEntries,
                    "workflow." + entry.getKey().metadataName());
        }
    }

    public static WorkflowConfig defaults() {
        return new WorkflowConfig(
                1_000,
                InputStageConfig.defaults(),
                ParserStageConfig.defaults(),
                Map.of(
                        CorpusStage.TLC, CheckerStageConfig.tlcDefaults(),
                        CorpusStage.APALACHE, CheckerStageConfig.apalacheDefaults()));
    }

    /** Returns the limits of one checker stage. */
    public CheckerStageConfig checker(CorpusStage stage) {
        var checker = checkers.get(Objects.requireNonNull(stage, "stage"));
        Preconditions.require(checker != null, stage + " is not a checker stage");
        return checker;
    }

    /** Returns the result-directory occupancy limit of one stage. */
    public int maximumEntries(CorpusStage stage) {
        return stage == CorpusStage.PARSER
                ? parser.maximumEntries()
                : checker(stage).maximumEntries();
    }

    private static void requireWithinTotal(int stageEntries, int maximumEntries, String table) {
        Preconditions.require(stageEntries <= maximumEntries,
                table + ".maximumEntries must not exceed workflow.maximumEntries");
    }
}
