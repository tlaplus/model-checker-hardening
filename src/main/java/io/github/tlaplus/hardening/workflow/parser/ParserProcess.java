package io.github.tlaplus.hardening.workflow.parser;

import io.github.tlaplus.hardening.workflow.WorkflowException;
import io.github.tlaplus.hardening.workflow.worker.ChildJvm;
import io.github.tlaplus.hardening.workflow.worker.IsolatedWorkerProcess;
import io.github.tlaplus.hardening.workflow.worker.WorkerSpec;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/** Starts one persistent isolated SANY JVM; the common process handle owns requests and closing. */
final class ParserProcess {
    private ParserProcess() {}

    static IsolatedWorkerProcess start(Path scratchDirectory, List<Path> classpath, Duration timeout)
            throws WorkflowException, InterruptedException {
        return IsolatedWorkerProcess.start(new WorkerSpec(
                scratchDirectory, timeout, ParserWorkerMain.class, classpath, ChildJvm.singleProcessor(),
                "parser worker"));
    }
}
