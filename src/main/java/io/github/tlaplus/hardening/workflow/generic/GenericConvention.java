package io.github.tlaplus.hardening.workflow.generic;

import io.github.tlaplus.hardening.checker.CheckerFailureCode;
import io.github.tlaplus.hardening.common.ArchRole;
import io.github.tlaplus.hardening.common.Key;
import io.github.tlaplus.hardening.common.Role;
import io.github.tlaplus.hardening.config.GenericCompatibility;
import io.github.tlaplus.hardening.config.GenericErrors;
import io.github.tlaplus.hardening.workflow.apalache.ApalacheArguments;
import io.github.tlaplus.hardening.workflow.apalache.ApalacheOutcomeClassifier;
import io.github.tlaplus.hardening.workflow.spec.FuzzInputModule;
import io.github.tlaplus.hardening.workflow.tlc.TlcCommandLineMetrics;
import io.github.tlaplus.hardening.workflow.tlc.TlcConfiguration;
import io.github.tlaplus.hardening.workflow.tlc.TlcOutcomeClassifier;
import io.github.tlaplus.hardening.workflow.worker.CheckRequest;
import io.github.tlaplus.hardening.workflow.worker.StageOutcome;
import io.github.tlaplus.hardening.workflow.worker.ToolResult;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * What a compatibility means to the generic checker (ADR 0033 §2): the configuration file written
 * as {@code {config}}, how an exit status becomes a verdict, and which exploration metrics the
 * tool's output gives (§5). {@link GenericCompatibility} lives in {@code config}, below the
 * workflow, so its conventions are keyed by it here; loading this class fails if a compatibility
 * has none.
 *
 * @param configuration the configuration file of one invocation
 * @param classifier the verdict of an exit status and the tool's output, when the exit statuses
 *     are compared exactly
 * @param metrics the classified result with the metrics that the tool's output records
 */
@ArchRole(Role.POLICY)
@Key
record GenericConvention(
        Function<CheckRequest, String> configuration, Classifier classifier, UnaryOperator<ToolResult> metrics) {
    @FunctionalInterface
    interface Classifier {
        ToolResult classify(int exitStatus, String diagnostic);
    }

    private static final Map<GenericCompatibility, GenericConvention> CONVENTIONS = conventions();

    GenericConvention {
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(classifier, "classifier");
        Objects.requireNonNull(metrics, "metrics");
    }

    /**
     * The exit statuses of a process killed by a fatal signal, 128 + the signal by the shell's
     * convention: SIGILL, SIGTRAP, SIGABRT, SIGBUS, SIGFPE, SIGKILL and SIGSEGV. Other statuses above
     * 128 are a tool's own, such as TLC's 150 for a parse error.
     */
    private static final Set<Integer> FATAL_SIGNAL_STATUSES = Set.of(132, 133, 134, 135, 136, 137, 139);

    /**
     * Returns how an exit status becomes a verdict under {@code errors}: by this compatibility's
     * classifier, or by {@link #classifyNonzero} (ADR 0033 §7).
     */
    Classifier classifier(GenericErrors errors) {
        return switch (Objects.requireNonNull(errors, "errors")) {
            case EXACT -> classifier;
            case NONZERO -> GenericConvention::classifyNonzero;
        };
    }

    /**
     * Classifies a tool compared by its exit status alone: 0 passes, a process killed by a fatal
     * signal crashed, and any other status is an {@link CheckerFailureCode#ERROR}, which the oracle
     * matches with a counterexample or a failure of another checker.
     */
    static ToolResult classifyNonzero(int exitStatus, String diagnostic) {
        if (exitStatus == 0) {
            return new ToolResult(StageOutcome.PASS, diagnostic);
        }
        if (FATAL_SIGNAL_STATUSES.contains(exitStatus)) {
            return new ToolResult(StageOutcome.CRASH, diagnostic);
        }
        return ToolResult.failure(CheckerFailureCode.ERROR, diagnostic);
    }

    /** Returns the conventions of {@code compatibility}. */
    static GenericConvention of(GenericCompatibility compatibility) {
        return CONVENTIONS.get(Objects.requireNonNull(compatibility, "compatibility"));
    }

    private static Map<GenericCompatibility, GenericConvention> conventions() {
        var conventions = new EnumMap<GenericCompatibility, GenericConvention>(GenericCompatibility.class);
        conventions.put(GenericCompatibility.TLC,
                new GenericConvention(TlcConfiguration::text, TlcOutcomeClassifier::classifyCommandLine,
                        TlcCommandLineMetrics::attach));
        conventions.put(GenericCompatibility.APALACHE,
                new GenericConvention(GenericConvention::apalacheConfiguration, ApalacheOutcomeClassifier::classify,
                        UnaryOperator.identity()));
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
