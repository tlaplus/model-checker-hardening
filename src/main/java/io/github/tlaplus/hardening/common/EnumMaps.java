package io.github.tlaplus.hardening.common;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;

/** Validated immutable copies of maps keyed by an enum, iterated in declaration order. */
public final class EnumMaps {
    private EnumMaps() {}

    /**
     * Copies {@code values}, requiring a value for every key in {@code required}. Keys outside
     * {@code required} are kept. {@code name} names the map in diagnostics.
     */
    public static <K extends Enum<K>, V> Map<K, V> requireKeys(
            Class<K> type, Map<K, ? extends V> values, Collection<K> required, String name) {
        Objects.requireNonNull(values, name);
        var copy = new EnumMap<K, V>(type);
        copy.putAll(values);
        Preconditions.require(!copy.containsValue(null), name + " must not contain null values");
        for (var key : required) {
            Preconditions.require(copy.containsKey(key), name + " is missing " + key);
        }
        return Collections.unmodifiableMap(copy);
    }

    /** Copies {@code values}, requiring a value for every constant of {@code type}. */
    public static <K extends Enum<K>, V> Map<K, V> requireAllKeys(
            Class<K> type, Map<K, ? extends V> values, String name) {
        return requireKeys(type, values, EnumSet.allOf(type), name);
    }
}
