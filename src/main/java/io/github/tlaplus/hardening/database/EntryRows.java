package io.github.tlaplus.hardening.database;

import static io.github.tlaplus.hardening.database.DatabaseColumns.CODE;
import static io.github.tlaplus.hardening.database.DatabaseColumns.COHORT;
import static io.github.tlaplus.hardening.database.DatabaseColumns.DETAIL;
import static io.github.tlaplus.hardening.database.DatabaseColumns.DIRECTORY;
import static io.github.tlaplus.hardening.database.DatabaseColumns.DURATION_MILLIS;
import static io.github.tlaplus.hardening.database.DatabaseColumns.END_TIME;
import static io.github.tlaplus.hardening.database.DatabaseColumns.ENTRY_ID;
import static io.github.tlaplus.hardening.database.DatabaseColumns.ERROR;
import static io.github.tlaplus.hardening.database.DatabaseColumns.EVALUATED_NODES;
import static io.github.tlaplus.hardening.database.DatabaseColumns.HASH;
import static io.github.tlaplus.hardening.database.DatabaseColumns.ID;
import static io.github.tlaplus.hardening.database.DatabaseColumns.INPUT_BYTES;
import static io.github.tlaplus.hardening.database.DatabaseColumns.KIND;
import static io.github.tlaplus.hardening.database.DatabaseColumns.METRICS;
import static io.github.tlaplus.hardening.database.DatabaseColumns.NAME;
import static io.github.tlaplus.hardening.database.DatabaseColumns.OCCURRENCES;
import static io.github.tlaplus.hardening.database.DatabaseColumns.PHASE;
import static io.github.tlaplus.hardening.database.DatabaseColumns.POSITION;
import static io.github.tlaplus.hardening.database.DatabaseColumns.REPLAY_ERROR;
import static io.github.tlaplus.hardening.database.DatabaseColumns.RICHNESS;
import static io.github.tlaplus.hardening.database.DatabaseColumns.SATURATED;
import static io.github.tlaplus.hardening.database.DatabaseColumns.SIGNATURE;
import static io.github.tlaplus.hardening.database.DatabaseColumns.STAGE_NAME;
import static io.github.tlaplus.hardening.database.DatabaseColumns.START_TIME;
import static io.github.tlaplus.hardening.database.DatabaseColumns.VERDICT;

import io.github.tlaplus.hardening.corpus.CorpusEnvelope;
import io.github.tlaplus.hardening.corpus.StageMetadata;
import io.github.tlaplus.hardening.corpus.StoredEntry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * The rows that one decoded corpus entry contributes: its {@code entry} row, one {@code
 * knownDefect} row per signature, one {@code stage} row per stage record and one {@code expr} row
 * per expression construct of its replayed input.
 */
record EntryRows(Row entry, List<Row> knownDefects, List<Row> stages, List<Row> exprs) {
    EntryRows {
        knownDefects = List.copyOf(knownDefects);
        stages = List.copyOf(stages);
        exprs = List.copyOf(exprs);
    }

    /** Maps an entry file, which the database identifies by {@code id}, to its rows. */
    static EntryRows of(
            long id, StoredEntry stored, CorpusEnvelope envelope, ReplayOutcome replay) {
        var input = envelope.corpusInput();
        var entry = new Row(DatabaseTable.ENTRY)
                .set(ID, id)
                .set(DIRECTORY, directory(stored))
                .set(HASH, stored.digest())
                .set(KIND, input.kind().encodedName())
                .set(INPUT_BYTES, input.input().length);
        var knownDefects = new ArrayList<Row>();
        envelope.generation().ifPresent(generation -> {
            entry.set(COHORT, generation.cohort()).set(RICHNESS, generation.richness());
            var signatures = generation.knownDefects();
            for (var position = 0; position < signatures.size(); position++) {
                knownDefects.add(new Row(DatabaseTable.KNOWN_DEFECT)
                        .set(ENTRY_ID, id)
                        .set(POSITION, position)
                        .set(SIGNATURE, signatures.get(position)));
            }
        });
        var stages = new ArrayList<Row>();
        for (var stage : envelope.stages()) {
            stages.add(stageRow(id, stage));
        }
        var exprs = new ArrayList<Row>();
        switch (replay) {
            case ReplayOutcome.Replayed(var features) -> {
                entry.set(EVALUATED_NODES, features.evaluatedNodes());
                features.exprs().forEach((name, occurrences) -> exprs.add(
                        new Row(DatabaseTable.EXPR)
                                .set(ENTRY_ID, id)
                                .set(NAME, name)
                                .set(OCCURRENCES, occurrences)));
            }
            case ReplayOutcome.Failed(var error) -> entry.set(REPLAY_ERROR, error);
        }
        return new EntryRows(entry, knownDefects, stages, exprs);
    }

    /** Returns the row of an entry file that does not decode. */
    static Row unreadable(StoredEntry stored, String error) {
        return new Row(DatabaseTable.UNREADABLE)
                .set(DIRECTORY, directory(stored))
                .set(HASH, stored.digest())
                .set(ERROR, error);
    }

    /** Every row, parents first. */
    List<Row> all() {
        var rows = new ArrayList<Row>(
                1 + knownDefects.size() + stages.size() + exprs.size());
        rows.add(entry);
        rows.addAll(knownDefects);
        rows.addAll(stages);
        rows.addAll(exprs);
        return rows;
    }

    private static Row stageRow(long id, StageMetadata stage) {
        var row = new Row(DatabaseTable.STAGE)
                .set(ENTRY_ID, id)
                .set(STAGE_NAME, stage.stage())
                .set(VERDICT, stage.verdict().encodedName())
                .set(START_TIME, stage.startTime())
                .set(END_TIME, stage.endTime())
                .set(DURATION_MILLIS, Duration.between(stage.startTime(), stage.endTime()).toMillis());
        stage.failure().ifPresent(failure -> {
            row.set(CODE, failure.code().encodedCode());
            failure.detail().ifPresent(detail -> row.set(DETAIL, detail));
        });
        stage.metrics().ifPresent(metrics -> {
            metrics.phase().ifPresent(phase -> row.set(PHASE, phase.encodedName()));
            row.set(SATURATED, metrics.saturated() ? 1 : 0);
            metrics.counts().forEach((count, value) -> row.set(METRICS.get(count), value));
        });
        return row;
    }

    private static String directory(StoredEntry stored) {
        return stored.location().relativePath().toString();
    }
}
