package io.github.tlaplus.hardening.corpus;

import static io.github.tlaplus.hardening.corpus.CorpusLayout.NO_FOLLOW_LINKS;

import io.github.tlaplus.hardening.common.Diagnostics;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

/**
 * Owns the on-disk layout and integrity checks for one FuzzTLA corpus.
 *
 * <p>A {@code CorpusDirectory} is a path handle, not an open operating-system resource. Use it in
 * one of these flows:
 *
 * <p><strong>Initialization</strong>
 *
 * <ol>
 *   <li>Call {@link #initialize(Path)} once to create the required layout and default
 *       configuration.
 * </ol>
 *
 * <p><strong>Read-only access</strong>
 *
 * <ol>
 *   <li>Call {@link #openExisting(Path)} to check the fixed corpus layout.
 *   <li>Read {@link CorpusPath#CONFIG} through {@link #resolve(CorpusPath)} and perform the
 *       required read-only operation.
 * </ol>
 *
 * <p><strong>Workflow run</strong>
 *
 * <ol>
 *   <li>Call {@link #openExisting(Path)}, read the configuration, and construct the corresponding
 *       generator.
 *   <li>Acquire {@link #acquireExclusiveLock()} and hold the returned {@link CorpusLock} for the
 *       remainder of the run.
 *   <li>Call {@link #recoverAndValidate(CorpusEntryValidator)} to finish interrupted transitions,
 *       validate every entry, and obtain the initial {@link CorpusInventory}.
 *   <li>Call {@link #readRunStatistics()}, create the stage scratch directories, and run the
 *       stages while keeping the lock held for all corpus mutations.
 *   <li>Close the scratch handles and atomically call {@link
 *       #writeRunStatistics(CorpusRunStatistics)} before releasing the corpus lock.
 * </ol>
 *
 * <p>{@code openExisting} performs only the bounded layout check. It neither scans entries nor
 * changes the corpus. Recovery validates every stored payload, scans the complete corpus, and may
 * move entries; it therefore belongs inside the locked workflow run. Workflow statistics are a
 * separate aggregate and are never reconstructed from per-entry stage timestamps.
 *
 * <p>This class is the only entry point to the corpus. It serializes every mutation it performs,
 * and delegates to {@link CorpusLayout} for paths and file writes, {@link CorpusEntries} for
 * reading and validating entries, {@link CorpusEntryStore} for admitting generated inputs, {@link
 * StageTransition} for moving an entry between stage directories, and {@link CorpusRecovery} for
 * startup recovery and inventory.
 */
public final class CorpusDirectory {
    public static final String CRASH_REPORT_EXTENSION = CorpusLayout.CRASH_REPORT_EXTENSION;

    private final CorpusLayout layout;
    private final CorpusEntryStore store;
    private final StageTransition transitions;

    private CorpusDirectory(Path root) {
        layout = new CorpusLayout(root);
        store = new CorpusEntryStore(layout);
        transitions = new StageTransition(layout, entries(CorpusEntryValidator.NONE));
    }

    /**
     * Initializes the complete workflow layout, writing {@code configuration} as the corpus
     * configuration file. The corpus decides where that file lives and refuses to overwrite one that
     * already exists; its caller decides what the file says.
     */
    public static CorpusDirectory initialize(Path root, String configuration)
            throws IOException, CorpusException {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(configuration, "configuration");
        var corpus = new CorpusDirectory(root);
        var corpusRoot = corpus.resolve(CorpusPath.ROOT);
        var config = corpus.resolve(CorpusPath.CONFIG);
        if (Files.exists(corpusRoot, NO_FOLLOW_LINKS)
                && !Files.isDirectory(corpusRoot, NO_FOLLOW_LINKS)) {
            throw new CorpusException("corpus path is not a directory: " + root);
        }
        if (Files.exists(config, NO_FOLLOW_LINKS)) {
            throw new CorpusException("configuration already exists: " + config);
        }
        corpus.layout.requireAbsentOrDirectory(corpus.layout.requiredDirectories());

        for (var directory : corpus.layout.requiredDirectories()) {
            Files.createDirectories(directory);
        }
        Files.writeString(
                config,
                configuration,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE);
        return corpus;
    }

    /** Opens an initialized corpus and checks every required workflow path. */
    public static CorpusDirectory openExisting(Path root) throws CorpusException {
        Objects.requireNonNull(root, "root");
        var corpus = new CorpusDirectory(root);
        var corpusRoot = corpus.resolve(CorpusPath.ROOT);
        var config = corpus.resolve(CorpusPath.CONFIG);
        if (!Files.isDirectory(corpusRoot, NO_FOLLOW_LINKS)) {
            throw new CorpusException("corpus directory does not exist: " + root);
        }
        if (!Files.isRegularFile(config, NO_FOLLOW_LINKS)) {
            throw new CorpusException("configuration file does not exist: " + config);
        }
        for (var directory : corpus.layout.requiredDirectories()) {
            if (!Files.isDirectory(directory, NO_FOLLOW_LINKS)) {
                throw new CorpusException("workflow directory does not exist: " + directory);
            }
        }
        return corpus;
    }

    /** Reads cumulative workflow statistics, or returns zero statistics before the first save. */
    public synchronized CorpusRunStatistics readRunStatistics()
            throws IOException, CorpusException {
        var path = resolve(CorpusPath.WORKFLOW_STATISTICS);
        if (Files.notExists(path, NO_FOLLOW_LINKS)) {
            return CorpusRunStatistics.empty();
        }
        if (!Files.isRegularFile(path, NO_FOLLOW_LINKS)) {
            throw new CorpusException("workflow statistics path is not a regular file: " + path);
        }
        try {
            return CorpusRunStatisticsCodec.decode(Files.readAllBytes(path));
        } catch (CorpusStatisticsFormatException exception) {
            throw new CorpusException(
                    "invalid workflow statistics file '"
                            + path
                            + "': "
                            + Diagnostics.message(exception),
                    exception);
        }
    }

    /** Atomically replaces cumulative workflow statistics. The caller must hold the corpus lock. */
    public synchronized void writeRunStatistics(CorpusRunStatistics statistics)
            throws IOException {
        var encoded = CorpusRunStatisticsCodec.encode(
                Objects.requireNonNull(statistics, "statistics"));
        layout.replaceAtomically(
                resolve(CorpusPath.WORKFLOW_STATISTICS), "workflow-stats-", encoded);
    }

    /** Acquires the process-wide exclusive lock for this corpus. */
    public CorpusLock acquireExclusiveLock() throws IOException, CorpusException {
        var channel = FileChannel.open(
                resolve(CorpusPath.LOCK), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        try {
            var lock = channel.tryLock();
            if (lock == null) {
                channel.close();
                throw new CorpusException(
                        "corpus is already in use: " + resolve(CorpusPath.ROOT));
            }
            return new CorpusLock(channel, lock);
        } catch (OverlappingFileLockException exception) {
            channel.close();
            throw new CorpusException(
                    "corpus is already locked by this process: " + resolve(CorpusPath.ROOT),
                    exception);
        } catch (IOException | RuntimeException exception) {
            channel.close();
            throw exception;
        }
    }

    /** Creates every stage's transient storage for one locked workflow invocation. */
    public StageScratchSet createScratch() throws IOException, CorpusException {
        return StageScratchSet.create(this);
    }

    /**
     * Recovers durable transitions and returns a validated snapshot of the corpus. The validator
     * decides whether a stored payload is still usable by this build.
     */
    public synchronized CorpusInventory recoverAndValidate(CorpusEntryValidator validator)
            throws IOException, CorpusException {
        Objects.requireNonNull(validator, "validator");
        return new CorpusRecovery(layout, entries(validator), transitions).recover();
    }

    /** Stores an expression input under its payload digest in {@code 00-inputs}. */
    public synchronized StoreResult store(byte[] input) throws IOException, CorpusException {
        return store.store(input, null);
    }

    /** Stores an expression input and its admission-time PBT metadata. */
    public synchronized StoreResult store(byte[] input, GenerationMetadata generation)
            throws IOException, CorpusException {
        return store.store(input, Objects.requireNonNull(generation, "generation"));
    }

    /** Preserves an input and stack trace for an unexpected generator failure. */
    public synchronized Path recordGeneratorCrash(byte[] input, Throwable failure)
            throws IOException, CorpusException {
        return store.recordGeneratorCrash(input, failure);
    }

    /** Returns the canonical input-stage path for the supplied payload. */
    public Path inputPath(byte[] input) {
        return store.inputPath(input);
    }

    /** Reads an entry owned by the input directory of the parser stage. */
    public synchronized byte[] readExpressionInput(Path path)
            throws IOException, CorpusException {
        return entries(CorpusEntryValidator.NONE)
                .readOwnedExpressionInput(path, CorpusStage.PARSER);
    }

    /** Reads an entry owned by one checker's input directory. */
    public synchronized byte[] readCheckerExpressionInput(Path path)
            throws IOException, CorpusException {
        return entries(CorpusEntryValidator.NONE)
                .readOwnedExpressionInput(path, checkerStageFor(path));
    }

    /** Records a parser result and atomically moves it to its result directory. */
    public synchronized Path completeParser(Path source, StageResult result)
            throws IOException, CorpusException {
        return transitions.complete(source, CorpusStage.PARSER, result);
    }

    /** Records a checker result and atomically moves it to its result directory. */
    public synchronized Path completeChecker(Path source, StageResult result)
            throws IOException, CorpusException {
        return transitions.complete(source, checkerStageFor(source), result);
    }

    /** Copies one parser pass into both checker branches, then removes the fan-out source. */
    public synchronized void fanOutParserPass(Path source)
            throws IOException, CorpusException {
        transitions.fanOutParserPass(source);
    }

    /** Resolves a fixed corpus location. */
    public Path resolve(CorpusPath corpusPath) {
        return layout.resolve(Objects.requireNonNull(corpusPath, "corpusPath"));
    }

    private CorpusEntries entries(CorpusEntryValidator validator) {
        return new CorpusEntries(layout, validator, store);
    }

    synchronized StageScratch createScratch(CorpusStage stage)
            throws IOException, CorpusException {
        var directory = resolve(stage.scratch());
        if (Files.exists(directory, NO_FOLLOW_LINKS)
                && !Files.isDirectory(directory, NO_FOLLOW_LINKS)) {
            throw new CorpusException(
                    stage.displayName() + " scratch path is not a directory: " + directory);
        }
        return StageScratch.create(directory);
    }

    private CorpusStage checkerStageFor(Path path) throws CorpusException {
        Objects.requireNonNull(path, "path");
        var parent = path.toAbsolutePath().normalize().getParent();
        for (var checker : CorpusStage.checkerBranches()) {
            if (resolve(checker.input()).equals(parent)) {
                return checker;
            }
        }
        throw new CorpusException("entry is not owned by a checker input stage: " + path);
    }
}
