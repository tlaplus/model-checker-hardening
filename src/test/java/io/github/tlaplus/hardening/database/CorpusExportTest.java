package io.github.tlaplus.hardening.database;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tlaplus.hardening.checker.CheckerFailure;
import io.github.tlaplus.hardening.checker.CheckerFailureCode;
import io.github.tlaplus.hardening.checker.ExplorationCount;
import io.github.tlaplus.hardening.checker.ExplorationMetrics;
import io.github.tlaplus.hardening.checker.ExplorationPhase;
import io.github.tlaplus.hardening.common.Digests;
import io.github.tlaplus.hardening.corpus.CorpusDirectory;
import io.github.tlaplus.hardening.corpus.CorpusEnvelopeCodec;
import io.github.tlaplus.hardening.corpus.CorpusException;
import io.github.tlaplus.hardening.corpus.CorpusInput;
import io.github.tlaplus.hardening.corpus.CorpusInputCodec;
import io.github.tlaplus.hardening.corpus.CorpusPath;
import io.github.tlaplus.hardening.corpus.CorpusVerdict;
import io.github.tlaplus.hardening.corpus.GenerationMetadata;
import io.github.tlaplus.hardening.corpus.StageMetadata;
import io.github.tlaplus.hardening.corpus.StageRecord;
import io.github.tlaplus.hardening.gen.InputKind;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CorpusExportTest {
    private static final Instant START = Instant.parse("2026-09-15T10:00:00Z");
    private static final CorpusExport.Provenance PROVENANCE = new CorpusExport.Provenance(
            "fuzztla test", Instant.parse("2026-09-15T12:00:00.5Z"));

    @Test
    void exportsEntriesStageRecordsMetricsAndKnownDefects(@TempDir Path directory)
            throws Exception {
        var root = corpus(directory);
        var aggregated = store(root, CorpusPath.AGGREGATOR_PASS, InputKind.MODULE, "aggregated",
                Optional.of(new GenerationMetadata(3, 7.25)),
                stage("parser", new StageRecord(CorpusVerdict.PASS, START, START)),
                stage("tlc", new StageRecord(
                        CorpusVerdict.COUNTEREXAMPLE,
                        START,
                        START.plusSeconds(1),
                        Optional.empty(),
                        Optional.of(ExplorationMetrics.builder()
                                .phase(ExplorationPhase.EXPLORE)
                                .count(ExplorationCount.INIT_STATES, 1)
                                .count(ExplorationCount.DEPTH, 2)
                                .count(ExplorationCount.TRACE_LENGTH, 2)
                                .saturated(true)
                                .build()))),
                stage("apalache", new StageRecord(
                        CorpusVerdict.FAIL,
                        START,
                        START.plusSeconds(3),
                        Optional.of(new CheckerFailure(
                                CheckerFailureCode.SPEC_EVAL, Optional.of("Mod by zero"))),
                        Optional.of(ExplorationMetrics.builder().build()))),
                stage("aggregator", new StageRecord(CorpusVerdict.FAIL, START, START)));
        var quarantined = store(root, CorpusPath.KNOWN_DEFECTS, InputKind.EXPRESSION, "quarantined",
                Optional.of(new GenerationMetadata(0, 1.0, List.of("modulo-by-zero", "string-set"))));
        var crashed = store(root, CorpusPath.TLC_CRASH, InputKind.MODULE, "crashed",
                Optional.empty(),
                stage("tlc", new StageRecord(CorpusVerdict.CRASH, START, START.plusSeconds(60))));
        Files.writeString(resolve(root, CorpusPath.TLC_CRASH, crashed + ".stacktrace"), "trace");
        var corrupt = Digests.digest("corrupt".getBytes(StandardCharsets.UTF_8));
        Files.write(resolve(root, CorpusPath.PARSER_FAIL, corrupt + ".cbor"), new byte[] {1, 2, 3});
        var output = directory.resolve("corpus.sqlite");

        var summary = run(root, output, false, true);

        assertEquals(new CorpusExport.Summary(output, 3, 1, 0, 0), summary);
        try (var connection = connect(output)) {
            assertEquals(
                    List.of(
                            row("00-known-defects", quarantined, "expr", 11L, 0L, 1.0),
                            row("02tlc-crash", crashed, "module", 7L, null, null),
                            row("03aggregator-pass", aggregated, "module", 10L, 3L, 7.25)),
                    rows(connection,
                            "SELECT directory, hash, kind, inputBytes, cohort, richness"
                                    + " FROM entry ORDER BY id"));
            assertEquals(
                    List.of(
                            row("00-known-defects", 11L, null),
                            row("02tlc-crash", 7L, null),
                            row("03aggregator-pass", 10L, null)),
                    rows(connection,
                            "SELECT directory, evaluatedNodes, replayError FROM entry ORDER BY id"));
            assertEquals(
                    List.of(row("EQ", 1L), row("SET_ENUM", 10L)),
                    rows(connection,
                            "SELECT o.name, o.occurrences FROM operator o JOIN entry e ON e.id = o.entryId"
                                    + " WHERE e.hash = '" + aggregated + "' ORDER BY o.name"));
            assertEquals(
                    List.of(row("modulo-by-zero", 0L), row("string-set", 1L)),
                    rows(connection, "SELECT signature, position FROM knownDefect ORDER BY position"));
            assertEquals(
                    List.of(
                            row("aggregator", "fail", 0L),
                            row("apalache", "fail", 3_000L),
                            row("parser", "pass", 0L),
                            row("tlc", "counterexample", 1_000L)),
                    rows(connection,
                            "SELECT s.stage, s.verdict, s.durationMillis FROM stage s"
                                    + " JOIN entry e ON e.id = s.entryId"
                                    + " WHERE e.hash = '" + aggregated + "' ORDER BY s.stage"));
            assertEquals(
                    List.of(row(
                            "2026-09-15T10:00:00.000Z",
                            "2026-09-15T10:00:01.000Z",
                            "explore", 1L, 1L, 2L, 2L, null)),
                    rows(connection,
                            "SELECT startTime, endTime, phase, saturated, initStates, depth,"
                                    + " traceLength, distinctStates FROM stage"
                                    + " WHERE stage = 'tlc' AND verdict = 'counterexample'"));
            assertEquals(
                    List.of(row(75L, "Mod by zero", null, 0L, null)),
                    rows(connection,
                            "SELECT code, detail, phase, saturated, traceLength FROM stage"
                                    + " WHERE stage = 'apalache'"));
            assertEquals(
                    List.of(row(null, null, null)),
                    rows(connection,
                            "SELECT code, phase, saturated FROM stage WHERE verdict = 'crashed'"));
            assertEquals(
                    List.of(row(aggregated, 3L, "fail", "counterexample", null, 2L, "fail", 75L, null)),
                    rows(connection,
                            "SELECT hash, cohort, aggregator, tlc, tlcCode, tlcTraceLength,"
                                    + " apalache, apalacheCode, apalacheTraceLength FROM verdictPair"));
            var unreadable = rows(connection, "SELECT directory, hash, error FROM unreadable");
            assertEquals(1, unreadable.size());
            assertEquals(row("01parser-fail", corrupt), unreadable.getFirst().subList(0, 2));
            assertFalse(((String) unreadable.getFirst().get(2)).isBlank());
            assertEquals(
                    List.of(
                            row("corpus", root.toAbsolutePath().normalize().toString()),
                            row("exportedAt", "2026-09-15T12:00:00.500Z"),
                            row("fuzztlaVersion", "fuzztla test")),
                    rows(connection, "SELECT key, value FROM export ORDER BY key"));
        }
    }

    @Test
    void refusesToReplaceAnExistingDatabaseUnlessAsked(@TempDir Path directory) throws Exception {
        var root = corpus(directory);
        store(root, CorpusPath.INPUT, InputKind.EXPRESSION, "input", Optional.empty());
        var output = directory.resolve("existing.sqlite");
        var previous = "not a database".getBytes(StandardCharsets.UTF_8);
        Files.write(output, previous);

        assertThrows(
                CorpusDatabaseException.class,
                () -> run(root, output, false, true));
        assertArrayEquals(previous, Files.readAllBytes(output));

        assertEquals(1, run(root, output, true, true).entries());
        try (var connection = connect(output)) {
            assertEquals(List.of(List.of(1L)), rows(connection, "SELECT count(*) FROM entry"));
        }
        try (var files = Files.list(directory)) {
            assertEquals(List.of(output), files.filter(Files::isRegularFile).toList());
        }
    }

    @Test
    void takesTheCorpusLockUnlessToldNotTo(@TempDir Path directory) throws Exception {
        var root = corpus(directory);
        store(root, CorpusPath.INPUT, InputKind.EXPRESSION, "input", Optional.empty());
        var output = directory.resolve("locked.sqlite");

        try (var lock = CorpusDirectory.openExisting(root).acquireExclusiveLock()) {
            assertThrows(
                    CorpusException.class, () -> run(root, output, false, true));
            assertFalse(Files.exists(output));

            assertEquals(1, run(root, output, false, false).entries());
        }
        assertTrue(Files.exists(output));
    }

    private static Path corpus(Path directory) throws Exception {
        var root = directory.resolve("corpus");
        CorpusDirectory.initialize(root, "");
        return root;
    }

    @Test
    void recordsInputsThatCannotBeReplayedAndContinues(@TempDir Path directory) throws Exception {
        var root = corpus(directory);
        var rejected = store(root, CorpusPath.INPUT, InputKind.EXPRESSION, "boom", Optional.empty());
        var deep = store(root, CorpusPath.PARSER_FAIL, InputKind.EXPRESSION, "deep", Optional.empty());
        var replayed = store(root, CorpusPath.PARSER_CRASH, InputKind.EXPRESSION, "fine", Optional.empty());
        var output = directory.resolve("failures.sqlite");

        var summary = run(root, output, false, true);

        assertEquals(new CorpusExport.Summary(output, 3, 0, 2, 0), summary);
        try (var connection = connect(output)) {
            assertEquals(
                    List.of(
                            row(rejected, null, "no replay for boom"),
                            row(deep, null, "StackOverflowError"),
                            row(replayed, 4L, null)),
                    rows(connection, "SELECT hash, evaluatedNodes, replayError FROM entry ORDER BY id"));
            assertEquals(
                    List.of(row(replayed)),
                    rows(connection,
                            "SELECT DISTINCT e.hash FROM operator o JOIN entry e ON e.id = o.entryId"));
        }
    }

    @Test
    void writesTheSameDatabaseForAnyNumberOfThreads(@TempDir Path directory) throws Exception {
        var root = corpus(directory);
        // More entries than one chunk, so that chunk boundaries are exercised too.
        for (var index = 0; index < EntryBatchExporter.CHUNK_SIZE + 7; index++) {
            var location = index % 3 == 0 ? CorpusPath.AGGREGATOR_PASS : CorpusPath.AGGREGATOR_FAIL;
            store(root, location, InputKind.MODULE, "input-" + index + "x".repeat(index % 11),
                    Optional.of(new GenerationMetadata(index % 10, index)));
        }
        var contents = new ArrayList<List<List<Object>>>();

        for (var threads : List.of(1, 4)) {
            var output = directory.resolve("threads-" + threads + ".sqlite");
            var options = new CorpusExport.Options(root, output, false, true, PROVENANCE);
            CorpusExport.run(options, new CorpusExport.Analysis(CorpusExportTest::analyze, threads));
            try (var connection = connect(output)) {
                var tables = new ArrayList<List<Object>>();
                tables.addAll(rows(connection, "SELECT * FROM entry ORDER BY id"));
                tables.addAll(rows(connection, "SELECT * FROM operator ORDER BY entryId, name"));
                contents.add(tables);
            }
        }

        assertEquals(EntryBatchExporter.CHUNK_SIZE + 7 + 2 * (EntryBatchExporter.CHUNK_SIZE + 7),
                contents.getFirst().size());
        assertEquals(contents.getFirst(), contents.getLast());
    }

    private static CorpusExport.Summary run(Path root, Path output, boolean replace, boolean lock)
            throws Exception {
        return CorpusExport.run(
                new CorpusExport.Options(root, output, replace, lock, PROVENANCE),
                new CorpusExport.Analysis(CorpusExportTest::analyze, 2));
    }

    /**
     * A stand-in for replay: the node count is the payload length, and every payload has one
     * equality and a set enumeration per byte. Payloads {@code boom} and {@code deep} fail.
     */
    private static InputFeatures analyze(CorpusInput input) {
        var payload = new String(input.input(), StandardCharsets.UTF_8);
        return switch (payload) {
            case "boom" -> throw new IllegalArgumentException("no replay for boom");
            case "deep" -> throw new StackOverflowError();
            default -> new InputFeatures(
                    payload.length(), Map.of("EQ", 1L, "SET_ENUM", (long) payload.length()));
        };
    }

    private static StageMetadata stage(String name, StageRecord record) {
        return new StageMetadata(name, record);
    }

    /** Writes an entry with the given stage records and returns its digest. */
    private static String store(
            Path root,
            CorpusPath location,
            InputKind kind,
            String payload,
            Optional<GenerationMetadata> generation,
            StageMetadata... stages)
            throws Exception {
        var input = new CorpusInput(kind, payload.getBytes(StandardCharsets.UTF_8));
        var encoded = generation.isPresent()
                ? CorpusInputCodec.encode(input, generation.get())
                : CorpusInputCodec.encode(input);
        for (var stage : stages) {
            encoded = CorpusEnvelopeCodec.withStageMetadata(encoded, stage);
        }
        var digest = Digests.digest(input.input());
        var file = resolve(root, location, digest + ".cbor");
        Files.createDirectories(file.getParent());
        Files.write(file, encoded);
        return digest;
    }

    private static Path resolve(Path root, CorpusPath location, String fileName) {
        return root.resolve(location.relativePath()).resolve(fileName);
    }

    private static Connection connect(Path file) throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + file);
    }

    private static List<List<Object>> rows(Connection connection, String query) throws SQLException {
        var rows = new ArrayList<List<Object>>();
        try (var statement = connection.createStatement();
                ResultSet result = statement.executeQuery(query)) {
            var columns = result.getMetaData().getColumnCount();
            while (result.next()) {
                var row = new ArrayList<Object>();
                for (var column = 1; column <= columns; column++) {
                    var value = result.getObject(column);
                    row.add(value instanceof Integer integer ? Long.valueOf(integer) : value);
                }
                rows.add(row);
            }
        }
        return rows;
    }

    /** A row of expected values; unlike {@code List.of}, it admits SQL {@code NULL}. */
    private static List<Object> row(Object... values) {
        return Arrays.asList(values);
    }
}
