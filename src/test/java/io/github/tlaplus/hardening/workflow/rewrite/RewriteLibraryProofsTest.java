package io.github.tlaplus.hardening.workflow.rewrite;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Keeps the shipped rewrite rules proven: every rule has a theorem in RewritesProofs.tla. CI has
 * no TLAPM here, so {@code make proofs} checks the theorems themselves.
 */
class RewriteLibraryProofsTest {
    private static final Path LIBRARY = Path.of("libraries/rewrites");
    private static final Path RULES = LIBRARY.resolve("Rewrites.tla");
    private static final Path PROOFS = LIBRARY.resolve("RewritesProofs.tla");
    private static final Pattern RULE = Pattern.compile("^([A-Z]\\w*)\\(.*==");

    @Test
    void theProofsCoverEveryRule() throws Exception {
        var proofs = Files.readString(PROOFS);
        var rules = Files.readAllLines(RULES).stream()
                .map(RULE::matcher)
                .filter(Matcher::find)
                .map(matcher -> matcher.group(1))
                .toList();
        assertFalse(rules.isEmpty());
        for (var rule : rules) {
            // The theorem's statement, up to its proof, must conclude the rule itself.
            var theorem = Pattern.compile("THEOREM " + rule + "Valid ==[^\\n]*(\\n  [^\\n]*)*?PROVE\\s+" + rule + "\\(");
            assertTrue(theorem.matcher(proofs).find(), "THEOREM " + rule + "Valid is missing from " + PROOFS);
        }
    }
}
