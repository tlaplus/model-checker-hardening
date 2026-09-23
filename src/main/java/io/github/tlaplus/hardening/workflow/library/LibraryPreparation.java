package io.github.tlaplus.hardening.workflow.library;

import at.forsyte.apalache.tla.lir.TlaModule;
import io.github.tlaplus.hardening.common.TemporaryDirectory;
import io.github.tlaplus.hardening.config.FuzzTlaConfig;
import io.github.tlaplus.hardening.corpus.CorpusStage;
import io.github.tlaplus.hardening.gen.IrGenerationConfig;
import io.github.tlaplus.hardening.gen.library.ModuleLink;
import io.github.tlaplus.hardening.gen.library.OperatorLibrary;
import io.github.tlaplus.hardening.workflow.WorkflowException;
import io.github.tlaplus.hardening.workflow.apalache.ApalacheDistribution;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Set;

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
            // The manifest covers the user's sources only, not the wrappers written below.
            var manifest = LibraryManifest.create(sources, jar, config.libraries());
            var modules = new LinkedHashMap<String, TlaModule>();
            var links = new HashMap<String, ModuleLink>();
            for (var selection : config.libraries().modules()) {
                var module = selection.module();
                var link = selection.link();
                links.put(module, link);
                // The TLC module is never imported; the source probe below checks its interface.
                if (link.linkage().namesTlcModule()) requireSource(sources, link.sourceModule());
                var roots = Set.copyOf(selection.operators());
                if (link.linkage().typechecksThroughWrapper()) {
                    var wrapper = wrapperName(module);
                    modules.put(module, typechecker.check(writeWrapper(sources, wrapper, module), Set.of(wrapper, module), roots));
                } else {
                    modules.put(module, typechecker.check(requireSource(sources, module), Set.of(module), roots));
                }
            }
            var library = OperatorLibrary.fromModules(modules, config.libraries().operators(), links);
            LibrarySourceProbe.check(library.sourceAliases(), config.workflow().parser(), scratch.resolve("parser"),
                    config.libraries().sourceCheckerClasspath());
            return new Prepared(config.generator().withLibrary(library), manifest);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new WorkflowException("custom library preparation interrupted", exception);
        } catch (IOException | RuntimeException exception) {
            throw new WorkflowException("cannot prepare custom operators: " + exception.getMessage(), exception);
        }
    }

    private static Path requireSource(Path sources, String module) throws WorkflowException {
        var source = sources.resolve(module + ".tla");
        if (!Files.isRegularFile(source)) {
            throw new WorkflowException("custom module not found on generator.classpath: " + module);
        }
        return source;
    }

    private static String wrapperName(String module) {
        return "FuzzTlaInstanceOf" + module;
    }

    /**
     * Writes a module that only extends {@code module}, so Apalache imports what it would evaluate
     * for {@code EXTENDS module}, including its own definitions for the modules it rewires.
     */
    private static Path writeWrapper(Path sources, String wrapper, String module)
            throws IOException, WorkflowException {
        var path = sources.resolve(wrapper + ".tla");
        if (Files.exists(path)) {
            throw new WorkflowException("generator.classpath must not contain " + path.getFileName());
        }
        return Files.writeString(path, "---- MODULE " + wrapper + " ----\nEXTENDS " + module + "\n====\n");
    }
}
