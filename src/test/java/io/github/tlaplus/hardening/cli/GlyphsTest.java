package io.github.tlaplus.hardening.cli;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import org.jline.utils.AttributedString;
import org.junit.jupiter.api.Test;

class GlyphsTest {
    @Test
    void everyGlyphOccupiesOneColumnSoBothSetsShareTheLayout() {
        for (var glyphs : Glyphs.values()) {
            var all = glyphs.all();
            assertEquals(all.codePointCount(0, all.length()), new AttributedString(all).columnLength(), glyphs.name());
        }
    }

    @Test
    void junctionsFollowTheirConnectedDirections() {
        assertEquals("\u251c", Glyphs.UNICODE.junction(true, true, false, true));
        assertEquals("\u252c", Glyphs.UNICODE.junction(false, true, true, true));
        assertEquals("\u2518", Glyphs.UNICODE.junction(true, false, true, false));
        assertEquals("\u2502", Glyphs.UNICODE.vertical());
        assertEquals("\u2500\u2500\u25b6", Glyphs.UNICODE.arrowRight(3));
        assertEquals("+", Glyphs.ASCII.junction(true, true, false, true));
        assertEquals("-", Glyphs.ASCII.junction(false, false, true, true));
        assertEquals("|", Glyphs.ASCII.vertical());
        assertEquals("-->", Glyphs.ASCII.arrowRight(3));
    }

    @Test
    void detectionFallsBackToAsciiWhereArrowsCannotBeEncoded() {
        assertEquals(Glyphs.UNICODE, Glyphs.detect(StandardCharsets.UTF_8));
        assertEquals(Glyphs.ASCII, Glyphs.detect(StandardCharsets.US_ASCII));
        assertEquals(Glyphs.ASCII, Glyphs.detect(StandardCharsets.ISO_8859_1));
        assertEquals(Glyphs.ASCII, Glyphs.detect(null));
        assertEquals("+", Glyphs.ASCII.marker(RunValue.Direction.UP));
        assertEquals("-", Glyphs.ASCII.marker(RunValue.Direction.DOWN));
        assertEquals("\u2191", Glyphs.UNICODE.marker(RunValue.Direction.UP));
        assertEquals(" ", Glyphs.UNICODE.marker(RunValue.Direction.CHANGED));
    }
}
