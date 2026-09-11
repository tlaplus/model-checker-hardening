package io.github.tlaplus.hardening.workflow.input;

import io.github.tlaplus.hardening.common.Diagnostics;
import io.github.tlaplus.hardening.common.Preconditions;
import io.github.tlaplus.hardening.corpus.CorpusException;
import io.github.tlaplus.hardening.corpus.GenerationMetadata;
import io.github.tlaplus.hardening.gen.Generator;
import io.github.tlaplus.hardening.gen.InputRejectedException;
import io.github.tlaplus.hardening.workflow.WorkflowException;
import io.github.tlaplus.hardening.workflow.execution.CpuBudget;
import io.github.tlaplus.hardening.workflow.execution.GeneratorStatistics;
import io.github.tlaplus.hardening.workflow.execution.GeneratorSummary;
import io.github.tlaplus.hardening.workflow.execution.StageEnvironment;
import io.github.tlaplus.hardening.workflow.execution.StageWorker;
import io.github.tlaplus.hardening.workflow.execution.WorkerGroup;
import io.github.tlaplus.hardening.workflow.execution.WorkflowStage;
import io.github.tlaplus.hardening.workflow.spec.SpecArtifact;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Concurrent property-based input generation stage.
 *
 * <p>Workers claim target entries dynamically. For each target a worker draws one richness cohort
 * and then candidates until one is stored in {@code 00-inputs}. Each candidate holds an input slot
 * while it is judged and keeps it only when it is stored.
 */
public final class PbtStage implements WorkflowStage {
    private static final long MAXIMUM_ATTEMPTS_PER_ENTRY = 10_000;

    private final InputAdmission admission;
    private final GenerationPlan plan;
    private final Generator<SpecArtifact> decoder;
    private final StageEnvironment environment;
    private final InputHandoff handoff;
    private final GeneratorStatistics statistics;
    private final AtomicLong nextTarget = new AtomicLong();
    private final WorkerGroup workers = new WorkerGroup("fuzztla-pbt-");

    public PbtStage(
            InputAdmission admission,
            GenerationPlan plan,
            StageEnvironment environment,
            InputHandoff handoff,
            GeneratorStatistics statistics) {
        this.admission = Objects.requireNonNull(admission, "admission");
        this.plan = Objects.requireNonNull(plan, "plan");
        this.environment = Objects.requireNonNull(environment, "environment");
        decoder = environment.decoders().decoder(plan.kind());
        this.handoff = Objects.requireNonNull(handoff, "handoff");
        this.statistics = Objects.requireNonNull(statistics, "statistics");
    }

    @Override
    public String name() {
        return "inputs";
    }

    @Override
    public void start() {
        var workerCount = Math.toIntExact(Math.min(plan.workerLimit(), plan.missingEntries()));
        var workerSeeds = workerSeeds(plan.seed(), workerCount);
        workers.start(
                workerCount,
                workerId -> () -> runWorker(workerId, workerSeeds[workerId]),
                handoff.queue()::close);
    }

    @Override
    public void await() throws InterruptedException {
        workers.await();
    }

    public GeneratorSummary summary() {
        return statistics.summary(plan.seed());
    }

    @Override
    public void close() {
        handoff.queue().close();
        workers.close();
    }

    private void runWorker(int workerId, long workerSeed) {
        StageWorker.run(
                environment.control(),
                "input generator worker " + workerId,
                () -> generateInputs(workerId, workerSeed));
    }

    /** Claims target entries until every one is claimed or the workflow stops. */
    private void generateInputs(int workerId, long workerSeed) throws Exception {
        var random = new SplittableRandom(workerSeed);
        var cohortRandom = random.split();
        var inputRandom = random.split();

        while (!environment.control().shouldStop()) {
            var target = nextTarget.getAndIncrement();
            if (target >= plan.missingEntries()) {
                return;
            }
            var cohort = cohortRandom.nextInt(admission.pbt().richnessCohorts());
            var entry = new EntryTarget(
                    workerId, workerSeed, target, cohort, admission.pbt().richnessThreshold(cohort));
            if (!generateEntry(entry, inputRandom)) {
                return;
            }
        }
    }

    /**
     * Draws candidates for one target entry until one is stored. Returns {@code false} when the
     * workflow stops first.
     */
    private boolean generateEntry(EntryTarget entry, SplittableRandom inputRandom)
            throws Exception {
        var progress = new EntryProgress();
        while (!environment.control().shouldStop()) {
            if (progress.attempts >= MAXIMUM_ATTEMPTS_PER_ENTRY) {
                throw exhausted(entry, progress);
            }
            if (!acquireInputCapacity()) {
                return false;
            }
            var outcome = Attempt.STOPPED;
            try {
                outcome = attempt(entry, inputRandom, progress);
            } finally {
                if (outcome != Attempt.STORED) {
                    handoff.capacity().release();
                }
            }
            if (outcome != Attempt.REJECTED) {
                return outcome == Attempt.STORED;
            }
        }
        return false;
    }

    /** What became of one candidate that held an input slot. */
    private enum Attempt {
        STORED,
        REJECTED,
        STOPPED
    }

    /** Draws and judges one candidate, timing everything but the wait for a CPU permit. */
    private Attempt attempt(
            EntryTarget entry, SplittableRandom inputRandom, EntryProgress progress)
            throws Exception {
        if (!environment.cpuBudget().acquire(
                CpuBudget.Priority.GENERATOR, 1, environment.control()::shouldStop)) {
            return Attempt.STOPPED;
        }
        statistics.elapsed().start();
        try {
            var candidate = draw(entry, inputRandom, progress);
            return candidate.isEmpty()
                    ? Attempt.REJECTED
                    : admit(entry, candidate.orElseThrow(), progress);
        } finally {
            statistics.elapsed().stop();
        }
    }

    /**
     * Draws and decodes one candidate while holding the CPU permit {@link #attempt} acquired, and
     * releases it. Returns empty when the decoder rejects the candidate.
     */
    private Optional<Candidate> draw(
            EntryTarget entry, SplittableRandom inputRandom, EntryProgress progress)
            throws WorkflowException {
        try {
            statistics.recordAttempt();
            progress.attempts++;
            var input = new byte[InputLengthSampler.sample(
                    inputRandom, admission.pbt().maximumInputBytes())];
            inputRandom.nextBytes(input);
            try {
                var artifact = decoder.generate(input);
                var richness = CollectionRichness.score(
                        artifact.generated(), admission.pbt().richnessNestingBase());
                return Optional.of(new Candidate(input, artifact, richness));
            } catch (InputRejectedException exception) {
                statistics.recordRejection();
                return Optional.empty();
            } catch (RuntimeException | StackOverflowError exception) {
                throw generatorCrash(entry, input, progress.attempts, exception);
            }
        } finally {
            environment.cpuBudget().release(1);
        }
    }

    /** Applies admission to a decoded candidate, and stores it when it is admitted. */
    private Attempt admit(EntryTarget entry, Candidate candidate, EntryProgress progress)
            throws IOException, CorpusException {
        progress.bestRichness = Math.max(progress.bestRichness, candidate.richness());
        switch (admission.decide(candidate.artifact(), candidate.richness(), entry.threshold())) {
            case InputAdmission.Decision.BelowRichnessThreshold _ -> {
                statistics.recordRichnessRejection();
                return Attempt.REJECTED;
            }
            case InputAdmission.Decision.ExceedsRequestFrame _ -> {
                statistics.recordRejection();
                return Attempt.REJECTED;
            }
            case InputAdmission.Decision.KnownDefect defect -> {
                progress.knownDefects++;
                statistics.recordKnownDefect(defect.primary());
                admission.quarantine(
                        plan.kind(),
                        candidate.input(),
                        new GenerationMetadata(
                                entry.cohort(), candidate.richness(), defect.signatures()));
                return Attempt.REJECTED;
            }
            case InputAdmission.Decision.Admitted _ -> {
                // Stored below.
            }
        }

        var corpus = environment.corpus();
        var generation = new GenerationMetadata(entry.cohort(), candidate.richness());
        return switch (corpus.store(plan.kind(), candidate.input(), generation)) {
            case ADDED -> {
                statistics.recordAdmission(candidate.richness());
                handoff.queue().submit(corpus.inputPath(candidate.input()));
                yield Attempt.STORED;
            }
            case DUPLICATE -> {
                statistics.recordDuplicate();
                yield Attempt.REJECTED;
            }
        };
    }

    private WorkflowException exhausted(EntryTarget entry, EntryProgress progress) {
        return new WorkflowException(
                entry.worker()
                        + " could not generate corpus entry "
                        + entryNumber(entry)
                        + " for richness cohort "
                        + entry.cohort()
                        + " (threshold "
                        + entry.threshold()
                        + ") within "
                        + MAXIMUM_ATTEMPTS_PER_ENTRY
                        + " attempts; best richness was "
                        + progress.bestRichness
                        + "; "
                        + progress.knownDefects
                        + " candidates matched known-defect signatures");
    }

    private WorkflowException generatorCrash(
            EntryTarget entry, byte[] input, long targetAttempt, Throwable failure) {
        var message = entry.worker()
                + " crashed while generating corpus entry "
                + entryNumber(entry)
                + " at target attempt "
                + targetAttempt
                + ": "
                + Diagnostics.message(failure);
        var diagnostic = environment.corpus()
                .preserveGeneratorCrash(plan.kind(), input, failure)
                .appendTo(message);
        return new WorkflowException(diagnostic, failure);
    }

    /** Returns the one-based ordinal of a target entry within the whole corpus. */
    private long entryNumber(EntryTarget entry) {
        return plan.initialEntries() + entry.target() + 1;
    }

    static long[] workerSeeds(long seed, int workerCount) {
        Preconditions.requireNonnegative(seed, "seed");
        Preconditions.requireNonnegative(workerCount, "workerCount");
        var seeds = new long[workerCount];
        var source = new SplittableRandom(seed);
        for (var workerId = 0; workerId < workerCount; workerId++) {
            seeds[workerId] = source.nextLong();
        }
        return seeds;
    }

    private boolean acquireInputCapacity() throws InterruptedException {
        while (!environment.control().shouldStop()) {
            if (handoff.capacity().tryAcquire(100, TimeUnit.MILLISECONDS)) {
                return true;
            }
        }
        return false;
    }

    /** One target entry a worker has claimed, and the threshold of the cohort drawn for it. */
    private record EntryTarget(
            int workerId, long workerSeed, long target, int cohort, double threshold) {
        /** Names the worker, and the seed that replays it, in a diagnostic. */
        String worker() {
            return "input generator worker " + workerId + " (seed " + workerSeed + ")";
        }
    }

    /** A decoded candidate and its collection-richness score. */
    private record Candidate(byte[] input, SpecArtifact artifact, double richness) {}

    /** What the attempts for one target entry have found so far, for its exhaustion diagnostic. */
    private static final class EntryProgress {
        private long attempts;
        private long knownDefects;
        private double bestRichness;
    }
}
