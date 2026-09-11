package io.github.tlaplus.hardening.gen.engine;

import at.forsyte.apalache.tla.lir.*;
import io.github.tlaplus.hardening.gen.Generator;
import io.github.tlaplus.hardening.gen.IrGenerationConfig;
import io.github.tlaplus.hardening.gen.library.LibraryTypes;
import java.util.*;
import org.apalache_mc.tla.jir.NamedType;
import org.apalache_mc.tla.jir.TlaTypeSubstitution;
import org.apalache_mc.tla.jir.TlaTypes;

/** Byte-free feasibility analysis followed by bounded, deferred completion of a type term. */
record TypeInstantiation(TlaType1 template, List<Variable> variables) {
    record RowBounds(int minimumFields, int maximumFields, Set<String> forbiddenFields) {
        RowBounds { forbiddenFields = Set.copyOf(forbiddenFields); }

        RowBounds intersect(RowBounds other) {
            var forbidden = new HashSet<>(forbiddenFields);
            forbidden.addAll(other.forbiddenFields);
            return new RowBounds(Math.max(minimumFields, other.minimumFields),
                    Math.min(maximumFields, other.maximumFields), forbidden);
        }
    }

    record Variable(int id, int depth, Optional<RowBounds> row) {
        Variable intersect(Variable other) {
            if (row.isPresent() != other.row.isPresent()) {
                throw new IllegalArgumentException("type variable used as both row and value");
            }
            return new Variable(id, Math.min(depth, other.depth),
                    row.map(bounds -> bounds.intersect(other.row.orElseThrow())));
        }
    }

    /** Plans within the full configured type depth. */
    static Optional<TypeInstantiation> plan(TlaType1 template, IrGenerationConfig config) {
        return plan(template, config, config.expressions().maximumTypeDepth());
    }

    static Optional<TypeInstantiation> plan(TlaType1 template, IrGenerationConfig config, int depth) {
        if (!Collections.disjoint(LibraryTypes.categories(template), config.ignoredCategories())) {
            return Optional.empty();
        }
        var variables = new LinkedHashMap<Integer, Variable>();
        if (!inspect(template, config.expressions().maximumCollectionSize(), depth, variables)) {
            return Optional.empty();
        }
        if (variables.values().stream().flatMap(v -> v.row().stream())
                .anyMatch(row -> row.minimumFields() > row.maximumFields())) {
            return Optional.empty();
        }
        return Optional.of(new TypeInstantiation(template, List.copyOf(variables.values())));
    }

    /** First structural occurrence determines draw order, not Snowcat's variable numbers. */
    private static boolean inspect(TlaType1 type, int width, int depth, Map<Integer, Variable> variables) {
        if (type instanceof OperT1 operator) {
            return LibraryTypes.children(operator).stream().allMatch(t -> inspect(t, width, depth, variables));
        }
        if (depth < 0) return false;
        if (type instanceof VarT1 variable) {
            variables.merge(variable.no(), new Variable(variable.no(), depth, Optional.empty()),
                    Variable::intersect);
            return true;
        }
        if (type instanceof RowT1 row) {
            var fields = TlaTypes.rowFields(row);
            if (fields.size() > width) return false;
            if (!fields.values().stream().allMatch(t -> inspect(t, width, depth, variables))) return false;
            var tail = TlaTypes.rowTail(row);
            if (tail.isPresent()) {
                var id = tail.orElseThrow().no();
                var bounds = new RowBounds(fields.isEmpty() ? 1 : 0, width - fields.size(), fields.keySet());
                variables.merge(id, new Variable(id, depth, Optional.of(bounds)), Variable::intersect);
            } else if (fields.isEmpty()) {
                // The value decoder's record/variant constructors require a nonempty shape.
                return false;
            }
            return true;
        }
        if (type instanceof TupT1 tuple) {
            var elements = TlaTypes.tupleElements(tuple);
            if (elements.isEmpty() || elements.size() > width) return false;
        }
        return LibraryTypes.children(type).stream().allMatch(t -> inspect(t, width, depth - 1, variables));
    }

    Generator<TlaType1> generator(GenerationContext context, IrTypeGenFactory types) {
        return draw -> {
            var assigned = new LinkedHashMap<Integer, TlaType1>();
            for (var variable : variables) {
                if (variable.row().isEmpty()) {
                    assigned.put(variable.id(), draw.draw(types.valueType(variable.depth())).toTlaType());
                    continue;
                }
                var bounds = variable.row().orElseThrow();
                var fields = new ArrayList<NamedType>();
                while (fields.size() < bounds.maximumFields()
                        && (fields.size() < bounds.minimumFields() || draw.drawBoolean())) {
                    String name;
                    do { name = context.freshField(); } while (bounds.forbiddenFields().contains(name));
                    var type = draw.draw(types.valueType(variable.depth())).toTlaType();
                    fields.add(new NamedType(name, type));
                }
                assigned.put(variable.id(), TlaTypes.row(fields.toArray(NamedType[]::new)));
            }
            return TlaTypeSubstitution.of(assigned).applyFully(template);
        };
    }

    /** Alpha-normalization uses simultaneous substitution, not transitive substitution. */
    static TlaType1 canonical(TlaType1 type) {
        var variables = new LinkedHashMap<Integer, TlaType1>();
        gather(type, variables);
        return TlaTypeSubstitution.of(variables).applyOnce(type);
    }

    private static void gather(TlaType1 type, Map<Integer, TlaType1> variables) {
        if (type instanceof VarT1 variable) {
            variables.computeIfAbsent(variable.no(), ignored -> TlaTypes.typeVariable(variables.size()));
        } else LibraryTypes.children(type).forEach(child -> gather(child, variables));
    }
}
