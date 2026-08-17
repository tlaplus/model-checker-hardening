package io.github.tlaplus.hardening.workflow.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WorkQueueTest {
    @Test
    void drainsAcceptedWorkAfterCloseAndThenSignalsCompletion() throws Exception {
        var queue = new WorkQueue<Integer>();
        assertTrue(queue.submit(1));
        assertTrue(queue.submit(2));

        queue.close();

        assertFalse(queue.submit(3));
        assertEquals(1, queue.take());
        assertEquals(2, queue.take());
        assertNull(queue.take());
    }
}
