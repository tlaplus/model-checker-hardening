package io.github.tlaplus.hardening.workflow.apalache;

import io.github.tlaplus.hardening.checker.CheckerFailure;
import java.util.Optional;
import java.util.regex.Pattern;

/** Extracts bounded human-readable context from Apalache diagnostics. */
final class ApalacheFailureDetail {
    private static final Pattern ERROR_TIMESTAMP = Pattern.compile(
            "\\s+E@\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?$");
    private static final Pattern SOURCE_LOCATION = Pattern.compile("^\\[[^]]+]:\\s*");

    private ApalacheFailureDetail() {}

    static Optional<String> extract(String diagnostic) {
        return diagnostic.lines()
                .map(String::strip)
                .filter(line -> ERROR_TIMESTAMP.matcher(line).find())
                .map(line -> ERROR_TIMESTAMP.matcher(line).replaceFirst(""))
                .map(line -> SOURCE_LOCATION.matcher(line).replaceFirst(""))
                .findFirst()
                .flatMap(CheckerFailure::normalizeDetail);
    }
}
