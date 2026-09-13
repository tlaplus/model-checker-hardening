package io.github.tlaplus.hardening.gen.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tlaplus.hardening.gen.Draw;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The section layout is part of the module byte encoding, so its order and weights are pinned. */
class ModuleSectionTest {
    @Test
    void layoutOrderAndWeightsAreTheStoredByteEncoding() {
        var layout = new LinkedHashMap<String, Integer>();
        Arrays.stream(ModuleSection.values()).forEach(section -> layout.put(section.name(), section.weight()));

        assertEquals(
                Map.of("VARIABLES", 1, "AUXILIARY_OPERATORS", 2, "INVARIANT", 3,
                        "ACTION_OPERATORS", 3, "INIT", 2, "NEXT", 5),
                layout);
        assertEquals(
                List.of("VARIABLES", "AUXILIARY_OPERATORS", "INVARIANT", "ACTION_OPERATORS", "INIT", "NEXT"),
                List.copyOf(layout.keySet()));
    }

    @Test
    void sectionsAreContiguousProportionalAndOwnEveryByte() {
        var input = new byte[160];
        for (var index = 0; index < input.length; index++) {
            input[index] = (byte) index;
        }
        var draw = new Draw(input);

        var sections = ModuleSection.split(draw);

        assertTrue(draw.isEmpty());
        var expectedLengths = List.of(10, 20, 30, 30, 20, 50);
        var offset = 0;
        for (var section : ModuleSection.values()) {
            var slice = sections.get(section);
            var length = expectedLengths.get(section.ordinal());
            assertEquals(length, slice.remaining(), section.name());
            assertEquals(offset, slice.drawByte(), section.name());
            offset += length;
        }
    }

    @Test
    void theLastSectionTakesTheRoundingRemainder() {
        var sections = ModuleSection.split(new Draw(new byte[17]));

        var lengths = Arrays.stream(ModuleSection.values()).map(section -> sections.get(section).remaining()).toList();

        assertEquals(List.of(1, 2, 3, 3, 2, 6), lengths);
    }

    @Test
    void anEmptyInputGivesEverySectionAnEmptyCursor() {
        var sections = ModuleSection.split(new Draw(new byte[0]));

        for (var section : ModuleSection.values()) {
            assertTrue(sections.get(section).isEmpty(), section.name());
        }
    }
}
