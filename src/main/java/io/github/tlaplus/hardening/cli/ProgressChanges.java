package io.github.tlaplus.hardening.cli;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/** Tracks changes and their direction for one refresh interval, using a caller-supplied monotonic clock. */
final class ProgressChanges {
    private static final long HIGHLIGHT_NANOS = Duration.ofSeconds(1).toNanos();
    private Map<RunMetric, RunValue> previous = Map.of();
    private final Map<RunMetric, Change> changes = new HashMap<>();

    private record Change(long at, RunValue.Direction direction) {}

    void observe(Map<RunMetric, RunValue> values, long now) {
        values.forEach((key, value) -> {
            var old = previous.get(key);
            if (value.highlightsChanges() && old != null && !old.equals(value)) {
                changes.put(key, new Change(now, value.since(old)));
            }
        });
        previous = Map.copyOf(values);
    }

    /** Returns the direction of each metric that changed within the last interval. */
    Map<RunMetric, RunValue.Direction> highlighted(long now) {
        changes.entrySet().removeIf(entry -> now - entry.getValue().at() >= HIGHLIGHT_NANOS);
        var result = new HashMap<RunMetric, RunValue.Direction>();
        changes.forEach((key, change) -> result.put(key, change.direction()));
        return Map.copyOf(result);
    }
}
