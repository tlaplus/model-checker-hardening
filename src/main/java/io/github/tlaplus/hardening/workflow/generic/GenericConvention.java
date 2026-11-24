package io.github.tlaplus.hardening.workflow.generic;

import io.github.tlaplus.hardening.common.ArchRole;
import io.github.tlaplus.hardening.common.Key;
import io.github.tlaplus.hardening.common.Role;
import io.github.tlaplus.hardening.config.GenericCompatibility;
import io.github.tlaplus.hardening.workflow.apalache.ApalacheArguments;
import io.github.tlaplus.hardening.workflow.apalache.ApalacheOutcomeClassifier;
import io.github.tlaplus.hardening.workflow.spec.FuzzInputModule;
import io.github.tlaplus.hardening.workflow.tlc.TlcConfiguration;
import io.github.tlaplus.hardening.workflow.tlc.TlcOutcomeClassifier;
import io.github.tlaplus.hardening.workflow.worker.CheckRequest;
import io.github.tlaplus.hardening.workflow.worker.ToolResult;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * What a compatibility means to the generic checker (ADR 0033 §2): the configuration file written
 * as {@code {config}}, and how an exit status becomes a verdict. {@link GenericCompatibility} lives
 * in {@code config}, below the workflow, so its conventions are keyed by it here; loading this
 * class fails if a compatibility has none.
 *
 * @param configuration the configuration file of one invocation
 * @param classifier the verdict of an exit status and the tool's output
 */
@ArchRole(Role.POLICY)
@Key
record GenericConvention(Function<CheckRequest, String> configuration, Classifier classifier) {
    @FunctionalInterface
    interface Classifier {
        ToolResult classify(int exitStatus, String diagnostic);
    }

    private static final Map<GenericCompatibility, GenericConvention> CONVENTIONS = conventions();

    GenericConvention {
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(classifier, "classifier");
    }

    /** Returns the conventions of {@code compatibility}. */
    static GenericConvention of(GenericCompatibility compatibility) {
        return CONVENTIONS.get(Objects.requireNonNull(compatibility, "compatibility"));
    }

    private static Map<GenericCompatibility, GenericConvention> conventions() {
        var conventions = new EnumMap<GenericCompatibility, GenericConvention>(GenericCompatibility.class);
        conventions.put(GenericCompatibility.TLC,
                new GenericConvention(TlcConfiguration::text, TlcOutcomeClassifier::classifyCommandLine));
        conventions.put(GenericCompatibility.APALACHE,
                new GenericConvention(GenericConvention::apalacheConfiguration, ApalacheOutcomeClassifier::classify));
        if (conventions.size() != GenericCompatibility.values().length) {
            throw new ExceptionInInitializerError("every compatibility needs exactly one convention");
        }
        return Map.copyOf(conventions);
    }

    /**
     * Returns a TLC-syntax configuration naming the entry points that Apalache's command line names
     * (ADR 0033 §2): the requested side's {@code INIT} and {@code NEXT}, no fairness, and the
     * invariants and temporal property Apalache checks.
     */
    static String apalacheConfiguration(CheckRequest request) {
        var side = FuzzInputModule.entryPoints(request.kind().side());
        var text = new StringBuilder()
                .append("INIT ").append(side.init()).append('\n')
                .append("NEXT ").append(side.next()).append('\n');
        ApalacheArguments.invariants(request).forEach(invariant -> text.append("INVARIANT ").append(invariant).append('\n'));
        ApalacheArguments.temporal(request).ifPresent(property -> text.append("PROPERTY ").append(property).append('\n'));
        return text.toString();
    }
}
