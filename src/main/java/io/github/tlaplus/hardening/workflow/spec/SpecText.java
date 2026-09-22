package io.github.tlaplus.hardening.workflow.spec;

import at.forsyte.apalache.tla.lir.TlaModule;
import io.github.tlaplus.hardening.gen.library.InstanceAlias;
import io.github.tlaplus.hardening.gen.library.OperatorLibrary;
import io.github.tlaplus.hardening.gen.library.SourceLink;
import io.github.tlaplus.hardening.workflow.worker.ToolWorkerProtocol;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import org.apalache_mc.tla.jio.TlaText;

/** Renders an assembled module as the TLA+ source that the parser and TLC consume. */
public final class SpecText {
    private static final TlaText TEXT = new TlaText(80, 2);

    /** Alias parameters use the library's namespace, which no generated binder uses. */
    private static final String ALIAS_PARAMETER = "CustomP";

    private SpecText() {}

    /**
     * Whether the module renders to TLA+ source that fits one worker request frame. A larger
     * rendering cannot be carried to the parser or TLC, so the input stage rejects it rather than
     * admitting an entry the checkers can only crash on.
     */
    public static boolean withinWorkerProtocolLimit(TlaModule module) {
        return render(module).getBytes(StandardCharsets.UTF_8).length
                <= ToolWorkerProtocol.maximumMessageBytes();
    }

    /** Renders the module, extending the standard modules its expressions may refer to. */
    public static String render(TlaModule module) {
        Objects.requireNonNull(module, "module");
        return TEXT.render(writer -> TEXT.writeWithStandard(module, writer));
    }

    /** Renders what the parser and TLC evaluate: the module and its instance aliases. */
    public static String render(SpecArtifact artifact) {
        return render(artifact.source());
    }

    /**
     * Renders the module and defines its aliases after {@code EXTENDS}. The IR printer can express
     * neither {@code INSTANCE} nor {@code !}, so these declarations are spliced in as text.
     */
    public static String render(SourceLink source) {
        var text = render(source.module());
        if (source.aliases().isEmpty()) return text;
        var extendsEnd = text.indexOf("\n\n", text.indexOf("EXTENDS "));
        if (extendsEnd < 0) throw new IllegalStateException("rendered module has no EXTENDS clause");
        return text.substring(0, extendsEnd + 1) + "\n" + instanceDeclarations(source.aliases())
                + text.substring(extendsEnd + 1);
    }

    private static String instanceDeclarations(List<InstanceAlias> aliases) {
        var text = new StringBuilder();
        aliases.stream().map(alias -> alias.target().module()).distinct().forEach(module -> text
                .append(OperatorLibrary.instanceName(module)).append(" == INSTANCE ").append(module)
                .append('\n'));
        for (var alias : aliases) {
            var call = alias.instance() + "!" + alias.target().operator();
            if (alias.arity() == 0) {
                text.append(alias.name()).append(" == ").append(call).append('\n');
                continue;
            }
            var parameters = new StringBuilder();
            for (var index = 1; index <= alias.arity(); index++) {
                if (index > 1) parameters.append(", ");
                parameters.append(ALIAS_PARAMETER).append(index);
            }
            text.append(alias.name()).append('(').append(parameters).append(") == ")
                    .append(call).append('(').append(parameters).append(")\n");
        }
        return text.toString();
    }
}
