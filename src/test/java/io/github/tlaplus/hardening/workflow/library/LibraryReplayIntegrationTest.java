package io.github.tlaplus.hardening.workflow.library;

import io.github.tlaplus.hardening.cli.FuzzTlaCommand;
import io.github.tlaplus.hardening.config.*;
import io.github.tlaplus.hardening.corpus.*;
import io.github.tlaplus.hardening.gen.InputKind;
import io.github.tlaplus.hardening.workflow.WorkflowRunner;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;
import static org.junit.jupiter.api.Assertions.*;

class LibraryReplayIntegrationTest {
    @Test
    void runRecordsIdentityAndPrintChecksIt(@TempDir Path directory) throws Exception {
        var sources = Files.createDirectory(directory.resolve("tla"));
        var source = sources.resolve("Mine.tla");
        Files.writeString(source, "---- MODULE Mine ----\nIdentity(x) == x\n====\n");
        var defaults = LibraryPreparationTest.config(List.of(sources), "Mine", "Identity");
        var checkers = new EnumMap<CorpusStage, CheckerStageConfig>(CorpusStage.class);
        for (var stage : CorpusStage.checkerBranches()) checkers.put(stage, new CheckerStageConfig(0, 30, 512, 1));
        var config = new FuzzTlaConfig(defaults.generatedKind(), defaults.generator(),
                new WorkflowConfig(0, InputStageConfig.of(0), new ParserStageConfig(0, 30), checkers),
                defaults.pbt(), defaults.libraries());
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"), TomlConfig.render(config));
        new WorkflowRunner(config).run(corpus, 42, 1);
        assertTrue(corpus.readLibraryManifest().isPresent());
        corpus.store(InputKind.EXPRESSION, new byte[0]);
        var input = corpus.inputPath(new byte[0]);
        var out = new StringWriter();
        var err = new StringWriter();
        var cli = new CommandLine(new FuzzTlaCommand()).setOut(new PrintWriter(out)).setErr(new PrintWriter(err));
        assertEquals(0, cli.execute("print", "--corpus", corpus.resolve(CorpusPath.ROOT).toString(), input.toString()), err.toString());
        assertTrue(out.toString().contains("FALSE"));
        Files.writeString(source, "---- MODULE Mine ----\nIdentity(x) == {x}\n====\n");
        assertNotEquals(0, cli.execute("print", "--corpus", corpus.resolve(CorpusPath.ROOT).toString(), input.toString()));
        assertTrue(err.toString().contains("library changed"), err.toString());
    }
}
