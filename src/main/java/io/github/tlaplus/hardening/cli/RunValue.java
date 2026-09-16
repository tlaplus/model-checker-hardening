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

    record Count(long value) implements RunValue {
        @Override
        public String format(Precision precision) {
            return precision == Precision.COMPACT ? HumanNumber.compact(value) : Long.toString(value);
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
