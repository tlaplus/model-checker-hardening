package io.github.tlaplus.hardening.gen.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.tlaplus.hardening.gen.Draw;
import io.github.tlaplus.hardening.gen.InputRejectedException;
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
    void bindingChoiceIsDeferredAndDoesNotInventAFreshAlternative() {
        var scopedContext = context();
        var scopedDraw = new Draw(new byte[] {99});
        var binding = new ScopedName("bound", PrimitiveType.BOOL);
        var choice = scopedContext.chooseBinding(PrimitiveType.BOOL);

        scopedDraw.draw(scopedContext.withBinding(binding, bodyDraw -> {
            assertEquals(binding, bodyDraw.draw(choice));
            return null;
        }));

        assertEquals(1, scopedDraw.remaining());
        assertEquals("next0", scopedContext.fresh("next"));
    }

    @Test
    void nameChoiceCanReachEveryCompatibleCandidate() {
        var bindings = List.of(
                new ScopedName("first", PrimitiveType.BOOL),
                new ScopedName("second", PrimitiveType.BOOL));

        assertEquals("first", chooseFrom(bindings, 0));
        assertEquals("second", chooseFrom(bindings, 1));
        assertEquals("first", chooseFrom(bindings, 2));
    }

    @Test
    void missingCompatibleBindingsRejectWithoutConsumingAChoiceByte() {
        var context = context();
        var draw = new Draw(new byte[] {42});

        assertThrows(
                InputRejectedException.class,
                () -> draw.draw(context.chooseBinding(PrimitiveType.INT)));
        assertEquals(1, draw.remaining());
        assertEquals("next0", context.fresh("next"));
    }

    @Test
    void scopedGeneratorsAreDeferredAndRestoreScopeAfterFailure() {
        var context = context();
        var binding = new ScopedName("bound", PrimitiveType.BOOL);
        var scoped = context.withBinding(binding, draw -> {
            assertEquals(binding, draw.draw(context.chooseBinding(PrimitiveType.BOOL)));
            throw new IllegalStateException("failure");
        });

        assertThrows(
                InputRejectedException.class,
                () -> new Draw(new byte[0]).draw(
                        context.chooseBinding(PrimitiveType.BOOL)));
        assertThrows(
                IllegalStateException.class,
                () -> new Draw(new byte[] {0}).draw(scoped));
        assertThrows(
                InputRejectedException.class,
                () -> new Draw(new byte[0]).draw(
                        context.chooseBinding(PrimitiveType.BOOL)));
    }

    @Test
    void specializedQueriesRespectRolesTypesAndShadowing() {
        var scope = new NameScope();
        var state = ScopedName.stateVariable("shared", PrimitiveType.INT);
        var operatorType = new OperatorType(
                List.of(PrimitiveType.INT), PrimitiveType.BOOL);
        var operator = new ScopedName("Predicate", operatorType);
        var shadow = new ScopedName("shared", PrimitiveType.BOOL);

        scope.withBindings(List.of(state, operator), () -> {
            assertEquals(List.of(state), scope.stateVariables());
            assertEquals(List.of(operator), scope.operatorsReturning(PrimitiveType.BOOL));
            assertEquals(List.of(), scope.operatorsReturning(PrimitiveType.INT));

            scope.withBinding(shadow, () -> {
                assertEquals(List.of(), scope.stateVariables());
                assertEquals(List.of(shadow), scope.matching(PrimitiveType.BOOL));
                assertEquals(List.of(), scope.matching(PrimitiveType.INT));
                return null;
            });
            return null;
        });
    }

    private GenerationContext context() {
        return new GenerationContext(IrGenerationConfig.defaults());
    }

    private String chooseFrom(List<ScopedName> bindings, int input) {
        var context = context();
        var draw = new Draw(new byte[] {(byte) input});
        return draw.draw(context.withBindings(
                        bindings, context.chooseBinding(PrimitiveType.BOOL)))
                .name();
    }
}
