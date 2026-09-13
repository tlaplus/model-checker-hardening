package io.github.tlaplus.hardening.gen.engine;

import at.forsyte.apalache.tla.lir.TlaEx;
import io.github.tlaplus.hardening.gen.ExpressionCategory;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import org.apalache_mc.tla.jir.TlaTypedScopeUncheckedBuilder;

/**
 * Reads, updates and domains of records, tuples and sequences.
 *
 * <p>TLA+ treats these values as functions, and the builder applies, updates and takes the domain
 * of each through the same operators as a function; Apalache's builder calls the types those
 * operators accept applicative. Without these forms a generated record or tuple could be built and
 * compared but never read, and a sequence could only be read at its head, so the checker code that
 * projects a component was out of reach.
 *
 * <p>Each constant pairs an {@link ApplicativeType} with an {@link Operation}; the facts that differ
 * between records, tuples and sequences live on {@code ApplicativeType}. Functions are applicative
 * too, but keep their own forms in the general, set and other families.
 */
public enum ApplicativeExpressionKind implements ExpressionKind {
    RECORD_ACCESS(ApplicativeType.RECORD, Operation.ACCESS),
    TUPLE_ACCESS(ApplicativeType.TUPLE, Operation.ACCESS),
    SEQUENCE_ACCESS(ApplicativeType.SEQUENCE, Operation.ACCESS),
    RECORD_EXCEPT(ApplicativeType.RECORD, Operation.EXCEPT),
    TUPLE_EXCEPT(ApplicativeType.TUPLE, Operation.EXCEPT),
    SEQUENCE_EXCEPT(ApplicativeType.SEQUENCE, Operation.EXCEPT),
    RECORD_DOMAIN(ApplicativeType.RECORD, Operation.DOMAIN),
    TUPLE_DOMAIN(ApplicativeType.TUPLE, Operation.DOMAIN),
    SEQUENCE_DOMAIN(ApplicativeType.SEQUENCE, Operation.DOMAIN);

    /** A value type that TLA+ applies as a function to a component index. */
    enum ApplicativeType {
        /** Applied to a field name; its domain is the set of field names. */
        RECORD(ExpressionCategory.RECORD, PrimitiveType.STRING) {
            @Override
            boolean isInstance(IrType type) {
                return type instanceof RecordType;
            }

            @Override
            IrType of(List<IrType> components, GenerationContext context) {
                return new RecordType(components.stream()
                        .map(component -> new Field(context.freshField(), component))
                        .toList());
            }

            @Override
            Optional<TlaEx> literalIndex(
                    TlaTypedScopeUncheckedBuilder builder, IrType applied, int position) {
                return Optional.of(builder.str(((RecordType) applied).fields().get(position).name()));
            }
        },
        /** Applied to a literal position; its domain is {@code 1..n}. */
        TUPLE(ExpressionCategory.TUPLE, PrimitiveType.INT) {
            @Override
            boolean isInstance(IrType type) {
                return type instanceof TupleType;
            }

            @Override
            IrType of(List<IrType> components, GenerationContext context) {
                return new TupleType(components);
            }

            @Override
            Optional<TlaEx> literalIndex(
                    TlaTypedScopeUncheckedBuilder builder, IrType applied, int position) {
                return Optional.of(builder.integer(BigInteger.valueOf(position + 1L)));
            }
        },
        /**
         * Applied to an arbitrary integer, which may lie outside {@code 1..Len(s)}: like
         * {@code Head}, a sequence read is partial.
         */
        SEQUENCE(ExpressionCategory.SEQUENCE, PrimitiveType.INT) {
            @Override
            boolean isInstance(IrType type) {
                return type instanceof SequenceType;
            }

            @Override
            int maximumOtherComponents(int maximumCollectionSize) {
                return 0;
            }

            @Override
            IrType of(List<IrType> components, GenerationContext context) {
                return new SequenceType(components.getFirst());
            }

            @Override
            Optional<TlaEx> literalIndex(
                    TlaTypedScopeUncheckedBuilder builder, IrType applied, int position) {
                return Optional.empty();
            }
        };

        private final ExpressionCategory category;
        private final PrimitiveType domainElement;

        ApplicativeType(ExpressionCategory category, PrimitiveType domainElement) {
            this.category = category;
            this.domainElement = domainElement;
        }

        ExpressionCategory category() {
            return category;
        }

        /** Returns the element type of this type's {@code DOMAIN}. */
        PrimitiveType domainElement() {
            return domainElement;
        }

        abstract boolean isInstance(IrType type);

        /**
         * Returns how many components a fresh value of this type may hold beside the one it must
         * contain.
         * A sequence has exactly one component type.
         */
        int maximumOtherComponents(int maximumCollectionSize) {
            return maximumCollectionSize - 1;
        }

        /** Builds a type of this kind with the given component types, in order. */
        abstract IrType of(List<IrType> components, GenerationContext context);

        /**
         * Returns the literal index of a component, or empty when the index is an arbitrary
         * expression. The builder types an application of a record or tuple only when its index is
         * a literal, because the result type depends on which component it names.
         */
        abstract Optional<TlaEx> literalIndex(
                TlaTypedScopeUncheckedBuilder builder, IrType applied, int position);
    }

    /** What a form does with an applicative value. */
    enum Operation {
        /** {@code c[i]}: a component of the requested type. */
        ACCESS,
        /** {@code [c EXCEPT ![i] = e]}: a value of the requested type. */
        EXCEPT,
        /** {@code DOMAIN c}: a set of component indices. */
        DOMAIN
    }

    private final ApplicativeType applicativeType;
    private final Operation operation;
    private final Categories categories;

    ApplicativeExpressionKind(ApplicativeType applicativeType, Operation operation) {
        this.applicativeType = applicativeType;
        this.operation = operation;
        categories = operation == Operation.DOMAIN
                ? new Categories(applicativeType.category(), ExpressionCategory.SET)
                : new Categories(applicativeType.category());
    }

    ApplicativeType applicativeType() {
        return applicativeType;
    }

    Operation operation() {
        return operation;
    }

    @Override
    public boolean isTypeApplicable(IrType type) {
        return switch (operation) {
            case ACCESS -> !(type instanceof OperatorType);
            case EXCEPT -> applicativeType.isInstance(type);
            case DOMAIN -> type.equals(new SetType(applicativeType.domainElement()));
        };
    }

    @Override
    public Categories categories() {
        return categories;
    }
}
