package io.github.tlaplus.hardening.workflow.input;

import io.github.tlaplus.hardening.common.Diagnostics;
import io.github.tlaplus.hardening.common.Preconditions;
import io.github.tlaplus.hardening.corpus.CorpusException;
import io.github.tlaplus.hardening.corpus.EntryName;
import io.github.tlaplus.hardening.gen.InputRejectedException;
import io.github.tlaplus.hardening.workflow.WorkflowException;
import io.github.tlaplus.hardening.workflow.execution.CpuBudget;
import io.github.tlaplus.hardening.workflow.execution.GeneratorStatistics;
import io.github.tlaplus.hardening.workflow.execution.StageEnvironment;
import io.github.tlaplus.hardening.workflow.execution.StageWorker;
import io.github.tlaplus.hardening.workflow.execution.WorkQueue;
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
 * Concurrent input stage: admits reserved target ranges from adjacent generations.
 *
 * <p>The stage lives for the whole invocation. The coordinator {@link #submit}s one
 * {@link GenerationPlan} per contiguous target range — a generation's mutant prefix or PBT suffix
 * (ADR 0010) — and calls {@link #finish} when there will be no more. Ranges from two adjacent
 * generations may be open at once.
 *
 * <p>Workers claim target entries dynamically. The plan names the source of each target; a worker
 * claims the target from that source, then draws candidates until one is stored in {@code
 * 00-inputs}. Each candidate holds an input slot while it is judged and keeps it only when it is
 * stored. Every source's candidates are decoded, admitted and stored the same way.
 *
 * <p>Randomness belongs to the target, not to the worker: a target's claim and candidates come from
 * a stream seeded by the generation seed and the target's ordinal. Which worker claims a target,
 * and so the number of workers, does not change what the target admits, except when two targets
 * draw the same bytes and the one that stores them first is decided by scheduling.
 */
public final class InputStage implements WorkflowStage {
    private static final long MAXIMUM_ATTEMPTS_PER_ENTRY = 10_000;

    private final InputAdmission admission;
    private final int workerLimit;
    private final WorkQueue<TargetRange> targets = new WorkQueue<>();
    private final StageEnvironment environment;
    private final InputHandoff handoff;
    private final GeneratorStatistics statistics;
    private final WorkerGroup workers = new WorkerGroup("fuzztla-input-");

    public InputStage(
            InputAdmission admission,
            int workerLimit,
            StageEnvironment environment,
            InputHandoff handoff,
            GeneratorStatistics statistics) {
        this.admission = Objects.requireNonNull(admission, "admission");
        Preconditions.requirePositive(workerLimit, "workerLimit");
        this.workerLimit = workerLimit;
        this.environment = Objects.requireNonNull(environment, "environment");
        this.handoff = Objects.requireNonNull(handoff, "handoff");
        this.statistics = Objects.requireNonNull(statistics, "statistics");
        environment.control().onStop(targets::close);
    }

    @Override
    public String name() {
        return "inputs";
    }

    @Override
    public void start() {
        workers.start(workerLimit, workerId -> () -> runWorker(workerId), handoff.queue()::close);
    }

    /**
     * Adds one contiguous target range without changing its generation-local ordinals.
     *
     * <p>The same range is queued once per worker that may share it, up to {@code workerLimit}
     * copies, because workers claim targets from it concurrently through {@link TargetRange}'s
     * counter. Queuing it once would let only one worker fill it. A worker that draws an exhausted
     * copy takes the next one, so extra copies cost only a queue round.
     */
    public void submit(GenerationPlan plan) {
        Objects.requireNonNull(plan, "plan");
        var range = new TargetRange(plan);
        for (long worker = 0; worker < Math.min(workerLimit, plan.missingEntries()); worker++) {
            if (!targets.submit(range)) {
                // The run is stopping; the workers are on their way out and need no more work.
                return;
            }
        }
    }

    /** Signals that the coordinator will submit no more targets. */
    public void finish() {
        targets.close();
    }

    @Override
    public void await() throws InterruptedException {
        workers.await();
    }

    @Override
    public void close() {
        targets.close();
        handoff.queue().close();
        workers.close();
    }

    private void runWorker(int workerId) {
        StageWorker.run(
                environment.control(),
                "input generator worker " + workerId,
                () -> generateInputs(workerId));
    }

    /** Claims target entries until every one is claimed or the workflow stops. */
    private void generateInputs(int workerId) throws Exception {
        while (!environment.control().shouldStop()) {
            var range = targets.take();
            if (range == null) {
                return;
            }
            var plan = range.plan;
            while (!environment.control().shouldStop()) {
                var target = range.nextTarget.getAndIncrement();
                if (target >= plan.missingEntries()) {
                    break;
                }
                var targetSeed = derivedSeed(plan.seed(), plan.firstTarget() + target);
                var random = new SplittableRandom(targetSeed);
                var entry = new EntryTarget(
                        workerId, targetSeed, plan.firstTarget() + target, plan,
                        plan.source(target).claim(random.split()));
                if (!generateEntry(entry, random.split())) {
                    return;
                }
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
                CpuBudget.Priority.GENERATOR, entry.plan().generation(), 1,
                environment.control()::shouldStop)) {
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
     * releases it. Returns empty when the decoder rejects the candidate or it is a clone of its
     * parent.
     */
    private Optional<Candidate> draw(
            EntryTarget entry, SplittableRandom inputRandom, EntryProgress progress)
            throws WorkflowException {
        try {
            statistics.recordAttempt();
            progress.attempts++;
            var draft = entry.claimed().draw(inputRandom);
            try {
                var artifact = environment.decoders().decoder(entry.plan().kind()).generate(draft.input());
                if (draft.isClone().test(artifact)) {
                    statistics.recordClone();
                    progress.clones++;
                    return Optional.empty();
                }
                var richness = CollectionRichness.score(
                        artifact.generated(), admission.pbt().richnessNestingBase());
                return Optional.of(new Candidate(draft, artifact, richness));
            } catch (InputRejectedException exception) {
                statistics.recordRejection();
                return Optional.empty();
            } catch (RuntimeException | StackOverflowError exception) {
                throw generatorCrash(entry, draft.input(), progress.attempts, exception);
            }
        } finally {
            environment.cpuBudget().release(1);
        }
    }

    /** Applies admission to a decoded candidate, and stores it when it is admitted. */
    private Attempt admit(EntryTarget entry, Candidate candidate, EntryProgress progress)
            throws IOException, CorpusException {
        progress.bestRichness = Math.max(progress.bestRichness, candidate.richness());
        var plan = entry.plan();
        var generation = candidate.draft().metadata(plan.generation(), candidate.richness());
        var input = candidate.draft().input();
        switch (admission.decide(
                candidate.artifact(), candidate.richness(), entry.claimed().richnessThreshold())) {
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
                        plan.kind(), input, generation.withKnownDefects(defect.signatures()));
                return Attempt.REJECTED;
            }
            case InputAdmission.Decision.Admitted _ -> {
                // Stored below.
            }
        }

        var corpus = environment.corpus();
        return switch (corpus.store(plan.kind(), input, generation)) {
            case ADDED -> {
                statistics.recordAdmission(candidate.richness());
                var path = corpus.inputPath(input);
                // Reported only now: store() has written the entry, so the coordinator that counts
                // admissions never counts one that is not yet durable.
                environment.events().admitted(
                        EntryName.of(path), plan.generation(), generation.mutation().isPresent());
                handoff.queue().submit(path);
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
                        + " for "
                        + entry.claimed().describe()
                        + " within "
                        + MAXIMUM_ATTEMPTS_PER_ENTRY
                        + " attempts; best richness was "
                        + progress.bestRichness
                        + "; "
                        + progress.knownDefects
                        + " candidates matched known-defect signatures; "
                        + progress.clones
                        + " were clones of their parent");
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
                .preserveGeneratorCrash(entry.plan().kind(), input, failure)
                .appendTo(message);
        return new WorkflowException(diagnostic, failure);
    }

    /**
     * Returns the one-based ordinal a target entry occupies in the corpus, counted nominally: its
     * generation's first ordinal plus its position in that generation. Generations that admitted
     * fewer entries than {@code generation_size} leave gaps.
     */
    private long entryNumber(EntryTarget entry) {
        return entry.plan().initialEntries() + entry.target() + 1;
    }

    /** Returns the seed of one generation's input stage, derived from the run seed. */
    public static long generationSeed(long seed, int generation) {
        Preconditions.requireNonnegative(generation, "generation");
        return derivedSeed(seed, generation);
    }

    /**
     * Returns a nonnegative seed for the child {@code index} of {@code seed}. Unlike seeds taken
     * from one stream in order, a child is computed directly from its index, so it does not depend
     * on how many other children were derived before it.
     */
    static long derivedSeed(long seed, long index) {
        Preconditions.requireNonnegative(seed, "seed");
        Preconditions.requireNonnegative(index, "index");
        return mix(seed + mix(index + 1)) & Long.MAX_VALUE;
    }

    /** The 64-bit finalizer of MurmurHash3, which spreads every input bit over the result. */
    private static long mix(long value) {
        var mixed = (value ^ (value >>> 33)) * 0xff51afd7ed558ccdL;
        mixed = (mixed ^ (mixed >>> 33)) * 0xc4ceb9fe1a85ec53L;
        return mixed ^ (mixed >>> 33);
    }

    private boolean acquireInputCapacity() throws InterruptedException {
        while (!environment.control().shouldStop()) {
            if (handoff.capacity().tryAcquire(100, TimeUnit.MILLISECONDS)) {
                return true;
            }
        }
        return false;
    }

    /** One target entry a worker has claimed from its source, and the seed of its stream. */
    private record EntryTarget(
            int workerId, long targetSeed, long target, GenerationPlan plan,
            CandidateSource.Target claimed) {
        /** Names the worker, and the seed that replays the target, in a diagnostic. */
        String worker() {
            return "input generator worker " + workerId + " (target seed " + targetSeed + ")";
        }
    }

    private static final class TargetRange {
        final GenerationPlan plan;
        final AtomicLong nextTarget = new AtomicLong();

        TargetRange(GenerationPlan plan) {
            this.plan = plan;
        }
    }

    /** A decoded candidate and its collection-richness score. */
    private record Candidate(CandidateSource.Draft draft, SpecArtifact artifact, double richness) {}

    /** What the attempts for one target entry have found so far, for its exhaustion diagnostic. */
    private static final class EntryProgress {
        private long attempts;
        private long knownDefects;
        private long clones;
        private double bestRichness;
    }
}
