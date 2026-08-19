package io.github.tlaplus.hardening.common;

/**
 * An operation that takes no value and may fail with a checked exception.
 *
 * <p>This is {@link ThrowingConsumer} at {@code T = Void}, stated as a specialization so that a
 * no-argument body reads as {@code () -> ...} and no caller has to pass {@code null}.
 */
@FunctionalInterface
public interface ThrowingRunnable<E extends Throwable> extends ThrowingConsumer<Void, E> {
    void run() throws E;

    @Override
    default void accept(Void unused) throws E {
        run();
    }
}
