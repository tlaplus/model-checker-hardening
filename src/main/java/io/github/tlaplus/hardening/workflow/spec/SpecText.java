package io.github.tlaplus.hardening.workflow.spec;

import at.forsyte.apalache.io.annotations.PrettyWriterWithAnnotations;
import at.forsyte.apalache.io.annotations.store.package$;
import at.forsyte.apalache.io.lir.TlaWriter$;
import at.forsyte.apalache.tla.lir.TlaModule;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Objects;

/** Renders an assembled module as the TLA+ source that the parser and TLC consume. */
public final class SpecText {
    private SpecText() {}

    /** Renders the module, extending the standard modules its expressions may refer to. */
    public static String render(TlaModule module) {
        Objects.requireNonNull(module, "module");
        var output = new StringWriter();
        try (var writer = new PrintWriter(output)) {
            var prettyWriter = new PrettyWriterWithAnnotations(
                    package$.MODULE$.createAnnotationStore(),
                    writer,
                    PrettyWriterWithAnnotations.$lessinit$greater$default$3());
            prettyWriter.write(module, TlaWriter$.MODULE$.STANDARD_MODULES());
        }
        return output.toString();
    }
}
