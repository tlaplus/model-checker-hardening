package io.github.tlaplus.hardening.pbt;

import io.github.tlaplus.hardening.config.ConfigException;
import io.github.tlaplus.hardening.config.FuzzTlaConfig;
import io.github.tlaplus.hardening.config.TomlConfig;
import io.github.tlaplus.hardening.gen.Generator;
import io.github.tlaplus.hardening.gen.InputRejectedException;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;
import java.util.regex.Pattern;

/** Owns the on-disk layout and integrity checks for one FuzzTLA corpus. */
public final class CorpusDirectory {
    public static final String CONFIG_FILE_NAME = "config.toml";
    public static final String INPUT_DIRECTORY_NAME = "00inputs";

    private static final Pattern INPUT_FILE_NAME = Pattern.compile("([0-9a-f]{64})\\.expr");
    private static final LinkOption[] NO_FOLLOW_LINKS = {LinkOption.NOFOLLOW_LINKS};

    private final Path root;
    private final Path configFile;
    private final Path inputDirectory;

    private CorpusDirectory(Path root) {
        this.root = root;
        this.configFile = root.resolve(CONFIG_FILE_NAME);
        this.inputDirectory = root.resolve(INPUT_DIRECTORY_NAME);
    }

    /** Initializes a config in a missing or existing corpus directory without overwriting it. */
    public static CorpusDirectory initialize(Path root) throws IOException, CorpusException {
        Objects.requireNonNull(root, "root");
        var corpus = new CorpusDirectory(root);
        if (Files.exists(root, NO_FOLLOW_LINKS)
                && !Files.isDirectory(root, NO_FOLLOW_LINKS)) {
            throw new CorpusException("corpus path is not a directory: " + root);
        }
        if (Files.exists(corpus.configFile, NO_FOLLOW_LINKS)) {
            throw new CorpusException("configuration already exists: " + corpus.configFile);
        }
        if (Files.exists(corpus.inputDirectory, NO_FOLLOW_LINKS)
                && !Files.isDirectory(corpus.inputDirectory, NO_FOLLOW_LINKS)) {
            throw new CorpusException(
                    "input path is not a directory: " + corpus.inputDirectory);
        }

        Files.createDirectories(root);
        if (!Files.exists(corpus.inputDirectory, NO_FOLLOW_LINKS)) {
            Files.createDirectory(corpus.inputDirectory);
        }
        TomlConfig.writeNew(corpus.configFile, FuzzTlaConfig.defaults());
        return corpus;
    }

    /** Opens an initialized corpus and checks the required path types. */
    public static CorpusDirectory open(Path root) throws CorpusException {
        Objects.requireNonNull(root, "root");
        var corpus = new CorpusDirectory(root);
        if (!Files.isDirectory(root, NO_FOLLOW_LINKS)) {
            throw new CorpusException("corpus directory does not exist: " + root);
        }
        if (!Files.isRegularFile(corpus.configFile, NO_FOLLOW_LINKS)) {
            throw new CorpusException("configuration file does not exist: " + corpus.configFile);
        }
        if (!Files.isDirectory(corpus.inputDirectory, NO_FOLLOW_LINKS)) {
            throw new CorpusException(
                    "input directory does not exist: " + corpus.inputDirectory);
        }
        return corpus;
    }

    /** Reads the strict TOML configuration belonging to this corpus. */
    public FuzzTlaConfig readConfig() throws IOException, ConfigException {
        return TomlConfig.read(configFile);
    }

    /** Verifies every existing input's name, digest, and acceptance under {@code generator}. */
    public long verify(Generator<?> generator) throws IOException, CorpusException {
        Objects.requireNonNull(generator, "generator");
        long count = 0;
        try (var paths = Files.list(inputDirectory)) {
            for (var path : paths.sorted().toList()) {
                verifyPath(path, generator);
                count++;
            }
        }
        return count;
    }

    /** Stores bytes under their digest and reports whether the content was already present. */
    public StoreResult store(byte[] input) throws IOException, CorpusException {
        Objects.requireNonNull(input, "input");
        var path = inputDirectory.resolve(hash(input) + ".expr");
        try {
            Files.write(
                    path,
                    input,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);
            return StoreResult.ADDED;
        } catch (FileAlreadyExistsException exception) {
            if (Files.isRegularFile(path, NO_FOLLOW_LINKS)
                    && Arrays.equals(input, Files.readAllBytes(path))) {
                return StoreResult.DUPLICATE;
            }
            throw new CorpusException("SHA-256 collision at corpus entry: " + path, exception);
        }
    }

    /** Returns the root path supplied when this corpus was opened. */
    public Path root() {
        return root;
    }

    /** Returns the directory containing raw generator inputs. */
    public Path inputDirectory() {
        return inputDirectory;
    }

    /** Checks one entry before it is included in the existing-entry count. */
    private static void verifyPath(Path path, Generator<?> generator)
            throws IOException, CorpusException {
        if (!Files.isRegularFile(path, NO_FOLLOW_LINKS)) {
            throw new CorpusException("corpus entry is not a regular file: " + path);
        }
        var matcher = INPUT_FILE_NAME.matcher(path.getFileName().toString());
        if (!matcher.matches()) {
            throw new CorpusException("invalid corpus entry name: " + path);
        }
        var input = Files.readAllBytes(path);
        if (!matcher.group(1).equals(hash(input))) {
            throw new CorpusException("corpus entry hash does not match its contents: " + path);
        }
        try {
            generator.generate(input);
        } catch (InputRejectedException exception) {
            throw new CorpusException(
                    "corpus entry is rejected: " + path + ": " + diagnostic(exception), exception);
        }
    }

    /** Computes the lowercase SHA-256 digest used as a corpus entry's basename. */
    private static String hash(byte[] input) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    /** Produces a useful one-line rejection diagnostic. */
    private static String diagnostic(Throwable exception) {
        var message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message;
    }

    public enum StoreResult {
        ADDED,
        DUPLICATE
    }
}
