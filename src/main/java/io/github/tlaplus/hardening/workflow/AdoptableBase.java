package io.github.tlaplus.hardening.workflow;

import io.github.tlaplus.hardening.common.Diagnostics;
import io.github.tlaplus.hardening.config.ConfigException;
import io.github.tlaplus.hardening.config.FuzzTlaConfig;
import io.github.tlaplus.hardening.config.TomlConfig;
import io.github.tlaplus.hardening.corpus.CorpusDirectory;
import io.github.tlaplus.hardening.corpus.CorpusException;
import io.github.tlaplus.hardening.corpus.CorpusPath;
import io.github.tlaplus.hardening.corpus.Technique;
import io.github.tlaplus.hardening.gen.IrGenerationConfig;
import io.github.tlaplus.hardening.gen.library.OperatorLibrary;
import io.github.tlaplus.hardening.workflow.library.LibraryManifest;
import java.io.IOException;
import java.nio.file.Path;

/**
 * The base corpus of a metamorphic run (ADR 0016 §6). An adopted entry keeps its parent's payload
 * and decodes it with this run's generator, so it reproduces its parent only when both corpora
 * decode alike: the base must store plain payloads, as a {@code pbt} or {@code gbt} corpus does,
 * and have the same {@code [generator]} and {@code [graph]} settings, the same custom operator
 * library and the same edge guards.
 */
final class AdoptableBase {
    private AdoptableBase() {}

    /**
     * Refuses a base corpus whose entries this run would not decode to their parents.
     *
     * @param generator this run's generator settings, which include the graph limits; the library
     *     they carry is not compared
     * @param libraryManifest the manifest of this run's custom operator library, empty without one
     * @param guardManifest the manifest of this run's edge guards, empty without them
     */
    static void require(Path base, IrGenerationConfig generator, String libraryManifest, String guardManifest)
            throws IOException, CorpusException, WorkflowException {
        var corpus = CorpusDirectory.openExisting(base);
        final FuzzTlaConfig baseConfig;
        try {
            baseConfig = TomlConfig.read(corpus.resolve(CorpusPath.CONFIG));
        } catch (ConfigException exception) {
            throw new WorkflowException("cannot read the configuration of metamorphic.base_corpus '" + base
                    + "': " + Diagnostics.message(exception), exception);
        }
        var technique = baseConfig.workflow().technique();
        CorpusRecords.TECHNIQUE.requireUnchanged(corpus, technique);
        // An mt payload carries a metamorphic header, so it is not the payload of a base.
        if (technique == Technique.MT) {
            throw new WorkflowException("metamorphic.base_corpus must be a pbt or gbt corpus, but '" + base
                    + "' runs " + technique.encodedName());
        }
        var baseGenerator = baseConfig.generator();
        if (!withoutLibrary(baseGenerator).equals(withoutLibrary(generator))) {
            throw new WorkflowException("metamorphic.base_corpus '" + base + "' has other [generator] or [graph] "
                    + "settings, so adopted entries would not decode to their parents; copy its [generator] and "
                    + "[graph] tables");
        }
        if (!LibraryManifest.read(corpus).equals(libraryManifest)) {
            throw new WorkflowException("metamorphic.base_corpus '" + base + "' has another custom operator "
                    + "library, so adopted entries would not decode to their parents; copy its [generator] "
                    + "classpath and custom_operators, and its library sources");
        }
        // A pbt or gbt corpus pins exactly its edge guards in this record (ADR 0023 §6).
        if (!CorpusRecords.REWRITE_LIBRARY.read(corpus).equals(guardManifest)) {
            throw new WorkflowException("metamorphic.base_corpus '" + base + "' has other edge guards, so adopted "
                    + "graph entries would not decode to their parents; copy its [graph] rules and weights, and "
                    + "the rule sources");
        }
    }

    /** The library is compared by its manifest, which pins sources rather than resolved paths. */
    private static IrGenerationConfig withoutLibrary(IrGenerationConfig generator) {
        return generator.withLibrary(OperatorLibrary.empty());
    }
}
