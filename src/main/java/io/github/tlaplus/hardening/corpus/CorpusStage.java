package io.github.tlaplus.hardening.corpus;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * One implemented stage of the fuzzing pipeline, and the corpus directories it owns.
 *
 * <p>This enum is the identity a stage is known by everywhere: the corpus keys inventories and
 * durable statistics by it, and the workflow keys its clocks, counters, and progress by it. Adding
 * a stage to the pipeline therefore starts here, with its metadata name and its directories.
 *
 * <p>Declaration order is pipeline order.
 */
public enum CorpusStage {
    PARSER(
            "parser",
            "parser",
            Passes.FAN_OUT,
            CorpusPath.INPUT,
            CorpusPath.PARSER_SCRATCH,
            CorpusPath.PARSER_PASS,
            CorpusPath.PARSER_FAIL,
            CorpusPath.PARSER_CRASH,
            true,
            FailureMetadata.FORBIDDEN),
    TLC(
            "tlc",
            "TLC",
            Passes.RETAINED,
            CorpusPath.TLC_INPUT,
            CorpusPath.TLC_SCRATCH,
            CorpusPath.TLC_PASS,
            CorpusPath.TLC_FAIL,
            CorpusPath.TLC_CRASH,
            true,
            FailureMetadata.REQUIRED_ON_FAIL),
    APALACHE(
            "apalache",
            "Apalache",
            Passes.RETAINED,
            CorpusPath.APALACHE_INPUT,
            CorpusPath.APALACHE_SCRATCH,
            CorpusPath.APALACHE_PASS,
            CorpusPath.APALACHE_FAIL,
            CorpusPath.APALACHE_CRASH,
            true,
            FailureMetadata.REQUIRED_ON_FAIL),
    AGGREGATOR(
            "aggregator",
            "aggregator",
            Passes.RETAINED,
            null,
            null,
            CorpusPath.AGGREGATOR_PASS,
            CorpusPath.AGGREGATOR_FAIL,
            null,
            false,
            FailureMetadata.FORBIDDEN);

    /** What becomes of an entry this stage passes. */
    private enum Passes {
        /** The pass is copied onward to the next stages and leaves this stage's directories. */
        FAN_OUT,
        /** The pass stays in this stage's result directory and occupies its capacity. */
        RETAINED
    }

    private enum FailureMetadata {
        FORBIDDEN,
        REQUIRED_ON_FAIL
    }

    private static final List<CorpusStage> CHECKER_BRANCHES = List.of(TLC, APALACHE);

    private final String metadataName;
    private final String displayName;
    private final Passes passes;
    private final CorpusPath input;
    private final CorpusPath scratch;
    private final CorpusPath pass;
    private final CorpusPath fail;
    private final CorpusPath crash;
    private final boolean configuredResultCapacity;
    private final FailureMetadata failureMetadata;

    CorpusStage(
            String metadataName,
            String displayName,
            Passes passes,
            CorpusPath input,
            CorpusPath scratch,
            CorpusPath pass,
            CorpusPath fail,
            CorpusPath crash,
            boolean configuredResultCapacity,
            FailureMetadata failureMetadata) {
        this.metadataName = metadataName;
        this.displayName = displayName;
        this.passes = passes;
        this.input = input;
        this.scratch = scratch;
        this.pass = pass;
        this.fail = fail;
        this.crash = crash;
        this.configuredResultCapacity = configuredResultCapacity;
        this.failureMetadata = failureMetadata;
    }

    /** Returns the stages that check a parser pass, in the order the parser fans out to them. */
    public static List<CorpusStage> checkerBranches() {
        return CHECKER_BRANCHES;
    }

    static Optional<CorpusStage> fromMetadataName(String metadataName) {
        return Arrays.stream(values())
                .filter(stage -> stage.metadataName.equals(metadataName))
                .findFirst();
    }

    /** Returns the name this stage records under {@code stages} in a corpus entry. */
    public String metadataName() {
        return metadataName;
    }

    /** Returns the name this stage is called by in messages and progress output. */
    public String displayName() {
        return displayName;
    }

    /**
     * Reports whether a passed entry stays in this stage's result directory. A parser pass does
     * not: it is copied to both checker branches and removed, so only failures and crashes occupy
     * the parser's result capacity.
     */
    public boolean retainsPasses() {
        return passes == Passes.RETAINED;
    }

    /** Returns the stages that own a durable input directory. */
    static List<CorpusStage> inputStages() {
        return Arrays.stream(values()).filter(stage -> stage.input != null).toList();
    }

    /** Returns the stages that need invocation-local scratch storage. */
    static List<CorpusStage> scratchStages() {
        return Arrays.stream(values()).filter(stage -> stage.scratch != null).toList();
    }

    /** Returns the stages whose result occupancy has its own configuration limit. */
    public static List<CorpusStage> capacityLimitedStages() {
        return Arrays.stream(values()).filter(stage -> stage.configuredResultCapacity).toList();
    }

    /** Returns the verdicts for which this stage owns a result directory. */
    public List<CorpusVerdict> resultVerdicts() {
        return crash == null
                ? List.of(CorpusVerdict.PASS, CorpusVerdict.FAIL)
                : List.of(CorpusVerdict.PASS, CorpusVerdict.FAIL, CorpusVerdict.CRASH);
    }

    CorpusPath input() {
        if (input == null) {
            throw new IllegalStateException(metadataName + " has no input directory");
        }
        return input;
    }

    CorpusPath scratch() {
        if (scratch == null) {
            throw new IllegalStateException(metadataName + " has no scratch directory");
        }
        return scratch;
    }

    CorpusPath result(CorpusVerdict verdict) {
        var path = switch (verdict) {
            case PASS -> pass;
            case FAIL -> fail;
            case CRASH -> crash;
        };
        if (path == null) {
            throw new IllegalArgumentException(
                    metadataName + " does not record " + verdict.encodedName() + " results");
        }
        return path;
    }

    /** Enforces the failure-metadata policy of a result written by this build. */
    void requireValidFailureMetadata(StageResult result) throws CorpusException {
        var hasFailure = result.failure().isPresent();
        if (failureMetadata == FailureMetadata.FORBIDDEN && hasFailure) {
            throw new CorpusException(displayName + " metadata must not contain a failure code");
        }
        if (failureMetadata == FailureMetadata.REQUIRED_ON_FAIL
                && result.verdict() == CorpusVerdict.FAIL
                && !hasFailure) {
            throw new CorpusException(displayName + " failure metadata requires a failure code");
        }
    }
}
