package io.github.tlaplus.hardening.workflow.tlc;

import io.github.tlaplus.hardening.checker.CheckerFailure;
import java.util.Optional;

/** Extracts bounded human-readable context from TLC diagnostics. */
final class TlcFailureDetail {
    private static final String ERROR_PREFIX = "Error:";
    private TlcFailureDetail() {}

    static Optional<String> extract(String diagnostic) {
        return diagnostic.lines()
                .map(String::strip)
                .filter(line -> line.startsWith(ERROR_PREFIX))
                .map(line -> line.substring(ERROR_PREFIX.length()).strip())
                .filter(line -> !line.isBlank())
                .findFirst()
                .flatMap(CheckerFailure::normalizeDetail);
    }
}
