package io.github.tlaplus.hardening.corpus;

import static io.github.tlaplus.hardening.corpus.CorpusLayout.ENTRY_FILE_NAME;
import static io.github.tlaplus.hardening.corpus.CorpusLayout.NO_FOLLOW_LINKS;

import io.github.tlaplus.hardening.common.Diagnostics;
import io.github.tlaplus.hardening.corpus.CorpusEntries.Entry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Joins the checker copies of one logical input into one durable aggregator result. */
final class AggregationTransition {
    private final CorpusLayout layout;
    private final CorpusEntries entries;

    AggregationTransition(CorpusLayout layout, CorpusEntries entries) {
        this.layout = Objects.requireNonNull(layout, "layout");
        this.entries = Objects.requireNonNull(entries, "entries");
    }

    /** Returns a ready non-crash pair named by a checker completion notification. */
    Optional<AggregationInput> find(Path candidate) throws IOException, CorpusException {
        Objects.requireNonNull(candidate, "candidate");
        var name = candidate.getFileName().toString();
        if (!ENTRY_FILE_NAME.matcher(name).matches()) {
            throw new CorpusException("invalid corpus entry name: " + candidate);
        }
        var branches = branchResults(name);
        if (branches.size() != CorpusStage.checkerBranches().size()
                || branches.stream().anyMatch(branch -> branch.verdict() == CorpusVerdict.CRASH)) {
            return Optional.empty();
        }
        requireCompatibleBranches(name, branches);
        var verdicts = new EnumMap<CorpusStage, CorpusVerdict>(CorpusStage.class);
        branches.forEach(branch -> verdicts.put(branch.stage(), branch.verdict()));
        return Optional.of(new AggregationInput(candidate, verdicts));
    }

    /** Installs one merged result before removing either checker source. */
    Path complete(AggregationInput input, StageResult result)
            throws IOException, CorpusException {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(result, "result");
        CorpusStage.AGGREGATOR.requireValidFailureMetadata(result);
        if (!CorpusStage.AGGREGATOR.resultVerdicts().contains(result.verdict())) {
            throw new CorpusException("aggregator cannot record " + result.verdict().encodedName());
        }
        if (result.verdict() != input.conformanceVerdict()) {
            throw new CorpusException(
                    "aggregator verdict does not match checker verdicts: "
                            + input.candidate().getFileName());
        }

        var name = input.candidate().getFileName().toString();
        var branches = branchResults(name);
        if (branches.size() != CorpusStage.checkerBranches().size()) {
            throw new CorpusException("checker results are no longer ready for aggregation: " + name);
        }
        requireCompatibleBranches(name, branches);
        var actual = new EnumMap<CorpusStage, CorpusVerdict>(CorpusStage.class);
        branches.forEach(branch -> actual.put(branch.stage(), branch.verdict()));
        if (!actual.equals(input.checkerVerdicts())) {
            throw new CorpusException("checker results changed before aggregation: " + name);
        }

        for (var branch : branches) {
            CorpusEntries.requireMissingStage(branch.entry(), CorpusStage.AGGREGATOR);
        }
        var destination = layout.resolve(CorpusStage.AGGREGATOR.result(result.verdict())).resolve(name);
        if (Files.exists(destination, NO_FOLLOW_LINKS)) {
            throw new CorpusException("aggregator destination already exists: " + destination);
        }

        final byte[] merged;
        try {
            merged = CorpusEnvelopeCodec.mergeWithStageMetadata(
                    branches.stream().map(branch -> branch.entry().encoded()).toList(),
                    new StageMetadata(
                            CorpusStage.AGGREGATOR.metadataName(),
                            result.verdict(),
                            result.startTime(),
                            result.endTime(),
                            result.failure()));
        } catch (CorpusFormatException exception) {
            throw new CorpusException(
                    "cannot merge checker results for "
                            + name
                            + ": "
                            + Diagnostics.message(exception),
                    exception);
        }

        layout.createAtomically(destination, "aggregate-", merged);
        for (var branch : branches) {
            Files.delete(branch.entry().path());
        }
        return destination;
    }

    /** Validates and returns residual sources of a durably installed aggregate destination. */
    List<Path> residualSources(
            Entry aggregate, CorpusVerdict expected, AggregationInput aggregation)
            throws IOException, CorpusException {
        var name = aggregate.path().getFileName().toString();
        CorpusEntries.requireStageVerdict(aggregate, CorpusStage.AGGREGATOR, expected);
        if (!aggregate.path().equals(aggregation.candidate())) {
            throw new IllegalArgumentException("aggregation input does not name its destination");
        }
        var residual = new ArrayList<Path>();
        for (var checker : CorpusStage.checkerBranches()) {
            var source = findResult(name, checker);
            if (source.isEmpty()) {
                continue;
            }
            var branch = source.orElseThrow();
            CorpusEntries.requireSameParserOutput(name, aggregate, branch.entry());
            CorpusEntries.requireMissingStage(branch.entry(), CorpusStage.AGGREGATOR);
            for (var otherChecker : CorpusStage.checkerBranches()) {
                if (otherChecker != checker) {
                    CorpusEntries.requireMissingStage(branch.entry(), otherChecker);
                }
            }
            var metadata = aggregate.envelope().stage(checker).orElseThrow();
            if (!metadata.equals(branch.entry().envelope().stage(checker).orElseThrow())) {
                throw new CorpusException(
                        "aggregator destination disagrees with " + checker.displayName()
                                + " source: " + name);
            }
            residual.add(branch.entry().path());
        }
        return List.copyOf(residual);
    }

    /** Validates and returns the non-crash checker verdicts carried by an aggregate entry. */
    Map<CorpusStage, CorpusVerdict> upstreamCheckerVerdicts(Entry aggregate)
            throws CorpusException {
        CorpusEntries.requireStageVerdict(
                aggregate, CorpusStage.PARSER, CorpusVerdict.PASS);
        var verdicts = new EnumMap<CorpusStage, CorpusVerdict>(CorpusStage.class);
        for (var checker : CorpusStage.checkerBranches()) {
            var metadata = aggregate.envelope().stage(checker).orElseThrow(() ->
                    new CorpusException("aggregator entry is missing " + checker.metadataName()
                            + " metadata: " + aggregate.path()));
            if (metadata.verdict() == CorpusVerdict.CRASH) {
                throw new CorpusException(
                        "aggregator entry contains a crashed checker result: " + aggregate.path());
            }
            verdicts.put(checker, metadata.verdict());
        }
        return Map.copyOf(verdicts);
    }

    private List<BranchResult> branchResults(String name) throws IOException, CorpusException {
        var results = new ArrayList<BranchResult>();
        for (var checker : CorpusStage.checkerBranches()) {
            findResult(name, checker).ifPresent(results::add);
        }
        return List.copyOf(results);
    }

    private Optional<BranchResult> findResult(String name, CorpusStage checker)
            throws IOException, CorpusException {
        BranchResult found = null;
        for (var verdict : checker.resultVerdicts()) {
            var path = layout.resolve(checker.result(verdict)).resolve(name);
            if (!Files.exists(path, NO_FOLLOW_LINKS)) {
                continue;
            }
            if (found != null) {
                throw new CorpusException(
                        checker.displayName() + " entry appears in multiple result directories: "
                                + name);
            }
            var entry = entries.verify(path);
            CorpusEntries.requireStageVerdict(entry, checker, verdict);
            found = new BranchResult(checker, verdict, entry);
        }
        return Optional.ofNullable(found);
    }

    private static void requireCompatibleBranches(String name, List<BranchResult> branches)
            throws CorpusException {
        Entry reference = null;
        for (var branch : branches) {
            CorpusEntries.requireStageVerdict(
                    branch.entry(), CorpusStage.PARSER, CorpusVerdict.PASS);
            for (var checker : CorpusStage.checkerBranches()) {
                if (checker != branch.stage()) {
                    CorpusEntries.requireMissingStage(branch.entry(), checker);
                }
            }
            if (reference == null) {
                reference = branch.entry();
            } else {
                CorpusEntries.requireSameParserOutput(name, reference, branch.entry());
            }
        }
    }

    private record BranchResult(CorpusStage stage, CorpusVerdict verdict, Entry entry) {}
}
