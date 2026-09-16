package io.github.tlaplus.hardening.cli;

import java.time.Duration;
import java.util.OptionalDouble;

/** Unrounded, typed values used both for formatting and change comparison. */
sealed interface RunValue {
    String format(Precision precision);

    /** Whether a change is worth highlighting; elapsed clocks change on every refresh. */
    default boolean highlightsChanges() {
        return true;
    }

    /** Whether the value is ordered, so a live screen reserves a column for its change marker. */
    default boolean directional() {
        return false;
    }

    /** How this value moved from an earlier, different value of the same metric. */
    default Direction since(RunValue previous) {
        return Direction.CHANGED;
    }

    enum Direction {
        UP,
        DOWN,
        /** Changed without an order, such as the workflow phase. */
        CHANGED
    }

    record Count(long value) implements RunValue {
        @Override
        public String format(Precision precision) {
            return precision == Precision.COMPACT ? HumanNumber.compact(value) : Long.toString(value);
        }

        @Override
        public boolean directional() {
            return true;
        }

        @Override
        public Direction since(RunValue previous) {
            return previous instanceof Count(var old) && value < old ? Direction.DOWN : Direction.UP;
        }
    }

    record Richness(OptionalDouble value) implements RunValue {
        static Richness of(long samples, double value) {
            return new Richness(samples == 0 ? OptionalDouble.empty() : OptionalDouble.of(value));
        }

        @Override
        public String format(Precision precision) {
            return value.isEmpty() ? "n/a" : HumanNumber.richness(value.getAsDouble(), precision);
        }

        @Override
        public boolean directional() {
            return true;
        }

        /** A first sample counts as a rise; losing all samples as a fall. */
        @Override
        public Direction since(RunValue previous) {
            if (value.isEmpty()) {
                return Direction.DOWN;
            }
            return previous instanceof Richness(var old) && old.isPresent()
                    && value.getAsDouble() < old.getAsDouble() ? Direction.DOWN : Direction.UP;
        }
    }

    /** The live phase or the final stop reason. */
    record Status(Enum<?> value) implements RunValue {
        @Override
        public String format(Precision precision) {
            return value.toString();
        }
    }

    record Elapsed(Duration value) implements RunValue {
        @Override
        public String format(Precision precision) {
            return precision == Precision.COMPACT ? HumanDuration.compact(value) : HumanDuration.format(value);
        }

        @Override
        public boolean highlightsChanges() {
            return false;
        }
    }
}
