package io.github.tlaplus.hardening.workflow.tlc;

import io.github.tlaplus.hardening.checker.ExplorationCount;
import io.github.tlaplus.hardening.checker.ExplorationMetrics;
import io.github.tlaplus.hardening.checker.ExplorationPhase;
import io.github.tlaplus.hardening.common.ArchRole;
import io.github.tlaplus.hardening.common.Key;
import io.github.tlaplus.hardening.common.Role;
import io.github.tlaplus.hardening.workflow.worker.StageOutcome;
import io.github.tlaplus.hardening.workflow.worker.ToolResult;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Reads the size of a completed search from the statistics that TLC's command line prints, for a
 * generic checker that follows TLC's output (ADR 0033 §5). TLC itself is measured in process by
 * {@link TlcExplorationMetrics}.
 */
@ArchRole(Role.POLICY)
@Key
public final class TlcCommandLineMetrics {
    /** TLC's statistics line; a run prints it with its progress reports and once at the end. */
    private static final Pattern STATISTICS = Pattern.compile(
            "^(\\d+) states? generated, (\\d+) distinct states? found, (\\d+) states? left on queue\\.$",
            Pattern.MULTILINE);

    private TlcCommandLineMetrics() {}

    /**
     * Returns {@code result} with the distinct-state count of a completed search: a pass whose last
     * statistics line leaves no state on the queue. Any other result, and output without the line,
     * is returned unchanged, so a tool that prints no statistics records no metrics.
     */
    public static ToolResult attach(ToolResult result) {
        Objects.requireNonNull(result, "result");
        if (result.outcome() != StageOutcome.PASS) {
            return result;
        }
        var matcher = STATISTICS.matcher(result.diagnostic());
        String distinct = null;
        String queued = null;
        while (matcher.find()) {
            distinct = matcher.group(2);
            queued = matcher.group(3);
        }
        if (distinct == null || Long.parseLong(queued) != 0) {
            return result;
        }
        return result.withMetrics(ExplorationMetrics.builder()
                .phase(ExplorationPhase.COMPLETE)
                .count(ExplorationCount.DISTINCT_STATES, Long.parseLong(distinct))
                .build());
    }
}
