package io.github.tlaplus.hardening.workflow.execution;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/** A close-aware multi-producer, multi-consumer queue; optionally ordered by priority. */
public final class WorkQueue<T> {
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition available = lock.newCondition();
    private final Queue<T> elements;
    private boolean closed;

    public WorkQueue() {
        elements = new ArrayDeque<>();
    }

    public WorkQueue(Comparator<? super T> priority) {
        elements = new PriorityQueue<>(Objects.requireNonNull(priority, "priority"));
    }

    public boolean submit(T element) {
        Objects.requireNonNull(element, "element");
        lock.lock();
        try {
            if (closed) {
                return false;
            }
            elements.add(element);
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
            return elements.poll();
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
