package io.github.tlaplus.hardening.workflow.input;

import io.github.tlaplus.hardening.workflow.execution.WorkQueue;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.Semaphore;

/**
 * How the input stage hands entries to the parser: the parser's queue, and the {@code 00-inputs}
 * slots that bound how many entries wait in it. A generator worker reserves a slot for each
 * candidate and keeps it only when the candidate is stored; the parser frees it once it has
 * recorded a verdict for the entry.
 */
public record InputHandoff(WorkQueue<Path> queue, Semaphore capacity) {
    public InputHandoff {
        Objects.requireNonNull(queue, "queue");
        Objects.requireNonNull(capacity, "capacity");
    }
}
