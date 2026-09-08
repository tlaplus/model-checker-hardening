package io.github.tlaplus.hardening.common;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CleanupTest {
    @Test
    void runsCleanupOnceAndRetainsSuppressedFailuresInOrder() {
        var primary = new IOException("primary");
        var first = new IOException("first");
        var second = new IOException("second");
        var calls = new AtomicInteger();
        Cleanup.suppressIOException(primary, calls::incrementAndGet);
        Cleanup.suppressIOException(primary, () -> { throw first; });
        Cleanup.suppressIOException(primary, () -> { throw second; });
        assertEquals(1, calls.get());
        assertArrayEquals(new Throwable[] {first, second}, primary.getSuppressed());
    }

    @Test
    void doesNotBroadenTheSuppressionCatchSet() {
        var primary = new IOException("primary");
        var unchecked = new IllegalStateException("unchecked");
        var error = new AssertionError("error");
        assertSame(unchecked, assertThrows(IllegalStateException.class,
                () -> Cleanup.suppressIOException(primary, () -> { throw unchecked; })));
        assertSame(error, assertThrows(AssertionError.class,
                () -> Cleanup.suppressIOException(primary, () -> { throw error; })));
        assertEquals(0, primary.getSuppressed().length);
    }
}
