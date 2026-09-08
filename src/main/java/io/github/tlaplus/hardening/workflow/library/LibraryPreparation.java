package io.github.tlaplus.hardening.workflow.library;

import at.forsyte.apalache.tla.lir.TlaModule;
import io.github.tlaplus.hardening.common.TemporaryDirectory;
import io.github.tlaplus.hardening.config.FuzzTlaConfig;
import io.github.tlaplus.hardening.corpus.CorpusStage;
import io.github.tlaplus.hardening.gen.IrGenerationConfig;
import io.github.tlaplus.hardening.gen.library.OperatorLibrary;
import io.github.tlaplus.hardening.workflow.WorkflowException;
import io.github.tlaplus.hardening.workflow.apalache.ApalacheDistribution;
import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedHashMap;

/** Prepares custom libraries once before decoding; no importer/typechecker Maven dependency. */
public final class LibraryPreparation {
    /**
     * Settings carrying the prepared library, beside the replay identity of the sources it was
     * built from. That identity is a workflow concern, so it does not travel inside the library.
     * The manifest is empty when no custom module is configured.
     */
    public record Prepared(IrGenerationConfig generator, String manifest) {}

    private LibraryPreparation() {}

    public static Prepared prepare(FuzzTlaConfig config) throws WorkflowException {
        if (config.libraries().modules().isEmpty()) return new Prepared(config.generator(), "");
        try (var temporary = TemporaryDirectory.create("fuzztla-library-")) {
            var scratch = temporary.path();
            var sources = Files.createDirectory(scratch.resolve("sources"));
            var jar = ApalacheDistribution.locate();
            LibrarySources.snapshot(config.libraries().classpath(), sources, jar);
            var settings = config.workflow().checker(CorpusStage.APALACHE);
            var typechecker = new LibraryTypechecker(jar, scratch, settings);
            var modules = new LinkedHashMap<String, TlaModule>();
            for (var selection : config.libraries().modules()) {
                var module = selection.module();
                var source = sources.resolve(module + ".tla");
                if (!Files.isRegularFile(source)) {
                    throw new WorkflowException("custom module not found on generator.classpath: " + module);
                }
                modules.put(module, typechecker.check(source, module));
            }
            var selected = config.libraries().operators();
            return new Prepared(
                    config.generator().withLibrary(OperatorLibrary.fromModules(modules, selected)),
                    LibraryManifest.create(sources, jar, selected));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new WorkflowException("custom library preparation interrupted", exception);
        } catch (IOException | RuntimeException exception) {
            throw new WorkflowException("cannot prepare custom operators: " + exception.getMessage(), exception);
        }
    }
}
