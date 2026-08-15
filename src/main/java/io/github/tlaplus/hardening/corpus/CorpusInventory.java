package io.github.tlaplus.hardening.corpus;

import java.nio.file.Path;
import java.util.List;

/** Immutable startup inventory used to seed stage queues and capacities. */
public record CorpusInventory(
        List<Path> inputs,
        long parserPassEntries,
        long parserFailEntries,
        long parserCrashEntries) {
    public CorpusInventory {
        inputs = List.copyOf(inputs);
        if (parserPassEntries < 0 || parserFailEntries < 0 || parserCrashEntries < 0) {
            throw new IllegalArgumentException("corpus counters must be nonnegative");
        }
    }

    public long inputEntries() {
        return inputs.size();
    }

    public long parserEntries() {
        return parserPassEntries + parserFailEntries + parserCrashEntries;
    }

    public long totalEntries() {
        return inputEntries() + parserEntries();
    }
}
