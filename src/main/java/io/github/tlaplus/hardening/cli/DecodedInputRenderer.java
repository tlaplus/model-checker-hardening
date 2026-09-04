package io.github.tlaplus.hardening.cli;

import io.github.tlaplus.hardening.workflow.apalache.ApalacheIrJson;
import io.github.tlaplus.hardening.workflow.spec.SpecArtifact;
import io.github.tlaplus.hardening.workflow.spec.SpecText;
import java.util.Objects;

/** Renders a decoded input independently of picocli command wiring. */
final class DecodedInputRenderer {
    enum Mode {
        DEFAULT,
        SPECIFICATION,
        APALACHE_IR
    }

    private DecodedInputRenderer() {}

    static String render(SpecArtifact artifact, Mode mode) {
        Objects.requireNonNull(artifact, "artifact");
        Objects.requireNonNull(mode, "mode");
        return switch (mode) {
            case DEFAULT -> artifact.standaloneExpression()
                    .map(EnvelopeReport::expression)
                    .orElseGet(() -> SpecText.render(artifact.module()));
            case SPECIFICATION -> SpecText.render(artifact.module());
            case APALACHE_IR -> ApalacheIrJson.render(artifact.module());
        };
    }
}
