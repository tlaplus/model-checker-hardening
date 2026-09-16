package io.github.tlaplus.hardening.workflow.quality;

import io.github.tlaplus.hardening.common.Diagnostics;
import io.github.tlaplus.hardening.config.MutatorConfig;
import io.github.tlaplus.hardening.corpus.CorpusDirectory;
import io.github.tlaplus.hardening.corpus.CorpusEnvelope;
import io.github.tlaplus.hardening.corpus.CorpusEnvelopeCodec;
import io.github.tlaplus.hardening.corpus.CorpusException;
import io.github.tlaplus.hardening.corpus.CorpusFormatException;
import io.github.tlaplus.hardening.corpus.CorpusStage;
import io.github.tlaplus.hardening.corpus.CorpusVerdict;
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
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Selects the parents of the next generation from one settled generation (ADR 0010).
 *
 * <p>The gate's input for generation g is every entry of g that reached {@code 03aggregator-pass},
 * including those an earlier, interrupted run of the gate already moved on. It keeps the best
 * {@code ceil(select_fraction × |input|)} admissible entries in {@code 04quality-pass} and moves
 * the rest to {@code 04quality-fail}. An entry is admissible when TLC measured its exploration, did
 * not fail, and matched no enabled shallow pattern.
 *
 * <p>Every pass is committed in rank order before any fail, so the entries an interruption leaves
 * behind are the lower-ranked tail, and running the gate again completes the same placement.
 */
public final class QualityGate {
    private final CorpusDirectory corpus;
    private final MutatorConfig config;
    private final StageCounters counters;

    public QualityGate(CorpusDirectory corpus, MutatorConfig config, StageCounters counters) {
        this.corpus = Objects.requireNonNull(corpus, "corpus");
        this.config = Objects.requireNonNull(config, "config");
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
            var passed = entries(CorpusStage.QUALITY, CorpusVerdict.PASS, generation).size();
            var failed = entries(CorpusStage.QUALITY, CorpusVerdict.FAIL, generation).size();
            var passesLeft = Math.max(0, selected(pending.size() + passed + failed) - passed);
            var kept = pending.stream()
                    .filter(this::admissible)
                    .sorted(Comparator.comparing(Candidate::ranked, QualityKey.BEST_FIRST))
                    .limit(passesLeft)
                    .toList();
            for (var entry : kept) {
                complete(entry, CorpusVerdict.PASS);
            }
            var keptSet = Set.copyOf(kept);
            for (var entry : pending) {
                if (!keptSet.contains(entry)) {
                    complete(entry, CorpusVerdict.FAIL);
                }
            }
        } finally {
            counters.elapsed().stop();
        }
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

    private void complete(Candidate candidate, CorpusVerdict verdict) throws IOException, CorpusException {
        var startTime = Instant.now();
        corpus.completeQuality(
                candidate.stored().path(),
                new StageResult(verdict, startTime, StageResult.endedNow(startTime)));
        counters.record(verdict);
    }

    private List<Candidate> entries(CorpusStage stage, CorpusVerdict verdict, int generation)
            throws IOException, CorpusException {
        var result = new ArrayList<Candidate>();
        for (var stored : corpus.resultEntries(stage, verdict)) {
            var envelope = decode(stored);
            var recorded = envelope.generation()
                    .map(metadata -> metadata.generation())
                    .orElse(OptionalInt.empty());
            if (recorded.isEmpty()) {
                throw new CorpusException("corpus entry records no generation: " + stored.path());
            }
            if (recorded.getAsInt() == generation) {
                result.add(new Candidate(stored, envelope, envelope.corpusInput().input().length));
            }
        }
        return result;
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

        QualityKey.Ranked ranked() {
            return new QualityKey.Ranked(tlc().metrics().orElseThrow(), inputBytes, stored.digest());
        }
    }
}
