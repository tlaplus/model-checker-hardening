package io.github.tlaplus.hardening.workflow.worker;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class BoundedTextOutputStreamTest {
    @Test
    void returnsCompleteOutputWithoutATruncationMarker() throws Exception {
        var output = new BoundedTextOutputStream(3, "diagnostic");

        output.write("abc".getBytes(StandardCharsets.UTF_8));

        assertEquals("abc", output.text());
    }

    @Test
    void retainsTheBoundedPrefixAndMarksTruncation() throws Exception {
        var output = new BoundedTextOutputStream(3, "diagnostic");

        output.write('a');
        output.write("bcde".getBytes(StandardCharsets.UTF_8));

        assertEquals(
                "abc" + System.lineSeparator() + "[diagnostic truncated]",
                output.text());
    }
}
