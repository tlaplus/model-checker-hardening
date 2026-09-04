package io.github.tlaplus.hardening.corpus;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
            new StagePaths(
                    CorpusPath.INPUT,
                    CorpusPath.PARSER_SCRATCH,
                    Map.of(
                            CorpusVerdict.PASS, CorpusPath.PARSER_PASS,
                            CorpusVerdict.FAIL, CorpusPath.PARSER_FAIL,
                            CorpusVerdict.CRASH, CorpusPath.PARSER_CRASH)),
            new StagePolicy(Passes.FAN_OUT, true, FailureMetadata.FORBIDDEN)),
    TLC(
            "tlc",
            "TLC",
            new StagePaths(
                    CorpusPath.TLC_INPUT,
                    CorpusPath.TLC_SCRATCH,
                    Map.of(
                            CorpusVerdict.PASS, CorpusPath.TLC_PASS,
                            CorpusVerdict.COUNTEREXAMPLE, CorpusPath.TLC_COUNTEREXAMPLE,
                            CorpusVerdict.FAIL, CorpusPath.TLC_FAIL,
                            CorpusVerdict.CRASH, CorpusPath.TLC_CRASH)),
            new StagePolicy(Passes.RETAINED, true, FailureMetadata.REQUIRED_ON_FAIL)),
    APALACHE(
            "apalache",
            "Apalache",
            new StagePaths(
                    CorpusPath.APALACHE_INPUT,
                    CorpusPath.APALACHE_SCRATCH,
                    Map.of(
                            CorpusVerdict.PASS, CorpusPath.APALACHE_PASS,
                            CorpusVerdict.COUNTEREXAMPLE, CorpusPath.APALACHE_COUNTEREXAMPLE,
                            CorpusVerdict.FAIL, CorpusPath.APALACHE_FAIL,
                            CorpusVerdict.CRASH, CorpusPath.APALACHE_CRASH)),
            new StagePolicy(Passes.RETAINED, true, FailureMetadata.REQUIRED_ON_FAIL)),
    AGGREGATOR(
            "aggregator",
            "aggregator",
            new StagePaths(
                    null,
                    null,
                    Map.of(
                            CorpusVerdict.PASS, CorpusPath.AGGREGATOR_PASS,
                            CorpusVerdict.FAIL, CorpusPath.AGGREGATOR_FAIL)),
            new StagePolicy(Passes.RETAINED, false, FailureMetadata.FORBIDDEN));

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

    private record StagePaths(
            CorpusPath input,
            CorpusPath scratch,
            Map<CorpusVerdict, CorpusPath> results) {
        private StagePaths {
            Objects.requireNonNull(results, "results");
            var copy = new EnumMap<CorpusVerdict, CorpusPath>(CorpusVerdict.class);
            copy.putAll(results);
            results = Map.copyOf(copy);
        }
    }

    private record StagePolicy(
            Passes passes,
            boolean configuredResultCapacity,
            FailureMetadata failureMetadata) {}

    private static final List<CorpusStage> CHECKER_BRANCHES = List.of(TLC, APALACHE);

    private final String metadataName;
    private final String displayName;
    private final StagePaths paths;
    private final StagePolicy policy;

    CorpusStage(String metadataName, String displayName, StagePaths paths, StagePolicy policy) {
        this.metadataName = metadataName;
        this.displayName = displayName;
        this.paths = paths;
        this.policy = policy;
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
        return policy.passes() == Passes.RETAINED;
    }

    /** Returns the stages that own a durable input directory. */
    static List<CorpusStage> inputStages() {
        return Arrays.stream(values()).filter(stage -> stage.paths.input() != null).toList();
    }

    /** Returns the stages that need invocation-local scratch storage. */
    static List<CorpusStage> scratchStages() {
        return Arrays.stream(values()).filter(stage -> stage.paths.scratch() != null).toList();
    }

    /** Returns the stages whose result occupancy has its own configuration limit. */
    public static List<CorpusStage> capacityLimitedStages() {
        return Arrays.stream(values())
                .filter(stage -> stage.policy.configuredResultCapacity())
                .toList();
    }

    /** Returns the verdicts for which this stage owns a result directory. */
    public List<CorpusVerdict> resultVerdicts() {
        return Arrays.stream(CorpusVerdict.values())
                .filter(paths.results()::containsKey)
                .toList();
    }

    CorpusPath input() {
        if (paths.input() == null) {
            throw new IllegalStateException(metadataName + " has no input directory");
        }
        return paths.input();
    }

    CorpusPath scratch() {
        if (paths.scratch() == null) {
            throw new IllegalStateException(metadataName + " has no scratch directory");
        }
        return paths.scratch();
    }

    CorpusPath result(CorpusVerdict verdict) {
        var path = paths.results().get(Objects.requireNonNull(verdict, "verdict"));
        if (path == null) {
            throw new IllegalArgumentException(
                    metadataName + " does not record " + verdict.encodedName() + " results");
        }
        return path;
    }

    /** Enforces the verdict and failure-metadata policy of a result written by this build. */
    void requireValidResult(StageResult result) throws CorpusException {
        if (!paths.results().containsKey(result.verdict())) {
            throw new CorpusException(
                    displayName + " cannot record " + result.verdict().encodedName());
        }
        var hasFailure = result.failure().isPresent();
        if (policy.failureMetadata() == FailureMetadata.FORBIDDEN && hasFailure) {
            throw new CorpusException(displayName + " metadata must not contain a failure code");
        }
        if (policy.failureMetadata() == FailureMetadata.REQUIRED_ON_FAIL
                && result.verdict() == CorpusVerdict.FAIL
                && !hasFailure) {
            throw new CorpusException(displayName + " failure metadata requires a failure code");
        }
    }
}
