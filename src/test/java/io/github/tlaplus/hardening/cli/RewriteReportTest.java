package io.github.tlaplus.hardening.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.tlaplus.hardening.gen.rewrite.Orientation;
import io.github.tlaplus.hardening.gen.rewrite.Rewrite;
import java.util.List;
import org.apalache_mc.tla.jir.TlaTypedScopeUncheckedBuilder;
import org.junit.jupiter.api.Test;

class RewriteReportTest {
    @Test
    void rendersTheOrientationTheRulesAndBothSides() {
        var builder = new TlaTypedScopeUncheckedBuilder();
        var original = builder.plus(builder.integer(1), builder.integer(2));
        var rewritten = builder.plus(builder.plus(builder.integer(1), builder.integer(2)), builder.integer(0));
        var report = RewriteReport.render(
                new Rewrite<>(original, rewritten, Orientation.EXPLORE_REWRITE, List.of("PlusZero")));
        assertEquals(String.join(System.lineSeparator(),
                "explored: rewrite",
                "rules: PlusZero",
                "original:",
                "1 + 2",
                "rewrite:",
                "(1 + 2) + 0",
                ""), report);
    }
}
