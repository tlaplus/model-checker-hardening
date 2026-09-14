package io.github.tlaplus.hardening.signature;

import at.forsyte.apalache.tla.lir.LetInEx;
import at.forsyte.apalache.tla.lir.NameEx;
import at.forsyte.apalache.tla.lir.OperEx;
import at.forsyte.apalache.tla.lir.TlaEx;
import at.forsyte.apalache.tla.lir.TlaOperDecl;
import at.forsyte.apalache.tla.lir.ValEx;
import at.forsyte.apalache.tla.lir.oper.TlaOper;
import at.forsyte.apalache.tla.lir.values.TlaBool;
import at.forsyte.apalache.tla.lir.values.TlaInt;
import at.forsyte.apalache.tla.lir.values.TlaStr;
import java.math.BigInteger;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.apalache_mc.tla.jir.TlaExpressions;
import org.apalache_mc.tla.jir.TlaTypes;

/**
 * One pattern over the typed Apalache IR, as parsed by {@link PatternParser}.
 *
 * <p>A pattern is matched against a single expression whose labels have been removed, extending
 * the bindings of the alternative it belongs to. It never searches: finding the subexpression to
 * match is {@link IrTree}'s job.
 */
sealed interface IrPattern {
    boolean matches(TlaEx expression, Bindings bindings);

    /** {@code _}: any expression. */
    record Wildcard() implements IrPattern {
        @Override
        public boolean matches(TlaEx expression, Bindings bindings) {
            return true;
        }
    }

    /** {@code ?x}: any expression, equal to every other expression {@code ?x} matches. */
    record MetaVariable(String name) implements IrPattern {
        public MetaVariable {
            Objects.requireNonNull(name, "name");
        }

        @Override
        public boolean matches(TlaEx expression, Bindings bindings) {
            return bindings.bindExpression(name, expression);
        }
    }

    record IntegerLiteral(BigInteger value) implements IrPattern {
        public IntegerLiteral {
            Objects.requireNonNull(value, "value");
        }

        @Override
        public boolean matches(TlaEx expression, Bindings bindings) {
            return expression instanceof ValEx literal
                    && literal.value() instanceof TlaInt integer
                    && integer.value().bigInteger().equals(value);
        }
    }

    record StringLiteral(String value) implements IrPattern {
        public StringLiteral {
            Objects.requireNonNull(value, "value");
        }

        @Override
        public boolean matches(TlaEx expression, Bindings bindings) {
            return expression instanceof ValEx literal
                    && literal.value() instanceof TlaStr string
                    && string.value().equals(value);
        }
    }

    record BooleanLiteral(boolean value) implements IrPattern {
        @Override
        public boolean matches(TlaEx expression, Bindings bindings) {
            return expression instanceof ValEx literal
                    && literal.value() instanceof TlaBool bool
                    && bool.value() == value;
        }
    }

    record PredefinedSetLiteral(PredefinedSet set) implements IrPattern {
        public PredefinedSetLiteral {
            Objects.requireNonNull(set, "set");
        }

        @Override
        public boolean matches(TlaEx expression, Bindings bindings) {
            return expression instanceof ValEx literal && set.value().equals(literal.value());
        }
    }

    /** A variable, parameter, or definition name spelled exactly {@code name}. */
    record Name(String name) implements IrPattern {
        public Name {
            Objects.requireNonNull(name, "name");
        }

        @Override
        public boolean matches(TlaEx expression, Bindings bindings) {
            return expression instanceof NameEx reference && reference.name().equals(name);
        }
    }

    /**
     * {@code (OPER p1 ... pn)}: an application of {@code operator} whose arguments match in order.
     * An open-ended application, written with a trailing {@code ...}, accepts further arguments.
     */
    record Application(TlaOper operator, List<IrPattern> arguments, boolean openEnded)
            implements IrPattern {
        public Application {
            Objects.requireNonNull(operator, "operator");
            arguments = List.copyOf(arguments);
        }

        @Override
        public boolean matches(TlaEx expression, Bindings bindings) {
            if (!(expression instanceof OperEx application)
                    || !application.oper().name().equals(operator.name())) {
                return false;
            }
            var actual = TlaExpressions.arguments(application);
            if (openEnded ? actual.size() < arguments.size() : actual.size() != arguments.size()) {
                return false;
            }
            for (var index = 0; index < arguments.size(); index++) {
                if (!arguments.get(index).matches(IrTree.unlabeled(actual.get(index)), bindings)) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * {@code (.. p)}: an expression that matches {@code pattern} itself or has a subexpression that
     * does. The search follows operator arguments and {@code LET} bodies and declarations, skipping
     * labels, but not references to other definitions. Subexpressions are tried in pre-order, and
     * the first that matches determines the bindings.
     */
    record Descendant(IrPattern pattern) implements IrPattern {
        public Descendant {
            Objects.requireNonNull(pattern, "pattern");
        }

        @Override
        public boolean matches(TlaEx expression, Bindings bindings) {
            var attempt = bindings.copy();
            if (pattern.matches(expression, attempt)) {
                bindings.adopt(attempt);
                return true;
            }
            var children = switch (expression) {
                case OperEx application -> TlaExpressions.arguments(application);
                case LetInEx letIn -> Stream.concat(
                        TlaExpressions.localDeclarations(letIn).stream().map(TlaOperDecl::body),
                        Stream.of(letIn.body())).toList();
                default -> List.<TlaEx>of();
            };
            for (var child : children) {
                if (matches(IrTree.unlabeled(child), bindings)) {
                    return true;
                }
            }
            return false;
        }
    }

    /** {@code (& p1 ... pn)}: an expression that matches every pattern, binding left to right. */
    record Conjunction(List<IrPattern> patterns) implements IrPattern {
        public Conjunction {
            patterns = List.copyOf(patterns);
            if (patterns.isEmpty()) {
                throw new IllegalArgumentException("a conjunction takes at least one pattern");
            }
        }

        @Override
        public boolean matches(TlaEx expression, Bindings bindings) {
            for (var pattern : patterns) {
                if (!pattern.matches(expression, bindings)) {
                    return false;
                }
            }
            return true;
        }
    }

    /** {@code (: p "T")}: an expression that matches {@code pattern} and has a type matching T. */
    record Typed(IrPattern pattern, TypePattern type) implements IrPattern {
        public Typed {
            Objects.requireNonNull(pattern, "pattern");
            Objects.requireNonNull(type, "type");
        }

        @Override
        public boolean matches(TlaEx expression, Bindings bindings) {
            return type.matches(TlaTypes.typeOf(expression), bindings)
                    && pattern.matches(expression, bindings);
        }
    }
}
