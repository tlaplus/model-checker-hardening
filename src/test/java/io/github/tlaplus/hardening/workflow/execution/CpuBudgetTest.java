package io.github.tlaplus.hardening.workflow.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import io.github.tlaplus.hardening.workflow.execution.CpuBudget.Priority;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;

class CpuBudgetTest {
    private static final BooleanSupplier NEVER_CANCELLED = () -> false;
    private static final long COMPLETION_TIMEOUT_SECONDS = 5;
    private static final long PENDING_OBSERVATION_MILLISECONDS = 250;

    @Test
    void servesDownstreamPrioritiesFirst() throws Exception {
        var budget = new CpuBudget(1);
        assertTrue(budget.acquire(Priority.GENERATOR, 1, NEVER_CANCELLED));
        var order = Collections.synchronizedList(new ArrayList<String>());

        var generator = autoReleasingRequest(budget, Priority.GENERATOR, 1, "generator", order);
        generator.awaitQueued();
        var parser = autoReleasingRequest(budget, Priority.PARSER, 1, "parser", order);
        parser.awaitQueued();
        var checker = autoReleasingRequest(budget, Priority.CHECKER, 1, "checker", order);
        checker.awaitQueued();

        budget.release(1);

        assertTrue(generator.await());
        assertTrue(parser.await());
        assertTrue(checker.await());
        assertEquals(List.of("checker", "parser", "generator"), order);
    }

    @Test
    void preservesFifoOrderWithinAPriority() throws Exception {
        var budget = new CpuBudget(1);
        assertTrue(budget.acquire(Priority.CHECKER, 1, NEVER_CANCELLED));
        var order = Collections.synchronizedList(new ArrayList<String>());

        var first = autoReleasingRequest(budget, Priority.CHECKER, 1, "first", order);
        first.awaitQueued();
        var second = autoReleasingRequest(budget, Priority.CHECKER, 1, "second", order);
        second.awaitQueued();
        var third = autoReleasingRequest(budget, Priority.CHECKER, 1, "third", order);
        third.awaitQueued();

        budget.release(1);

        assertTrue(first.await());
        assertTrue(second.await());
        assertTrue(third.await());
        assertEquals(List.of("first", "second", "third"), order);
    }

    @Test
    void reservesPartialCapacityForAMultiPermitChecker() throws Exception {
        var budget = new CpuBudget(2);
        assertTrue(budget.acquire(Priority.GENERATOR, 2, NEVER_CANCELLED));

        var checker = new BudgetRequest(budget, Priority.CHECKER, 2, NEVER_CANCELLED, () -> {});
        checker.awaitQueued();
        var generator = new BudgetRequest(
                budget,
                Priority.GENERATOR,
                1,
                NEVER_CANCELLED,
                () -> budget.release(1));
        generator.awaitQueued();

        budget.release(1);
        checker.assertPending();
        generator.assertPending();

        budget.release(1);
        assertTrue(checker.await());
        generator.assertPending();

        budget.release(2);
        assertTrue(generator.await());
    }

    @Test
    void removesCancelledRequestsAndResumesUpstreamWork() throws Exception {
        var budget = new CpuBudget(1);
        assertTrue(budget.acquire(Priority.GENERATOR, 1, NEVER_CANCELLED));
        var cancelled = new AtomicBoolean();

        var checker =
                new BudgetRequest(budget, Priority.CHECKER, 1, cancelled::get, () -> {});
        checker.awaitQueued();
        var generator = new BudgetRequest(
                budget,
                Priority.GENERATOR,
                1,
                NEVER_CANCELLED,
                () -> budget.release(1));
        generator.awaitQueued();

        cancelled.set(true);
        assertFalse(checker.await());
        budget.release(1);
        assertTrue(generator.await());
    }

    @Test
    void removesInterruptedRequests() throws Exception {
        var budget = new CpuBudget(1);
        assertTrue(budget.acquire(Priority.GENERATOR, 1, NEVER_CANCELLED));

        var checker =
                new BudgetRequest(budget, Priority.CHECKER, 1, NEVER_CANCELLED, () -> {});
        checker.awaitQueued();
        checker.interrupt();

        var exception = assertThrows(ExecutionException.class, checker::await);
        assertInstanceOf(InterruptedException.class, exception.getCause());

        var generator = new BudgetRequest(
                budget,
                Priority.GENERATOR,
                1,
                NEVER_CANCELLED,
                () -> budget.release(1));
        generator.awaitQueued();
        budget.release(1);
        assertTrue(generator.await());
    }

    @Test
    void validatesPermitAccounting() throws Exception {
        var budget = new CpuBudget(2);

        assertThrows(
                IllegalArgumentException.class,
                () -> budget.acquire(Priority.GENERATOR, 0, NEVER_CANCELLED));
        assertThrows(
                IllegalArgumentException.class,
                () -> budget.acquire(Priority.GENERATOR, 3, NEVER_CANCELLED));
        assertThrows(
                NullPointerException.class, () -> budget.acquire(null, 1, NEVER_CANCELLED));
        assertThrows(
                NullPointerException.class, () -> budget.acquire(Priority.GENERATOR, 1, null));
        assertThrows(IllegalArgumentException.class, () -> budget.release(0));
        assertThrows(IllegalStateException.class, () -> budget.release(1));

        assertTrue(budget.acquire(Priority.CHECKER, 1, NEVER_CANCELLED));
        assertThrows(IllegalStateException.class, () -> budget.release(2));
        budget.release(1);
        assertThrows(IllegalStateException.class, () -> budget.release(1));
    }

    private static BudgetRequest autoReleasingRequest(
            CpuBudget budget,
            Priority priority,
            int permits,
            String name,
            List<String> order) {
        return new BudgetRequest(budget, priority, permits, NEVER_CANCELLED, () -> {
            order.add(name);
            budget.release(permits);
        });
    }

    private static final class BudgetRequest {
        private final CompletableFuture<Boolean> result = new CompletableFuture<>();
        private final Thread thread;

        private BudgetRequest(
                CpuBudget budget,
                Priority priority,
                int permits,
                BooleanSupplier cancelled,
                Runnable acquired) {
            thread = Thread.ofPlatform().daemon().name("cpu-budget-test").start(() -> {
                try {
                    var didAcquire = budget.acquire(priority, permits, cancelled);
                    if (didAcquire) {
                        acquired.run();
                    }
                    result.complete(didAcquire);
                } catch (Throwable exception) {
                    result.completeExceptionally(exception);
                }
            });
        }

        private boolean await() throws InterruptedException, ExecutionException, TimeoutException {
            return result.get(COMPLETION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }

        private void awaitQueued() throws InterruptedException {
            var deadline = System.nanoTime()
                    + TimeUnit.SECONDS.toNanos(COMPLETION_TIMEOUT_SECONDS);
            while (System.nanoTime() < deadline) {
                if (thread.getState() == Thread.State.TIMED_WAITING) {
                    return;
                }
                if (result.isDone()) {
                    fail("CPU request completed before it could queue");
                }
                Thread.sleep(1);
            }
            fail("CPU request did not queue within the test timeout");
        }

        private void assertPending() {
            assertThrows(
                    TimeoutException.class,
                    () -> result.get(
                            PENDING_OBSERVATION_MILLISECONDS, TimeUnit.MILLISECONDS));
        }

        private void interrupt() {
            thread.interrupt();
        }
    }
}
