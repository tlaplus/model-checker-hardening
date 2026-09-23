package io.github.tlaplus.hardening.workflow.library;

import io.github.tlaplus.hardening.config.ParserStageConfig;
import io.github.tlaplus.hardening.gen.library.InstanceAlias;
import io.github.tlaplus.hardening.workflow.WorkflowException;
import io.github.tlaplus.hardening.workflow.parser.ParserBackend;
import io.github.tlaplus.hardening.workflow.spec.SpecText;
import io.github.tlaplus.hardening.workflow.worker.StageOutcome;
import io.github.tlaplus.hardening.workflow.worker.ToolInput;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Parses every source alias of a prepared library once, with the classpath the parser and TLC
 * workers receive (ADR 0015). Fuzztla never imports the modules these aliases instantiate, so a
 * missing module or operator, or a mismatched arity, would otherwise fail every input that calls
 * it.
 */
final class LibrarySourceProbe {
    private LibrarySourceProbe() {}

    static void check(List<InstanceAlias> aliases, ParserStageConfig settings, Path scratch, List<Path> classpath)
            throws IOException, WorkflowException, InterruptedException {
        if (aliases.isEmpty()) return;
        Files.createDirectories(scratch);
        try (var worker = new ParserBackend(settings, 1, scratch, classpath).startWorker()) {
            var result = worker.check(new ToolInput(SpecText.aliasProbe(aliases), 0));
            if (result.outcome() != StageOutcome.PASS) {
                throw new WorkflowException("the parser cannot resolve the library's instance aliases on "
                        + "generator.classpath:\n" + result.diagnostic());
            }
        }
    }
}
