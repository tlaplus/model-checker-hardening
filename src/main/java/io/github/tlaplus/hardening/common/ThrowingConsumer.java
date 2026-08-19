package io.github.tlaplus.hardening.common;

/**
 * An operation on one value that may fail with a checked exception.
 *
 * <p>Java has no such interface: {@link java.util.function.Consumer} cannot declare a checked
 * exception, and {@link java.util.concurrent.Callable} declares one but takes no argument. The
 * type parameter {@code E} lets a caller state exactly which exception a body may raise instead
 * of widening every signature to {@code Exception}.
 */
@FunctionalInterface
public interface ThrowingConsumer<T, E extends Throwable> {
    void accept(T value) throws E;
}
