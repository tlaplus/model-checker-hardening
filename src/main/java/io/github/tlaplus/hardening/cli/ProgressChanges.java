package io.github.tlaplus.hardening.cli;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Tracks changes for one refresh interval, using a caller-supplied monotonic clock. */
final class ProgressChanges {
    private static final long HIGHLIGHT_NANOS = Duration.ofSeconds(1).toNanos();
    private Map<RunMetric, RunValue> previous = Map.of();
    private final Map<RunMetric, Long> changedAt = new HashMap<>();

    void observe(Map<RunMetric, RunValue> values, long now) {
        values.forEach((key, value) -> {
            var old = previous.get(key);
            if (value.highlightsChanges() && old != null && !old.equals(value)) {
                changedAt.put(key, now);
            }
        });
        previous = Map.copyOf(values);
    }

    Set<RunMetric> highlighted(long now) {
        changedAt.entrySet().removeIf(entry -> now - entry.getValue() >= HIGHLIGHT_NANOS);
        return Set.copyOf(changedAt.keySet());
    }
}
