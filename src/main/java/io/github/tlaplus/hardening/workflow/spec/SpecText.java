package io.github.tlaplus.hardening.workflow.spec;

import at.forsyte.apalache.tla.lir.TlaModule;
import io.github.tlaplus.hardening.workflow.worker.ToolWorkerProtocol;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.apalache_mc.tla.jio.TlaText;

/** Renders an assembled module as the TLA+ source that the parser and TLC consume. */
public final class SpecText {
    private static final TlaText TEXT = new TlaText(80, 2);

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
}
