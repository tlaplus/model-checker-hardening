package io.github.tlaplus.hardening.config;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Registers typed keys in document order, then snapshots them as an immutable schema table. */
final class ConfigTableBuilder<T> {
    private final String path;
    private final Function<FuzzTlaConfig, T> value;
    private final List<ConfigSchema.Key<?>> keys;

    ConfigTableBuilder(String path, Function<FuzzTlaConfig, T> value) {
        this(path, value, new ArrayList<>());
    }

    private ConfigTableBuilder(
            String path, Function<FuzzTlaConfig, T> value, List<ConfigSchema.Key<?>> keys) {
        this.path = path;
        this.value = value;
        this.keys = keys;
    }

    /** A narrower configuration view registers into the same ordered table. */
    <U> ConfigTableBuilder<U> project(Function<T, U> projection) {
        return new ConfigTableBuilder<>(path, value.andThen(projection), keys);
    }

    <U> ConfigSchema.Key<U> key(
            String name, ConfigValueType<U> type, Function<T, U> field, String... documentation) {
        var key = new ConfigSchema.Key<>(path, name, type, List.of(documentation), value.andThen(field));
        keys.add(key);
        return key;
    }

    ConfigSchema.Key<Integer> integer(String name, Function<T, Integer> field, String... documentation) {
        return key(name, ConfigValueType.INTEGER, field, documentation);
    }

    ConfigSchema.Table build() {
        return new ConfigSchema.Table(path, keys);
    }
}
