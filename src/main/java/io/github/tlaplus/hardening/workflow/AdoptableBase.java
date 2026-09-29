package io.github.tlaplus.hardening.workflow;

import io.github.tlaplus.hardening.common.Diagnostics;
import io.github.tlaplus.hardening.config.ConfigException;
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
 * decode alike: the base must be a {@code pbt} corpus with the same {@code [generator]} settings
 * and the same custom operator library.
 */
final class AdoptableBase {
    private AdoptableBase() {}

    /**
     * Refuses a base corpus whose entries this run would not decode to their parents.
     *
     * @param generator this run's generator settings; the library they carry is not compared
     * @param libraryManifest the manifest of this run's custom operator library, empty without one
     */
    static void require(Path base, IrGenerationConfig generator, String libraryManifest)
            throws IOException, CorpusException, WorkflowException {
        var corpus = CorpusDirectory.openExisting(base);
        var technique = CorpusRecords.TECHNIQUE.read(corpus);
        if (technique != Technique.PBT) {
            throw new WorkflowException("metamorphic.base_corpus must be a pbt corpus, but '" + base
                    + "' runs --how=" + technique.encodedName());
        }
        final IrGenerationConfig baseGenerator;
        try {
            baseGenerator = TomlConfig.read(corpus.resolve(CorpusPath.CONFIG)).generator();
        } catch (ConfigException exception) {
            throw new WorkflowException("cannot read the configuration of metamorphic.base_corpus '" + base
                    + "': " + Diagnostics.message(exception), exception);
        }
        if (!withoutLibrary(baseGenerator).equals(withoutLibrary(generator))) {
            throw new WorkflowException("metamorphic.base_corpus '" + base + "' has other [generator] settings, "
                    + "so adopted entries would not decode to their parents; copy its [generator] table");
        }
        if (!LibraryManifest.read(corpus).equals(libraryManifest)) {
            throw new WorkflowException("metamorphic.base_corpus '" + base + "' has another custom operator "
                    + "library, so adopted entries would not decode to their parents; copy its [generator] "
                    + "classpath and custom_operators, and its library sources");
        }
    }

    /** The library is compared by its manifest, which pins sources rather than resolved paths. */
    private static IrGenerationConfig withoutLibrary(IrGenerationConfig generator) {
        return generator.withLibrary(OperatorLibrary.empty());
    }
}
