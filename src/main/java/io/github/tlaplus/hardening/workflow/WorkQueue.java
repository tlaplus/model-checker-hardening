package io.github.tlaplus.hardening.workflow;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/** A close-aware multi-producer, multi-consumer FIFO queue. */
final class WorkQueue<T> {
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition available = lock.newCondition();
    private final ArrayDeque<T> elements = new ArrayDeque<>();
    private boolean closed;

    boolean submit(T element) {
        Objects.requireNonNull(element, "element");
        lock.lock();
        try {
            if (closed) {
                return false;
            }
            elements.addLast(element);
            available.signal();
            return true;
        } finally {
            lock.unlock();
        }
    }

    /** Returns {@code null} only after the queue is closed and drained. */
    T take() throws InterruptedException {
        lock.lockInterruptibly();
        try {
            while (elements.isEmpty() && !closed) {
                available.await();
            }
            return elements.pollFirst();
        } finally {
            lock.unlock();
        }
    }

    void close() {
        lock.lock();
        try {
            closed = true;
            available.signalAll();
        } finally {
            lock.unlock();
        }
    }
}
