package io.github.tlaplus.hardening.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GenericPlaceholderTest {
    /** The tokens are the configuration format of [generic] arguments (ADR 0033 §3). */
    @Test
    void tokensAreTheDocumentedNames() {
        assertEquals(
                List.of("{dir}", "{module}", "{module_name}", "{config}", "{init}", "{next}", "{inv}", "{temporal}",
                        "{length}", "{ir}"),
                Arrays.stream(GenericPlaceholder.values()).map(GenericPlaceholder::token).toList());
    }

    @Test
    void reportsUnknownNamesInBracesOnly() {
        assertEquals(List.of(), GenericPlaceholder.unknownNames("--out={dir}/run-{length}"));
        assertEquals(List.of("cfg", "x"), GenericPlaceholder.unknownNames("{cfg}{module}{x}"));
        assertEquals(List.of(), GenericPlaceholder.unknownNames("{A} {1} {} {"));
    }

    /** A value replaces its placeholder within the argument, whatever it contains. */
    @Test
    void expandsWithinOneArgument() {
        var values = Map.of(GenericPlaceholder.DIR, "/tmp/a b$1", GenericPlaceholder.LENGTH, "7");
        assertEquals("--out=/tmp/a b$1/x-7 {A}", GenericPlaceholder.expand("--out={dir}/x-{length} {A}", values));
        assertEquals("", GenericPlaceholder.expand("{temporal}", Map.of(GenericPlaceholder.TEMPORAL, "")));
        assertThrows(IllegalArgumentException.class, () -> GenericPlaceholder.expand("{inv}", values));
    }
}
