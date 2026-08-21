package io.github.tlaplus.hardening.workflow.spec;

import at.forsyte.apalache.io.annotations.PrettyWriterWithAnnotations;
import at.forsyte.apalache.io.annotations.store.package$;
import at.forsyte.apalache.io.lir.TlaWriter$;
import at.forsyte.apalache.tla.lir.TlaEx;
import java.io.PrintWriter;
import java.io.StringWriter;

/** Shared construction and rendering of the generated module consumed by tool stages. */
public final class ExprInputToSpec {
    private ExprInputToSpec() {}

    /** Renders the generated expression as the complete, typed tool input module. */
    public static String render(TlaEx expression) {
        var output = new StringWriter();
        try (var writer = new PrintWriter(output)) {
            var prettyWriter = new PrettyWriterWithAnnotations(
                    package$.MODULE$.createAnnotationStore(),
                    writer,
                    PrettyWriterWithAnnotations.$lessinit$greater$default$3());
            prettyWriter.write(
                    FuzzInputModule.create(expression),
                    TlaWriter$.MODULE$.STANDARD_MODULES());
        }
        return output.toString();
    }

}
