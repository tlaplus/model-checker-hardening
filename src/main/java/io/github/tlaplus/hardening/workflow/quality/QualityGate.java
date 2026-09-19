package io.github.tlaplus.hardening.workflow.quality;

import io.github.tlaplus.hardening.common.Diagnostics;
import io.github.tlaplus.hardening.config.QualityGateConfig;
import io.github.tlaplus.hardening.corpus.CorpusDirectory;
import io.github.tlaplus.hardening.corpus.CorpusEnvelope;
import io.github.tlaplus.hardening.corpus.CorpusEnvelopeCodec;
import io.github.tlaplus.hardening.corpus.CorpusException;
import io.github.tlaplus.hardening.corpus.CorpusFormatException;
import io.github.tlaplus.hardening.corpus.CorpusStage;
import io.github.tlaplus.hardening.corpus.CorpusVerdict;
import io.github.tlaplus.hardening.corpus.InputAnalysis;
import io.github.tlaplus.hardening.corpus.StageMetadata;
import io.github.tlaplus.hardening.corpus.StageResult;
import io.github.tlaplus.hardening.corpus.StoredEntry;
import io.github.tlaplus.hardening.workflow.execution.StageCounters;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Selects the parents of the next generation from one settled generation (ADR 0010, ADR 0013).
 *
 * <p>The gate's input for generation g is every entry of g that reached {@code 03aggregator-pass},
 * including those an earlier, interrupted run of the gate already moved on. An entry is admissible
 * when TLC measured its exploration, did not fail, and matched no enabled shallow pattern. The gate
 * walks the admissible entries in rank order and keeps an entry in {@code 04quality-pass} while it
 * has kept fewer than {@code ceil(select_fraction × |input|)} and the {@link SelectionArchive} of
 * the entries kept so far, over every generation, admits it. The rest move to {@code
 * 04quality-fail}.
 *
 * <p>Every pass is committed in rank order before any fail. A decision depends only on the
 * entries kept by earlier generations and on the passes that rank above it, so after an
 * interruption the committed passes are a prefix of the passes, and running the gate again over
 * the committed passes and the pending entries completes the same placement.
 */
public final class QualityGate {
    private final CorpusDirectory corpus;
    private final QualityGateConfig config;
    private final InputAnalysis analysis;
    private final StageCounters counters;

    /**
     * @param analysis replays an input into the counts its coverage features derive from; called
     *     only when cells are bounded and coverage is enabled
     */
    public QualityGate(
            CorpusDirectory corpus, QualityGateConfig config, InputAnalysis analysis, StageCounters counters) {
        this.corpus = Objects.requireNonNull(corpus, "corpus");
        this.config = Objects.requireNonNull(config, "config");
        this.analysis = Objects.requireNonNull(analysis, "analysis");
        this.counters = Objects.requireNonNull(counters, "counters");
    }

    /** Places every entry of {@code generation} still waiting in {@code 03aggregator-pass}. */
    public void run(int generation) throws IOException, CorpusException {
        var pending = entries(CorpusStage.AGGREGATOR, CorpusVerdict.PASS, generation);
        if (pending.isEmpty()) {
            return;
        }
        counters.elapsed().start();
        try {
            var archive = new SelectionArchive(config);
            var passed = new ArrayList<Candidate>();
            for (var entry : entries(CorpusStage.QUALITY, CorpusVerdict.PASS)) {
                var recorded = generationOf(entry);
                if (recorded < generation) {
                    archive.keep(entry.cell(), features(archive, entry));
                } else if (recorded == generation) {
                    passed.add(entry);
                }
            }
            var failed = inGeneration(entries(CorpusStage.QUALITY, CorpusVerdict.FAIL), generation).size();
            var limit = selected(pending.size() + passed.size() + failed);
            var kept = select(archive, passed, pending, limit);
            for (var entry : kept) {
                complete(entry, CorpusVerdict.PASS);
            }
            var keptSet = new HashSet<>(kept);
            for (var entry : pending) {
                if (!keptSet.contains(entry)) {
                    complete(entry, CorpusVerdict.FAIL);
                }
            }
        } finally {
            counters.elapsed().stop();
        }
    }

    /**
     * Walks the committed passes and the admissible pending entries in rank order and returns the
     * pending entries to keep. The committed passes are a prefix of the passes, so once {@code
     * limit} entries are kept, none remains.
     */
    private List<Candidate> select(
            SelectionArchive archive, List<Candidate> committed, List<Candidate> pending, long limit) {
        var committedSet = Set.copyOf(committed);
        var ranked = Stream.concat(committed.stream(), pending.stream().filter(this::admissible))
                .sorted(Comparator.comparing(Candidate::ranked, QualityKey.BEST_FIRST))
                .toList();
        var kept = new ArrayList<Candidate>();
        var keptCount = 0L;
        for (var entry : ranked) {
            if (keptCount >= limit) {
                break;
            }
            var features = features(archive, entry);
            var isCommitted = committedSet.contains(entry);
            if (isCommitted || archive.admits(entry.cell(), features)) {
                archive.keep(entry.cell(), features);
                keptCount++;
                if (!isCommitted) {
                    kept.add(entry);
                }
            }
        }
        return kept;
    }

    /** Returns how many entries of a gate input of {@code size} entries the gate keeps. */
    long selected(long size) {
        return BigDecimal.valueOf(config.selectFraction())
                .multiply(BigDecimal.valueOf(size))
                .setScale(0, RoundingMode.CEILING)
                .longValueExact();
    }

    private boolean admissible(Candidate candidate) {
        var tlc = candidate.tlc();
        return tlc.verdict() != CorpusVerdict.FAIL
                && tlc.metrics().isPresent()
                && config.shallowPatterns().stream().noneMatch(pattern -> pattern.matches(tlc.record()));
    }

    /**
     * Returns the coverage features of an entry when the archive uses them, and none otherwise. An
     * input that fails to replay has none.
     */
    private Set<CoverageFeature> features(SelectionArchive archive, Candidate candidate) {
        if (!archive.usesCoverage()) {
            return Set.of();
        }
        try {
            return CoverageFeature.of(analysis.analyze(candidate.envelope().corpusInput()));
        } catch (RuntimeException | StackOverflowError failure) {
            return Set.of();
        }
    }

    private void complete(Candidate candidate, CorpusVerdict verdict) throws IOException, CorpusException {
        var startTime = Instant.now();
        corpus.completeQuality(
                candidate.stored().path(),
                new StageResult(verdict, startTime, StageResult.endedNow(startTime)));
        counters.record(verdict);
    }

    private List<Candidate> entries(CorpusStage stage, CorpusVerdict verdict, int generation)
            throws IOException, CorpusException {
        return inGeneration(entries(stage, verdict), generation);
    }

    private static List<Candidate> inGeneration(List<Candidate> entries, int generation)
            throws CorpusException {
        var result = new ArrayList<Candidate>();
        for (var entry : entries) {
            if (generationOf(entry) == generation) {
                result.add(entry);
            }
        }
        return result;
    }

    private List<Candidate> entries(CorpusStage stage, CorpusVerdict verdict)
            throws IOException, CorpusException {
        var result = new ArrayList<Candidate>();
        for (var stored : corpus.resultEntries(stage, verdict)) {
            var envelope = decode(stored);
            result.add(new Candidate(stored, envelope, envelope.corpusInput().input().length));
        }
        return result;
    }

    private static int generationOf(Candidate candidate) throws CorpusException {
        var recorded = candidate.envelope().generation()
                .map(metadata -> metadata.generation())
                .orElse(OptionalInt.empty());
        if (recorded.isEmpty()) {
            throw new CorpusException("corpus entry records no generation: " + candidate.stored().path());
        }
        return recorded.getAsInt();
    }

    private static CorpusEnvelope decode(StoredEntry stored) throws IOException, CorpusException {
        try {
            return CorpusEnvelopeCodec.decodeEnvelope(stored.read());
        } catch (CorpusFormatException exception) {
            throw new CorpusException(
                    "invalid corpus entry '" + stored.path() + "': " + Diagnostics.message(exception),
                    exception);
        }
    }

    /** One entry of the gate's input. */
    private record Candidate(StoredEntry stored, CorpusEnvelope envelope, int inputBytes) {
        StageMetadata tlc() {
            return envelope.stage(CorpusStage.TLC).orElseThrow();
        }

        QualityCell cell() {
            return QualityCell.of(tlc().verdict(), tlc().metrics().orElseThrow());
        }

        QualityKey.Ranked ranked() {
            return new QualityKey.Ranked(tlc().metrics().orElseThrow(), inputBytes, stored.digest());
        }
    }
}
