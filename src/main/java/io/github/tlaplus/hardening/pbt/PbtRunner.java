package io.github.tlaplus.hardening.pbt;

import io.github.tlaplus.hardening.config.FuzzTlaConfig;
import io.github.tlaplus.hardening.config.PbtConfig;
import io.github.tlaplus.hardening.corpus.CorpusDirectory;
import io.github.tlaplus.hardening.corpus.CorpusException;
import io.github.tlaplus.hardening.gen.Generator;
import io.github.tlaplus.hardening.gen.InputRejectedException;
import io.github.tlaplus.hardening.gen.IrGenerators;
import java.io.IOException;
import java.util.Objects;
import java.util.SplittableRandom;

/** Populates a verified corpus with reproducible pseudorandom generator inputs. */
public final class PbtRunner {
    private final PbtConfig config;
    private final Generator<?> generator;

    /** Creates a runner using the complete corpus and IR generation configuration. */
    public PbtRunner(FuzzTlaConfig config) {
        Objects.requireNonNull(config, "config");
        this.config = config.pbt();
        this.generator = IrGenerators.expressions(config.generator());
    }

    /** Allows tests in this package to substitute a small purpose-built generator. */
    PbtRunner(PbtConfig config, Generator<?> generator) {
        this.config = Objects.requireNonNull(config, "config");
        this.generator = Objects.requireNonNull(generator, "generator");
    }

    /** Verifies the existing corpus, then fills it to the configured target. */
    public PbtRunSummary run(CorpusDirectory corpus, long seed)
            throws IOException, CorpusException, PbtException {
        Objects.requireNonNull(corpus, "corpus");
        if (seed < 0) {
            throw new IllegalArgumentException("seed must be nonnegative");
        }
        var existing = corpus.verify(generator);
        var missing = Math.max(0L, config.corpusEntries() - existing);
        var maximumAttempts = Math.max(10_000L, 100L * missing);
        var random = new SplittableRandom(seed);
        long added = 0;
        long attempts = 0;
        long rejected = 0;
        long duplicates = 0;

        while (added < missing) {
            if (attempts >= maximumAttempts) {
                var summary =
                        new PbtRunSummary(seed, existing, added, attempts, rejected, duplicates);
                throw new PbtException(
                        "could not reach "
                                + config.corpusEntries()
                                + " corpus entries within "
                                + maximumAttempts
                                + " attempts",
                        summary);
            }

            attempts++;
            var length = InputLengthSampler.sample(random, config.maximumInputBytes());
            var input = new byte[length];
            random.nextBytes(input);
            try {
                generator.generate(input);
            } catch (InputRejectedException exception) {
                rejected++;
                continue;
            }

            switch (corpus.store(input)) {
                case ADDED -> added++;
                case DUPLICATE -> duplicates++;
            }
        }

        return new PbtRunSummary(seed, existing, added, attempts, rejected, duplicates);
    }
}
