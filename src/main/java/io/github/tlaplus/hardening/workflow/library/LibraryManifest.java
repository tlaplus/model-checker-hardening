package io.github.tlaplus.hardening.workflow.library;

import io.github.tlaplus.hardening.common.Digests;
import io.github.tlaplus.hardening.corpus.CorpusDirectory;
import io.github.tlaplus.hardening.corpus.CorpusException;
import io.github.tlaplus.hardening.gen.library.OperatorId;
import io.github.tlaplus.hardening.workflow.WorkflowException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/** Replay policy for external source snapshots. Storage treats this manifest as opaque bytes. */
public final class LibraryManifest {
    private LibraryManifest() {}

    static String create(Path sources, Path jar, List<OperatorId> selected) throws IOException {
        var text = new StringBuilder("fuzztla-library-v1\napalache ").append(Digests.digest(jar)).append('\n');
        try (var paths = Files.list(sources)) {
            for (var path : paths.sorted().toList()) {
                text.append("source ").append(path.getFileName()).append(' ').append(Digests.digest(path)).append('\n');
            }
        }
        selected.forEach(id -> text.append("operator ").append(id).append('\n'));
        return text.toString();
    }

    /** The writer calls this under the corpus lock before decoding or admitting any entry. */
    public static void verify(CorpusDirectory corpus, String manifest, boolean initialize)
            throws IOException, CorpusException, WorkflowException {
        var saved = corpus.readLibraryManifest();
        var bytes = manifest.getBytes(StandardCharsets.UTF_8);
        if (saved.isPresent()) {
            if (!Arrays.equals(saved.get(), bytes)) {
                throw new WorkflowException("custom operator library changed: restore its sources, selections and "
                        + "Apalache distribution, or initialize a new corpus");
            }
        } else if (!manifest.isEmpty()) {
            if (!initialize || corpus.hasStoredInputs()) {
                throw new WorkflowException("custom operator replay manifest is missing; start with an empty corpus");
            }
            corpus.writeLibraryManifest(bytes);
        }
    }
}
