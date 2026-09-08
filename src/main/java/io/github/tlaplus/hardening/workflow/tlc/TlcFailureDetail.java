package io.github.tlaplus.hardening.workflow.tlc;

import io.github.tlaplus.hardening.checker.CheckerFailure;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Extracts bounded human-readable context from TLC diagnostics.
 *
 * <p>TLC often reports the failure it reached inside a message about how it reached it, so the
 * first line of its output names the wrapper rather than the failure. The stored detail is one
 * bounded line and is what triage classifies, so this extracts the innermost reported failure:
 * the first error block with every {@link Wrapper} removed.
 */
final class TlcFailureDetail {
    private static final String ERROR_PREFIX = "Error:";

    /** Lines that end the reported error and begin TLC's trailing context. */
    private static final Pattern BLOCK_END =
            Pattern.compile("^(?:Error:|While working on|State \\d+:|The behavior up to).*");

    private TlcFailureDetail() {}

    static Optional<String> extract(String diagnostic) {
        return errorBlock(diagnostic)
                .map(TlcFailureDetail::unwrap)
                .map(block -> block.lines().findFirst().orElse(""))
                .flatMap(CheckerFailure::normalizeDetail);
    }

    /**
     * Returns the first error TLC reported, as the {@code Error:} line and the lines continuing
     * it. A block with no text of its own is skipped: TLC emits a bare {@code Error:} before the
     * message it introduces.
     */
    private static Optional<String> errorBlock(String diagnostic) {
        var lines = diagnostic.lines().map(String::strip).toList();
        for (var start = 0; start < lines.size(); start++) {
            if (!lines.get(start).startsWith(ERROR_PREFIX)) {
                continue;
            }
            var body = new ArrayList<String>();
            addIfPresent(body, lines.get(start).substring(ERROR_PREFIX.length()).strip());
            for (var line : lines.subList(start + 1, lines.size())) {
                if (line.isBlank() || BLOCK_END.matcher(line).matches()) {
                    break;
                }
                addIfPresent(body, line);
            }
            if (!body.isEmpty()) {
                return Optional.of(String.join("\n", body));
            }
        }
        return Optional.empty();
    }

    private static void addIfPresent(List<String> body, String line) {
        if (!line.isBlank()) {
            body.add(line);
        }
    }

    /** Removes every wrapper, innermost last: a wrapped failure can itself be wrapped. */
    private static String unwrap(String block) {
        var text = block;
        for (var unwrapped = true; unwrapped; ) {
            unwrapped = false;
            for (var wrapper : Wrapper.values()) {
                var wrapped = wrapper.unwrap(text);
                if (wrapped.isPresent()) {
                    text = wrapped.get();
                    unwrapped = true;
                }
            }
        }
        return text;
    }

    /**
     * A TLC message that reports another failure rather than one of its own.
     *
     * <p>Each constant matches its own preamble; what follows the match is the failure to
     * classify. Adding a wrapper is adding a constant.
     */
    private enum Wrapper {
        /** An error raised while evaluating the invariant. */
        INVARIANT("Evaluating invariant \\w+ failed\\.\\s*"),
        /** An error that escaped as a generic exception; see findings/TLC/tlc-001.md. */
        UNEXPECTED_EXCEPTION(
                "TLC threw an unexpected exception\\..*?The exception was an? \\S+\\s*:?\\s*"),
        /** An error raised inside an operator that a Java module override implements. */
        JAVA_OVERRIDE(
                "Attempted to apply the operator overridden by the Java method\\b.*?"
                        + "but it produced the following error:\\s*");

        private final Pattern preamble;

        Wrapper(String preamble) {
            this.preamble = Pattern.compile("\\A" + preamble, Pattern.DOTALL);
        }

        /** Returns the wrapped failure, or empty when this wrapper does not introduce one. */
        Optional<String> unwrap(String block) {
            var matcher = preamble.matcher(block);
            if (!matcher.lookingAt()) {
                return Optional.empty();
            }
            var wrapped = block.substring(matcher.end());
            return wrapped.isBlank() ? Optional.empty() : Optional.of(wrapped);
        }
    }
}
