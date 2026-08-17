package io.github.tlaplus.hardening.workflow.tlc;

import io.github.tlaplus.hardening.checker.CheckerFailure;
import java.util.Optional;
import java.util.regex.Pattern;

/** Extracts bounded human-readable context from TLC diagnostics. */
final class TlcFailureDetail {
    private static final String ERROR_PREFIX = "Error:";
    private static final String ELLIPSIS = "…";
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private TlcFailureDetail() {}

    static Optional<String> extract(String diagnostic) {
        return diagnostic.lines()
                .map(String::strip)
                .filter(line -> line.startsWith(ERROR_PREFIX))
                .map(line -> line.substring(ERROR_PREFIX.length()).strip())
                .map(line -> WHITESPACE.matcher(line).replaceAll(" "))
                .filter(line -> !line.isBlank())
                .findFirst()
                .map(TlcFailureDetail::truncate);
    }

    private static String truncate(String detail) {
        var maximum = CheckerFailure.MAXIMUM_DETAIL_CHARACTERS;
        if (detail.codePointCount(0, detail.length()) <= maximum) {
            return detail;
        }
        var end = detail.offsetByCodePoints(0, maximum - 1);
        return detail.substring(0, end) + ELLIPSIS;
    }
}
