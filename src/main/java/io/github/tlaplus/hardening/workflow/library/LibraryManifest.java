package io.github.tlaplus.hardening.workflow.library;

import io.github.tlaplus.hardening.common.Digests;
import io.github.tlaplus.hardening.config.OperatorLibraryConfig;
import io.github.tlaplus.hardening.corpus.CorpusDirectory;
import io.github.tlaplus.hardening.corpus.CorpusException;
import io.github.tlaplus.hardening.corpus.CorpusRecord;
import io.github.tlaplus.hardening.gen.library.LibraryLinkage;
import io.github.tlaplus.hardening.gen.library.OperatorId;
import io.github.tlaplus.hardening.workflow.ReplayRecord;
import io.github.tlaplus.hardening.workflow.WorkflowException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Replay policy for external source snapshots. Storage treats this manifest as opaque bytes. */
public final class LibraryManifest {
    private LibraryManifest() {}

    /**
     * Names the Apalache distribution, the source snapshot and the selection. An instance- or
     * diff-linked module also pins every classpath file, whose class files now reach TLC (ADR 0014),
     * and a diff-linked one names its TLC module (ADR 0015); inline-only manifests are unchanged
     * from before linkage existed.
     */
    static String create(Path sources, Path jar, OperatorLibraryConfig libraries) throws IOException {
        var text = new StringBuilder("fuzztla-library-v1\napalache ").append(Digests.digest(jar)).append('\n');
        try (var paths = Files.list(sources)) {
            for (var path : paths.sorted().toList()) {
                text.append("source ").append(path.getFileName()).append(' ').append(Digests.digest(path)).append('\n');
            }
        }
        if (libraries.hasSourceAliases()) {
            for (var entry : libraries.classpath()) {
                if (Files.isRegularFile(entry)) {
                    text.append("classpath ").append(entry.getFileName()).append(' ')
                            .append(Digests.digest(entry)).append('\n');
                }
            }
        }
        for (var module : libraries.modules()) {
            for (var operator : module.operators()) {
                text.append("operator ").append(new OperatorId(module.module(), operator));
                if (module.linkage() != LibraryLinkage.INLINE) text.append(' ').append(module.linkage().encodedName());
                if (module.linkage().namesTlcModule()) text.append(' ').append(module.link().sourceModule());
                text.append('\n');
            }
        }
        return text.toString();
    }

    private static final ReplayRecord<String> RECORD = new ReplayRecord<>(
            CorpusRecord.LIBRARY_MANIFEST, "", ReplayRecord.Codec.TEXT, (saved, expected) -> saved.isEmpty()
                    ? "custom operator replay manifest is missing; start with an empty corpus"
                    : "custom operator library changed: restore its sources, classpath files, selections and "
                            + "Apalache distribution, or initialize a new corpus");

    /** The writer calls this under the corpus lock before decoding or admitting any entry. */
    public static void verify(CorpusDirectory corpus, String manifest, boolean initialize)
            throws IOException, CorpusException, WorkflowException {
        RECORD.verify(corpus, manifest, initialize);
    }
}
