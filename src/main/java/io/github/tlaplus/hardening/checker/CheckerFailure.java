package io.github.tlaplus.hardening.checker;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/** A stable checker failure code with optional bounded diagnostic context. */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public record CheckerFailure(CheckerFailureCode code, Optional<String> detail) {
    public static final int MAXIMUM_DETAIL_CHARACTERS = 80;

    private static final String ELLIPSIS = "…";
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    public CheckerFailure {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(detail, "detail");
        detail.ifPresent(value -> {
            if (value.isBlank()) {
                throw new IllegalArgumentException("checker failure detail must not be blank");
            }
            if (value.codePoints().anyMatch(CheckerFailure::isLineSeparator)) {
                throw new IllegalArgumentException(
                        "checker failure detail must be a single line");
            }
            if (value.codePointCount(0, value.length()) > MAXIMUM_DETAIL_CHARACTERS) {
                throw new IllegalArgumentException(
                        "checker failure detail must not exceed "
                                + MAXIMUM_DETAIL_CHARACTERS
                                + " characters");
            }
        });
    }

    private static boolean isLineSeparator(int codePoint) {
        return codePoint == '\n'
                || codePoint == '\r'
                || codePoint == 0x85
                || codePoint == 0x2028
                || codePoint == 0x2029;
    }

    public CheckerFailure(CheckerFailureCode code) {
        this(code, Optional.empty());
    }

    /** Normalizes human-readable context to the representation accepted by this record. */
    public static Optional<String> normalizeDetail(String detail) {
        var normalized = WHITESPACE.matcher(Objects.requireNonNull(detail, "detail").strip())
                .replaceAll(" ");
        if (normalized.isBlank()) {
            return Optional.empty();
        }
        if (normalized.codePointCount(0, normalized.length()) <= MAXIMUM_DETAIL_CHARACTERS) {
            return Optional.of(normalized);
        }
        var end = normalized.offsetByCodePoints(0, MAXIMUM_DETAIL_CHARACTERS - 1);
        return Optional.of(normalized.substring(0, end) + ELLIPSIS);
    }
}
