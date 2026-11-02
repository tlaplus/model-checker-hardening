package io.github.tlaplus.hardening.workflow.rewrite;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Keeps the shipped rule modules proven: every rule of a module {@code M} has a theorem in
 * {@code MProofs.tla}. CI has no TLAPM here, so {@code make proofs} checks the theorems themselves.
 */
class RewriteLibraryProofsTest {
    private static final Path LIBRARY = Path.of("libraries/rewrites");
    private static final Pattern RULE = Pattern.compile("^([A-Z]\\w*)\\(.*==");

    @ParameterizedTest
    @ValueSource(strings = {"Rewrites", "TemporalRewrites"})
    void theProofsCoverEveryRule(String module) throws Exception {
        var proofsPath = LIBRARY.resolve(module + "Proofs.tla");
        var proofs = Files.readString(proofsPath);
        var rules = Files.readAllLines(LIBRARY.resolve(module + ".tla")).stream()
                .map(RULE::matcher)
                .filter(Matcher::find)
                .map(matcher -> matcher.group(1))
                .toList();
        assertFalse(rules.isEmpty());
        for (var rule : rules) {
            // The theorem's statement, up to its proof, must conclude the rule itself.
            var theorem = Pattern.compile("THEOREM " + rule + "Valid ==[^\\n]*(\\n  [^\\n]*)*?PROVE\\s+" + rule + "\\(");
            assertTrue(theorem.matcher(proofs).find(), "THEOREM " + rule + "Valid is missing from " + proofsPath);
        }
    }
}
