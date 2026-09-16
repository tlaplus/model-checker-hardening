package io.github.tlaplus.hardening.workflow.worker;

import io.github.tlaplus.hardening.common.Preconditions;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The resources and options of one child JVM.
 *
 * <p>A worker's CPU permits bound only the tool's own threads. The JVM sizes its garbage-collector
 * and JIT-compiler thread pools from the processors it sees, and those threads run beside the tool
 * thread. The child therefore sees exactly the processors its permits reserve, and a short-lived
 * child skips optimizing compilation whose results it would not live to use.
 *
 * @param processors the processors the child JVM reports and sizes its thread pools for; the
 *     CPU permits one request of this worker reserves
 * @param compilation how much JIT compilation the child performs
 * @param options further JVM options, placed after the ones this record derives
 */
public record ChildJvm(int processors, Compilation compilation, List<String> options) {
    /** How much JIT compilation a child JVM performs, chosen by how long the child lives. */
    public enum Compilation {
        /** Full tiered compilation, for a child that serves many inputs. */
        TIERED(List.of()),
        /** Client-compiler code only, for a child that serves one input and exits. */
        QUICK(List.of("-XX:TieredStopAtLevel=1"));

        private final List<String> options;

        Compilation(List<String> options) {
            this.options = options;
        }
    }

    public ChildJvm {
        Preconditions.requirePositive(processors, "processors");
        Objects.requireNonNull(compilation, "compilation");
        options = List.copyOf(options);
    }

    /** A long-lived child that runs one tool thread and needs no further options. */
    public static ChildJvm singleProcessor() {
        return new ChildJvm(1, Compilation.TIERED, List.of());
    }

    /** Returns the JVM options that launch this child. */
    public List<String> arguments() {
        var arguments = new ArrayList<String>();
        arguments.add("-XX:ActiveProcessorCount=" + processors);
        arguments.addAll(compilation.options);
        arguments.addAll(options);
        return List.copyOf(arguments);
    }
}
