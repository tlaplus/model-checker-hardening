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
            new StagePolicy(Passes.FAN_OUT, true, FailureMetadata.FORBIDDEN, PipelineRole.PARSING),
            Map.of(
                    CorpusVerdict.PASS, ResultMeaning.PASS,
                    CorpusVerdict.FAIL, ResultMeaning.FAIL,
                    CorpusVerdict.CRASH, ResultMeaning.CRASH)),
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
            new StagePolicy(Passes.RETAINED, true, FailureMetadata.REQUIRED_ON_FAIL, PipelineRole.CHECKING),
            Map.of(
                    CorpusVerdict.PASS, ResultMeaning.PASS,
                    CorpusVerdict.COUNTEREXAMPLE, ResultMeaning.COUNTEREXAMPLE,
                    CorpusVerdict.FAIL, ResultMeaning.FAIL,
                    CorpusVerdict.CRASH, ResultMeaning.CRASH)),
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
            new StagePolicy(Passes.RETAINED, true, FailureMetadata.REQUIRED_ON_FAIL, PipelineRole.CHECKING),
            Map.of(
                    CorpusVerdict.PASS, ResultMeaning.PASS,
                    CorpusVerdict.COUNTEREXAMPLE, ResultMeaning.COUNTEREXAMPLE,
                    CorpusVerdict.FAIL, ResultMeaning.FAIL,
                    CorpusVerdict.CRASH, ResultMeaning.CRASH)),
    AGGREGATOR(
            "aggregator",
            "aggregator",
            new StagePaths(
                    null,
                    null,
                    Map.of(
                            CorpusVerdict.PASS, CorpusPath.AGGREGATOR_PASS,
                            CorpusVerdict.FAIL, CorpusPath.AGGREGATOR_FAIL)),
            new StagePolicy(Passes.RETAINED, false, FailureMetadata.FORBIDDEN, PipelineRole.AGGREGATION),
            Map.of(CorpusVerdict.PASS, ResultMeaning.AGREE, CorpusVerdict.FAIL, ResultMeaning.DIFFER)),
    /**
     * The quality gate of ADR 0010. It consumes the aggregator's passes, which are its input
     * directory without being its own, and like the aggregator is bounded only by the global limit.
     */
    QUALITY(
            "quality",
            "quality",
            new StagePaths(
                    CorpusPath.AGGREGATOR_PASS,
                    null,
                    Map.of(
                            CorpusVerdict.PASS, CorpusPath.QUALITY_PASS,
                            CorpusVerdict.FAIL, CorpusPath.QUALITY_FAIL)),
            new StagePolicy(Passes.RETAINED, false, FailureMetadata.FORBIDDEN, PipelineRole.SELECTION),
            Map.of(CorpusVerdict.PASS, ResultMeaning.KEEP, CorpusVerdict.FAIL, ResultMeaning.DROP));

    /**
     * The meaning of a verdict at one stage, independent of its persisted encoding. Distinct from the
     * workflow's {@code StageOutcome}, which is a tool result before it becomes a corpus verdict.
     */
    public enum ResultMeaning {
        PASS("Pass"), COUNTEREXAMPLE("Cex"), FAIL("Fail"), CRASH("Crash"),
        AGREE("Agree"), DIFFER("Differ"), KEEP("Keep"), DROP("Drop");

        private final String label;

        ResultMeaning(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /**
     * What a stage contributes to one entry's journey through the pipeline. Generation scheduling
     * (ADR 0010) asks a stage for its role instead of naming stages, so adding a stage is a change
     * to this file alone.
     */
    public enum PipelineRole {
        /** Judges an admitted entry and fans its pass out to the checker branches. */
        PARSING,
        /** One branch that checks a parser pass; the branches run concurrently. */
        CHECKING,
        /** Compares the finished branches and gives the entry its terminal verdict. */
        AGGREGATION,
        /** Selects among entries that already settled; runs outside the per-entry pipeline. */
        SELECTION
    }

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
            FailureMetadata failureMetadata,
            PipelineRole role) {}

    private static final List<CorpusStage> CHECKER_BRANCHES =
            Arrays.stream(values()).filter(stage -> stage.role() == PipelineRole.CHECKING).toList();

    private final String metadataName;
    private final String displayName;
    private final StagePaths paths;
    private final StagePolicy policy;
    private final Map<CorpusVerdict, ResultMeaning> meanings;

    CorpusStage(String metadataName, String displayName, StagePaths paths, StagePolicy policy,
            Map<CorpusVerdict, ResultMeaning> meanings) {
        if (!meanings.keySet().equals(paths.results().keySet())) {
            throw new IllegalArgumentException(metadataName + " must give each recorded verdict a meaning");
        }
        this.metadataName = metadataName;
        this.displayName = displayName;
        this.paths = paths;
        this.policy = policy;
        this.meanings = Map.copyOf(meanings);
    }

    /** Returns the stage-specific meaning and display label of a recorded verdict. */
    public ResultMeaning meaning(CorpusVerdict verdict) {
        var meaning = meanings.get(Objects.requireNonNull(verdict, "verdict"));
        if (meaning == null) {
            throw new IllegalArgumentException(displayName + " does not record " + verdict);
        }
        return meaning;
    }

    /** Returns what this stage contributes to an entry's journey through the pipeline. */
    public PipelineRole role() {
        return policy.role();
    }

    /**
     * Reports whether this stage claims per-entry work that competes for the CPU budget, so its
     * queue and its budget requests are served oldest generation first (ADR 0010). Aggregation is
     * a single cheap worker and selection runs between generations, so neither is ordered.
     */
    public boolean ordersWorkByGeneration() {
        return role() == PipelineRole.PARSING || role() == PipelineRole.CHECKING;
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

    /**
     * Returns the stages that consume a durable input directory. A stage's input may be another
     * stage's result directory, as the quality gate's is.
     */
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
