package io.github.tlaplus.hardening.workflow.execution;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/** A close-aware multi-producer, multi-consumer FIFO queue. */
public final class WorkQueue<T> {
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition available = lock.newCondition();
    private final ArrayDeque<T> elements = new ArrayDeque<>();
    private boolean closed;

    public boolean submit(T element) {
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
    public T take() throws InterruptedException {
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

    /**
     * Returns how many elements currently wait in the queue. This is a sampled value: an element
     * may be claimed or submitted the moment it is read, so it reports progress, not control.
     */
    public int size() {
        lock.lock();
        try {
            return elements.size();
        } finally {
            lock.unlock();
        }
    }

    public void close() {
        lock.lock();
        try {
            closed = true;
            available.signalAll();
        } finally {
            lock.unlock();
        }
    }
}
