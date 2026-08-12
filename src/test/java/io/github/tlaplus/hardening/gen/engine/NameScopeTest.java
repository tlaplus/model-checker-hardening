package io.github.tlaplus.hardening.gen.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.tlaplus.hardening.gen.Draw;
import io.github.tlaplus.hardening.gen.IrGenerationConfig;
import java.util.List;
import org.junit.jupiter.api.Test;

class NameScopeTest {
    @Test
    void nestedScopesAreTypedShadowAwareAndRestored() {
        var scope = new NameScope();
        var outer = new ScopedName("value", PrimitiveType.BOOL);
        var inner = List.of(
                new ScopedName("inner", PrimitiveType.BOOL),
                new ScopedName("value", PrimitiveType.INT));

        scope.withBinding(outer, () -> {
            assertEquals(List.of(outer), scope.matching(PrimitiveType.BOOL));
            scope.withBindings(inner, () -> {
                assertEquals(List.of(inner.get(0)), scope.matching(PrimitiveType.BOOL));
                assertEquals(List.of(inner.get(1)), scope.matching(PrimitiveType.INT));
                return null;
            });
            assertEquals(List.of(outer), scope.matching(PrimitiveType.BOOL));
            return null;
        });

        assertEquals(List.of(), scope.matching(PrimitiveType.BOOL));
    }

    @Test
    void scopeIsRestoredWhenGenerationThrows() {
        var scope = new NameScope();

        assertThrows(IllegalStateException.class, () -> scope.withBinding(
                new ScopedName("temporary", PrimitiveType.BOOL),
                () -> {
                    throw new IllegalStateException("failure");
                }));

        assertEquals(List.of(), scope.matching(PrimitiveType.BOOL));
    }

    @Test
    void nameChoiceIncludesScopedNamesAndADeferredFreshAlternative() {
        var scopedContext = context();
        var scopedDraw = new Draw(new byte[] {0});
        var binding = new ScopedName("bound", PrimitiveType.BOOL);
        scopedDraw.draw(scopedContext.withBinding(binding, bodyDraw -> {
            assertEquals(
                    "bound",
                    bodyDraw.draw(scopedContext.chooseName(PrimitiveType.BOOL, "free")));
            return null;
        }));
        assertEquals("next0", scopedContext.fresh("next"));

        var freshContext = context();
        var freshDraw = new Draw(new byte[] {1});
        freshDraw.draw(freshContext.withBinding(binding, bodyDraw -> {
            assertEquals(
                    "free0",
                    bodyDraw.draw(freshContext.chooseName(PrimitiveType.BOOL, "free")));
            return null;
        }));
        assertEquals("next1", freshContext.fresh("next"));
    }

    @Test
    void nameChoiceCanReachEveryCompatibleCandidate() {
        var bindings = List.of(
                new ScopedName("first", PrimitiveType.BOOL),
                new ScopedName("second", PrimitiveType.BOOL));

        assertEquals("first", chooseFrom(bindings, 0));
        assertEquals("second", chooseFrom(bindings, 1));
        assertEquals("free0", chooseFrom(bindings, 2));
    }

    @Test
    void missingCompatibleNamesConsumeNoChoiceByte() {
        var context = context();
        var draw = new Draw(new byte[] {42});

        assertEquals("int0", draw.draw(context.chooseName(PrimitiveType.INT, "int")));
        assertEquals(1, draw.remaining());
    }

    @Test
    void scopedGeneratorsAreDeferredAndRestoreScopeAfterFailure() {
        var context = context();
        var binding = new ScopedName("bound", PrimitiveType.BOOL);
        var scoped = context.withBinding(binding, draw -> {
            assertEquals(
                    "bound",
                    draw.draw(context.chooseName(PrimitiveType.BOOL, "free")));
            throw new IllegalStateException("failure");
        });

        assertEquals(
                "free0",
                new Draw(new byte[0]).draw(
                        context.chooseName(PrimitiveType.BOOL, "free")));
        assertThrows(
                IllegalStateException.class,
                () -> new Draw(new byte[] {0}).draw(scoped));
        assertEquals(
                "free1",
                new Draw(new byte[0]).draw(
                        context.chooseName(PrimitiveType.BOOL, "free")));
    }

    private GenerationContext context() {
        return new GenerationContext(IrGenerationConfig.defaults());
    }

    private String chooseFrom(List<ScopedName> bindings, int input) {
        var context = context();
        var draw = new Draw(new byte[] {(byte) input});
        return draw.draw(context.withBindings(
                bindings, context.chooseName(PrimitiveType.BOOL, "free")));
    }
}
