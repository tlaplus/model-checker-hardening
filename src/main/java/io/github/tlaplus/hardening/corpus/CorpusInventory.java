package io.github.tlaplus.hardening.corpus;

import java.nio.file.Path;
import java.util.List;

/** Immutable startup inventory used to seed stage queues and capacities. */
public record CorpusInventory(
        List<Path> inputs,
        List<Path> tlcInputs,
        List<Path> apalacheInputs,
        long parserPassEntries,
        long parserFailEntries,
        long parserCrashEntries,
        long tlcPassEntries,
        long tlcFailEntries,
        long tlcCrashEntries,
        long apalachePassEntries,
        long apalacheFailEntries,
        long apalacheCrashEntries) {
    public CorpusInventory {
        inputs = List.copyOf(inputs);
        tlcInputs = List.copyOf(tlcInputs);
        apalacheInputs = List.copyOf(apalacheInputs);
        if (parserPassEntries < 0
                || parserFailEntries < 0
                || parserCrashEntries < 0
                || tlcPassEntries < 0
                || tlcFailEntries < 0
                || tlcCrashEntries < 0
                || apalachePassEntries < 0
                || apalacheFailEntries < 0
                || apalacheCrashEntries < 0) {
            throw new IllegalArgumentException("corpus counters must be nonnegative");
        }
        if (parserPassEntries
                        != tlcInputs.size()
                                + tlcPassEntries
                                + tlcFailEntries
                                + tlcCrashEntries
                || parserPassEntries
                        != apalacheInputs.size()
                                + apalachePassEntries
                                + apalacheFailEntries
                                + apalacheCrashEntries) {
            throw new IllegalArgumentException(
                    "each parser pass must have one entry in each checker branch");
        }
    }

    public long inputEntries() {
        return inputs.size();
    }

    /** Number of logical inputs for which the parser has produced a verdict. */
    public long parserEntries() {
        return parserPassEntries + parserFailEntries + parserCrashEntries;
    }

    /** Current occupancy of parser-owned result directories after fan-out recovery. */
    public long parserResultEntries() {
        return parserFailEntries + parserCrashEntries;
    }

    public long tlcInputEntries() {
        return tlcInputs.size();
    }

    public long tlcEntries() {
        return tlcPassEntries + tlcFailEntries + tlcCrashEntries;
    }

    public long apalacheInputEntries() {
        return apalacheInputs.size();
    }

    public long apalacheEntries() {
        return apalachePassEntries + apalacheFailEntries + apalacheCrashEntries;
    }

    /** Counts one logical input once despite the two physical checker-branch copies. */
    public long totalEntries() {
        return inputEntries() + parserEntries();
    }
}
