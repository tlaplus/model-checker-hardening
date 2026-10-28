package io.github.tlaplus.hardening.workflow.library;

import io.github.tlaplus.hardening.common.Digests;
import io.github.tlaplus.hardening.config.OperatorLibraryConfig;
import io.github.tlaplus.hardening.gen.library.LibraryLinkage;
import io.github.tlaplus.hardening.gen.library.OperatorId;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The identity of a custom operator library's source snapshot, which a metamorphic run compares
 * with its base corpus's (ADR 0016 §6, ADR 0025 §5).
 */
public final class LibraryManifest {
    private LibraryManifest() {}

    /**
     * Names the Apalache distribution, the source snapshot and the selection. An instance- or
     * diff-linked module also pins every classpath file, whose class files now reach TLC (ADR 0014),
     * and a diff-linked one names its TLC module (ADR 0015); inline-only manifests are unchanged
     * from before linkage existed.
     */
    static String create(Path sources, Path jar, OperatorLibraryConfig libraries) throws IOException {
        var text = snapshot("fuzztla-library-v1", sources, jar);
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

    /**
     * Starts a manifest with its format line, the Apalache distribution and a digest of every file of
     * the source snapshot, in name order.
     */
    static StringBuilder snapshot(String format, Path sources, Path jar) throws IOException {
        var text = new StringBuilder(format).append("\napalache ").append(Digests.digest(jar)).append('\n');
        try (var paths = Files.list(sources)) {
            for (var path : paths.sorted().toList()) {
                text.append("source ").append(path.getFileName()).append(' ').append(Digests.digest(path)).append('\n');
            }
        }
        return text;
    }
}
