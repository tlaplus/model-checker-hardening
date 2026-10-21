package io.github.tlaplus.hardening.workflow.worker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tlaplus.hardening.workflow.worker.ToolThreadStacks.Sample;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ToolThreadStacksTest {
    private static StackTraceElement frame(String method) {
        return new StackTraceElement("tlc2.Tool", method, "Tool.java", 7);
    }

    private static List<StackTraceElement> frames(int count) {
        return IntStream.range(0, count).mapToObj(index -> frame("m" + index)).toList();
    }

    @Test
    void showsTheWorkingThreadsByNameInnermostFrameFirst() {
        var text = ToolThreadStacks.describe(List.of(
                new Sample("worker-b", Thread.State.BLOCKED, List.of(frame("lock"))),
                new Sample("main", Thread.State.WAITING, List.of(frame("join"))),
                new Sample("Signal Dispatcher", Thread.State.RUNNABLE, List.of()),
                new Sample("worker-a", Thread.State.RUNNABLE, List.of(frame("inner"), frame("outer")))));

        assertEquals(String.join(System.lineSeparator(),
                ToolThreadStacks.HEADING,
                "\"worker-a\" RUNNABLE",
                "\tat tlc2.Tool.inner(Tool.java:7)",
                "\tat tlc2.Tool.outer(Tool.java:7)",
                "\"worker-b\" BLOCKED",
                "\tat tlc2.Tool.lock(Tool.java:7)",
                ""), text);
    }

    @Test
    void isEmptyWhenNoThreadWorks() {
        assertEquals("", ToolThreadStacks.describe(List.of(
                new Sample("main", Thread.State.TIMED_WAITING, List.of(frame("sleep"))))));
    }

    @Test
    void keepsTheInnermostFramesOfADeepStack() {
        var text = ToolThreadStacks.describe(List.of(new Sample(
                "deep", Thread.State.RUNNABLE, frames(ToolThreadStacks.MAXIMUM_FRAMES + 10))));

        assertTrue(text.contains("tlc2.Tool.m0("), text);
        var last = ToolThreadStacks.MAXIMUM_FRAMES - 1;
        assertTrue(text.contains("tlc2.Tool.m" + last + "("), text);
        assertTrue(!text.contains("tlc2.Tool.m" + (last + 1) + "("), text);
        assertTrue(text.endsWith("\t[10 more frames]" + System.lineSeparator()), text);
    }

    @Test
    void boundsTheSectionOverAllThreads() {
        var samples = IntStream.range(0, 100)
                .mapToObj(index -> new Sample("worker-" + index, Thread.State.RUNNABLE,
                        frames(ToolThreadStacks.MAXIMUM_FRAMES)))
                .toList();

        var text = ToolThreadStacks.describe(samples);

        var marker = System.lineSeparator() + "[tool threads truncated]";
        assertTrue(text.endsWith(marker), text.substring(text.length() - 100));
        assertEquals(ToolThreadStacks.MAXIMUM_BYTES,
                text.substring(0, text.length() - marker.length())
                        .getBytes(StandardCharsets.UTF_8).length);
    }

    @Test
    void samplesTheThreadsOfThisJvmButNotTheCaller() throws Exception {
        var started = new java.util.concurrent.CountDownLatch(1);
        var spinner = Thread.ofPlatform().name("deliberate spinner").daemon().start(() -> {
            started.countDown();
            while (!Thread.currentThread().isInterrupted()) {
                Thread.onSpinWait();
            }
        });
        try {
            started.await();
            var text = ToolThreadStacks.sampleOthers();
            assertTrue(text.contains("\"deliberate spinner\" RUNNABLE"), text);
            assertTrue(text.contains("\tat java.lang.Thread.onSpinWait(")
                    || text.contains("\tat " + ToolThreadStacksTest.class.getName() + ".lambda$"), text);
            assertTrue(!text.contains("//"), "frames name no class loader: " + text);
            assertTrue(!text.contains("@"), "frames name no module version: " + text);
            assertTrue(!text.contains("sampleOthers"), text);
            assertTrue(!text.contains("Reference Handler"), text);
        } finally {
            spinner.interrupt();
        }
    }
}
