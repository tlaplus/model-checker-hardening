package io.github.tlaplus.hardening.config;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A placeholder in {@code [generic] arguments} (ADR 0033 §3), written {@code {name}}. The set is
 * closed: loading a configuration rejects any other {@code {name}}. The workflow supplies the
 * values of one invocation.
 */
public enum GenericPlaceholder {
    /** The invocation's working directory. */
    DIR("dir"),
    /** The module file, {@code {dir}/FuzzInput.tla}. */
    MODULE("module"),
    /** The module name, {@code FuzzInput}. */
    MODULE_NAME("module_name"),
    /** The configuration file, {@code {dir}/FuzzInput.cfg}. */
    CONFIG("config"),
    /** The initial predicate of the explored side. */
    INIT("init"),
    /** The next-state action of the explored side. */
    NEXT("next"),
    /** The invariants to check, separated by commas. */
    INV("inv"),
    /** The temporal property to check, empty when there is none. */
    TEMPORAL("temporal"),
    /** The number of steps a bounded checker unrolls. */
    LENGTH("length"),
    /** The module as Apalache's JSON IR, {@code {dir}/FuzzInput.json}. */
    IR("ir");

    /** A placeholder candidate: a lowercase name in braces. */
    private static final Pattern CANDIDATE = Pattern.compile("\\{([a-z_]+)\\}");

    private final String name;

    GenericPlaceholder(String name) {
        this.name = name;
    }

    /** Returns this placeholder as it appears in an argument. */
    public String token() {
        return "{" + name + "}";
    }

    /** Returns the placeholder written {@code {name}}, if any. */
    static Optional<GenericPlaceholder> fromName(String name) {
        return Arrays.stream(values()).filter(placeholder -> placeholder.name.equals(name)).findFirst();
    }

    /** Returns the names in braces in {@code argument} that are not placeholders, in order. */
    static List<String> unknownNames(String argument) {
        return CANDIDATE.matcher(Objects.requireNonNull(argument, "argument")).results()
                .map(match -> match.group(1))
                .filter(name -> fromName(name).isEmpty())
                .toList();
    }

    /**
     * Replaces every placeholder in {@code argument} by its value. The argument stays one argument,
     * whatever the values contain.
     *
     * @throws IllegalArgumentException if a placeholder of the argument has no value
     */
    public static String expand(String argument, Map<GenericPlaceholder, String> values) {
        Objects.requireNonNull(values, "values");
        return CANDIDATE.matcher(Objects.requireNonNull(argument, "argument")).replaceAll(match -> {
            var placeholder = fromName(match.group(1));
            if (placeholder.isEmpty()) {
                return Matcher.quoteReplacement(match.group());
            }
            var value = values.get(placeholder.get());
            if (value == null) {
                throw new IllegalArgumentException("no value for " + placeholder.get().token());
            }
            return Matcher.quoteReplacement(value);
        });
    }
}
