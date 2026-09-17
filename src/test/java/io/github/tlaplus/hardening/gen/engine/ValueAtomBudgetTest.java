package io.github.tlaplus.hardening.gen.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.tlaplus.hardening.gen.Draw;
import io.github.tlaplus.hardening.gen.Generator;
import org.junit.jupiter.api.Test;

class ValueAtomBudgetTest {
    @Test
    void nestedScopesDivideAndRestore() {
        var budget = new ValueAtomBudget(64);
        Generator<Integer> inner = budget.within(3, draw -> budget.current());
        Generator<int[]> outer = budget.within(4, draw -> new int[] {budget.current(), draw.draw(inner)});

        var observed = new Draw(new byte[0]).draw(outer);

        assertEquals(16, observed[0]);
        assertEquals(5, observed[1]);
        assertEquals(64, budget.current());
    }

    @Test
    void aScopeNeverDropsBelowOneAndRestoresOnFailure() {
        var budget = new ValueAtomBudget(2);
        Generator<Integer> starved = budget.within(8, draw -> budget.current());
        assertEquals(1, (int) new Draw(new byte[0]).draw(starved));

        Generator<Integer> failing = budget.within(2, draw -> {
            throw new IllegalStateException("rejected");
        });
        assertThrows(IllegalStateException.class, () -> new Draw(new byte[0]).draw(failing));
        assertEquals(2, budget.current());
    }

    @Test
    void aFreshScopeRestoresTheFullBudget() {
        var budget = new ValueAtomBudget(12);
        Generator<Integer> fresh = budget.fresh(draw -> budget.current());
        Generator<Integer> nested = budget.within(6, fresh);

        assertEquals(12, (int) new Draw(new byte[0]).draw(nested));
    }
}
