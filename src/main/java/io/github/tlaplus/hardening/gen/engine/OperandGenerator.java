package io.github.tlaplus.hardening.gen.engine;

import at.forsyte.apalache.tla.lir.TlaEx;
import at.forsyte.apalache.tla.lir.TlaType1;
import io.github.tlaplus.hardening.gen.Generator;
import io.github.tlaplus.hardening.gen.IrGenerationConfig;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Draws expressions for a rewrite that leaves an operand open (ADR 0017 §3): a fresh parameter is
 * drawn by the ordinary expression factory, at its type, in the lexical scope of the rewritten
 * node, and in the state-level context, so a fresh operand adds no prime.
 *
 * <p>One instance serves one rewritten module. Its name supply continues above every numeric
 * suffix the module already uses, so a binder of a fresh operand never captures or shadows a name
 * of the module.
 */
public final class OperandGenerator {
    /** What a name in scope denotes. */
    public enum Kind {
        /** A declared state variable. */
        STATE_VARIABLE(ScopedNameKind.STATE_VARIABLE),
        /** A variable bound by a quantifier, CHOOSE, function or set construct. */
        BINDER(ScopedNameKind.BINDER),
        /** An operator definition or a definition's formal parameter. */
        DEFINITION(ScopedNameKind.DEFINITION);

        private final ScopedNameKind scoped;

        Kind(ScopedNameKind scoped) {
            this.scoped = scoped;
        }
    }

    /** One typed name visible at the rewritten node. */
    public record Name(String name, TlaType1 type, Kind kind) {
        public Name {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(kind, "kind");
        }
    }

    private final GenerationContext context;
    private final IrTypeGenFactory types;
    private final IrExprGenFactory expressions;

    /**
     * @param namesInUse every name of the module the operands are drawn for
     * @throws IllegalArgumentException if the configured weights need more selection slots than
     *     one index can address
     */
    public OperandGenerator(IrGenerationConfig config, Collection<String> namesInUse) {
        Objects.requireNonNull(config, "config");
        ExpressionKindCatalog.requireAddressableSlots(config);
        CustomExpressionKind.requireUsableLibrary(config);
        context = new GenerationContext(config);
        context.reserveNames(Objects.requireNonNull(namesInUse, "namesInUse"));
        types = new IrTypeGenFactory(context);
        expressions = new IrExprGenFactory(context, types);
    }

    /**
     * Returns a generator of one expression of the concrete {@code type}. A name whose type the
     * generator cannot express is left out of scope.
     */
    public Generator<TlaEx> operand(TlaType1 type, List<Name> scope) {
        var requested = ImportedTypes.from(Objects.requireNonNull(type, "type"));
        var visible = new ArrayList<ScopedName>();
        for (var name : Objects.requireNonNull(scope, "scope")) {
            try {
                visible.add(new ScopedName(name.name(), ImportedTypes.from(name.type()), name.kind().scoped));
            } catch (IllegalArgumentException unsupported) {
                // A type variable or an open row: nothing drawn at a concrete type can use the name.
            }
        }
        var depth = context.config().expressions().maximumExpressionDepth();
        return context.withBindings(visible, context.withLevel(LevelContext.STATE,
                context.withFreshNodeBudget(expressions.mkGen(requested, depth))));
    }

    /**
     * Returns a generator that instantiates every type variable of {@code template} within the
     * configured limits, as a custom operator's residual variables are drawn, or empty when no
     * instantiation fits them.
     */
    public Optional<Generator<TlaType1>> instantiate(TlaType1 template) {
        return TypeInstantiation.plan(Objects.requireNonNull(template, "template"), context.config())
                .map(plan -> plan.generator(context, types));
    }
}
