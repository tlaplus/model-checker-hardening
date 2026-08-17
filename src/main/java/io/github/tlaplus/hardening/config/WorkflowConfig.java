package io.github.tlaplus.hardening.config;

import java.util.Objects;

/** Global and per-stage limits for the implemented fuzzing workflow. */
public record WorkflowConfig(
        int maximumEntries, StageConfig inputs, ParserStageConfig parser, TlcStageConfig tlc) {
    public WorkflowConfig {
        if (maximumEntries < 0) {
            throw new IllegalArgumentException("maximumEntries must be nonnegative");
        }
        Objects.requireNonNull(inputs, "inputs");
        Objects.requireNonNull(parser, "parser");
        Objects.requireNonNull(tlc, "tlc");
        if (inputs.maximumEntries() > maximumEntries) {
            throw new IllegalArgumentException(
                    "workflow.inputs.maximumEntries must not exceed workflow.maximumEntries");
        }
        if (parser.maximumEntries() > maximumEntries) {
            throw new IllegalArgumentException(
                    "workflow.parser.maximumEntries must not exceed workflow.maximumEntries");
        }
        if (tlc.maximumEntries() > maximumEntries) {
            throw new IllegalArgumentException(
                    "workflow.tlc.maximumEntries must not exceed workflow.maximumEntries");
        }
    }

    public static WorkflowConfig defaults() {
        return new WorkflowConfig(
                1_000, StageConfig.defaults(), ParserStageConfig.defaults(), TlcStageConfig.defaults());
    }
}
