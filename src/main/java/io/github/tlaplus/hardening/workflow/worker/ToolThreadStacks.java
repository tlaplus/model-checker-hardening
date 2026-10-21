package io.github.tlaplus.hardening.workflow.worker;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The stacks of the threads a stopped worker was working on, for the diagnostic of a timeout
 * (ADR 0021).
 *
 * <p>A thread is working if it is {@code RUNNABLE}, including in native code such as a solver
 * call, or {@code BLOCKED} on a lock. Waiting threads, such as TLC's main thread and its state
 * queue, and the JVM's own threads name no cause and are left out. Frames are innermost first and
 * read as {@link Throwable} prints them, so the frames that name a cause come before the bounds cut
 * the rest, and a signature written against an exception trace reads them the same way.
 */
final class ToolThreadStacks {
    static final String HEADING = "Tool threads at shutdown:";
    static final int MAXIMUM_FRAMES = 256;
    static final int MAXIMUM_BYTES = 64 * 1024;

    private ToolThreadStacks() {}

    /** One thread's name, state and stack, innermost frame first. */
    record Sample(String name, Thread.State state, List<StackTraceElement> frames) {
        Sample {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(state, "state");
            frames = List.copyOf(frames);
        }

        boolean working() {
            return !frames.isEmpty()
                    && (state == Thread.State.RUNNABLE || state == Thread.State.BLOCKED);
        }
    }

    /**
     * Samples every live thread except the calling one, which is the shutdown hook, and the threads
     * of the JVM's system thread group, such as the reference handler.
     */
    static String sampleOthers() {
        var self = Thread.currentThread();
        return describe(Thread.getAllStackTraces().entrySet().stream()
                .filter(entry -> entry.getKey() != self && !isSystemThread(entry.getKey()))
                .map(ToolThreadStacks::sample)
                .toList());
    }

    /** Formats the working threads of {@code samples} by name, or returns "" if none works. */
    static String describe(List<Sample> samples) {
        var working = samples.stream()
                .filter(Sample::working)
                .sorted(Comparator.comparing(Sample::name))
                .toList();
        if (working.isEmpty()) {
            return "";
        }
        var buffer = new BoundedTextOutputStream(MAXIMUM_BYTES, "tool threads");
        try (var text = new PrintStream(buffer, false, StandardCharsets.UTF_8)) {
            text.println(HEADING);
            for (var thread : working) {
                text.println("\"" + thread.name() + "\" " + thread.state());
                var shown = Math.min(thread.frames().size(), MAXIMUM_FRAMES);
                for (var frame : thread.frames().subList(0, shown)) {
                    text.println("\tat " + withoutLoaderAndModule(frame));
                }
                if (shown < thread.frames().size()) {
                    text.println("\t[" + (thread.frames().size() - shown) + " more frames]");
                }
            }
        }
        return buffer.text();
    }

    /**
     * The frame as an exception trace prints it, {@code tlc2.Tool.eval(Tool.java:7)}. A frame of
     * {@link Thread#getAllStackTraces} also names its class loader and module, as in {@code
     * app//tlc2.Tool.eval(Tool.java:7)}; a frame built from its parts names neither.
     */
    private static StackTraceElement withoutLoaderAndModule(StackTraceElement frame) {
        return new StackTraceElement(frame.getClassName(), frame.getMethodName(),
                frame.getFileName(), frame.getLineNumber());
    }

    private static boolean isSystemThread(Thread thread) {
        var group = thread.getThreadGroup();
        return group != null && group.getParent() == null;
    }

    private static Sample sample(Map.Entry<Thread, StackTraceElement[]> entry) {
        var thread = entry.getKey();
        return new Sample(thread.getName(), thread.getState(), List.of(entry.getValue()));
    }
}
