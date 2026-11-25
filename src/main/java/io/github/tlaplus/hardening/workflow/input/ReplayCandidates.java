package io.github.tlaplus.hardening.workflow.input;

import io.github.tlaplus.hardening.common.ArchRole;
import io.github.tlaplus.hardening.common.Diagnostics;
import io.github.tlaplus.hardening.common.Digests;
import io.github.tlaplus.hardening.common.Key;
import io.github.tlaplus.hardening.common.Role;
import io.github.tlaplus.hardening.corpus.CorpusDirectory;
import io.github.tlaplus.hardening.corpus.CorpusEnvelope;
import io.github.tlaplus.hardening.corpus.CorpusEnvelopeCodec;
import io.github.tlaplus.hardening.corpus.CorpusException;
import io.github.tlaplus.hardening.corpus.CorpusFormatException;
import io.github.tlaplus.hardening.corpus.CorpusStage;
import io.github.tlaplus.hardening.corpus.CorpusVerdict;
import io.github.tlaplus.hardening.corpus.GenerationMetadata;
import io.github.tlaplus.hardening.corpus.Mutation;
import io.github.tlaplus.hardening.corpus.StoredEntry;
import io.github.tlaplus.hardening.gen.InputRejectedException;
import io.github.tlaplus.hardening.gen.trace.TracePayload;
import io.github.tlaplus.hardening.mutation.MutationOperator;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.random.RandomGenerator;

/**
 * Replays one recorded counterexample (ADR 0034 §6): the single candidate of its target is the
 * {@code trace} payload of a parent's trace. It keeps its parent's cohort, records {@code replay} as
 * its provenance, and has no richness threshold. A target whose candidate is not stored, as a
 * duplicate or a quarantined known defect, is settled without an entry rather than retried.
 */
@ArchRole(Role.POLICY)
@Key
public final class ReplayCandidates implements CandidateSource {
    /** The aggregated directories, where the entries of a settled generation are. */
    private static final List<Aggregated> AGGREGATED = List.of(
            new Aggregated(CorpusStage.AGGREGATOR, CorpusVerdict.PASS),
            new Aggregated(CorpusStage.AGGREGATOR, CorpusVerdict.FAIL),
            new Aggregated(CorpusStage.QUALITY, CorpusVerdict.PASS),
            new Aggregated(CorpusStage.QUALITY, CorpusVerdict.FAIL));

    private record Aggregated(CorpusStage stage, CorpusVerdict verdict) {}

    private final String parent;
    private final int cohort;
    private final byte[] payload;

    ReplayCandidates(String parent, int cohort, byte[] payload) {
        this.parent = Objects.requireNonNull(parent, "parent");
        this.cohort = cohort;
        this.payload = Objects.requireNonNull(payload, "payload");
    }

    /**
     * Returns the replays of the traces that the checkers recorded on the generated entries of
     * {@code generation}, which must have settled, leaving out those {@code corpus} already holds.
     * They are ordered by parent digest and then by checker, in {@link CorpusStage} order, and a
     * trace two checkers recorded alike is replayed once. Nothing here draws randomness, so a
     * resumed run computes the same replays.
     */
    public static List<ReplayCandidates> collect(CorpusDirectory corpus, int generation)
            throws IOException, CorpusException {
        var parents = new ArrayList<StoredEntry>();
        for (var aggregated : AGGREGATED) {
            parents.addAll(corpus.resultEntries(aggregated.stage(), aggregated.verdict()));
        }
        parents.sort(Comparator.comparing(StoredEntry::digest));
        var replays = new LinkedHashMap<String, ReplayCandidates>();
        for (var stored : parents) {
            final CorpusEnvelope envelope;
            try {
                envelope = CorpusEnvelopeCodec.decodeEnvelope(stored.read());
            } catch (CorpusFormatException exception) {
                throw new CorpusException("invalid replay parent '" + stored.path() + "': "
                        + Diagnostics.message(exception), exception);
            }
            var input = envelope.corpusInput();
            var metadata = envelope.generation();
            var recorded = metadata.map(GenerationMetadata::generation).orElse(OptionalInt.empty());
            if (input.kind().derived() || recorded.isEmpty() || recorded.getAsInt() != generation) {
                continue;
            }
            var cohort = metadata.map(GenerationMetadata::cohort).orElse(0);
            for (var checker : CorpusStage.checkerBranches()) {
                var trace = envelope.stage(checker).flatMap(stage -> stage.trace());
                if (trace.isEmpty()) {
                    continue;
                }
                final byte[] payload;
                try {
                    payload = new TracePayload(input.kind(), input.input(),
                            TracePayload.decodeTrace(trace.get().bytes())).encode();
                } catch (InputRejectedException exception) {
                    throw new CorpusException("invalid trace of " + checker.metadataName() + " in '"
                            + stored.path() + "': " + exception.getMessage(), exception);
                }
                if (!corpus.contains(payload)) {
                    replays.putIfAbsent(Digests.digest(payload), new ReplayCandidates(stored.digest(), cohort, payload));
                }
            }
        }
        return List.copyOf(replays.values());
    }

    @Override
    public Target claim(RandomGenerator random) {
        return new Target() {
            @Override
            public Draft draw(RandomGenerator candidateRandom) {
                return new Draft(payload.clone(), cohort,
                        Optional.of(new Mutation(parent, List.of(MutationOperator.REPLAY))), candidate -> false);
            }

            @Override
            public double richnessThreshold() {
                return 0.0;
            }

            @Override
            public boolean retries() {
                return false;
            }

            @Override
            public String describe() {
                return "a replay of a counterexample of " + parent;
            }
        };
    }
}
