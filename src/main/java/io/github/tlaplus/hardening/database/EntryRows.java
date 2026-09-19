package io.github.tlaplus.hardening.database;

import static io.github.tlaplus.hardening.database.DatabaseColumns.CHILD_NAME;
import static io.github.tlaplus.hardening.database.DatabaseColumns.CODE;
import static io.github.tlaplus.hardening.database.DatabaseColumns.COHORT;
import static io.github.tlaplus.hardening.database.DatabaseColumns.DETAIL;
import static io.github.tlaplus.hardening.database.DatabaseColumns.DIRECTORY;
import static io.github.tlaplus.hardening.database.DatabaseColumns.DURATION_MILLIS;
import static io.github.tlaplus.hardening.database.DatabaseColumns.END_TIME;
import static io.github.tlaplus.hardening.database.DatabaseColumns.ENTRY_ID;
import static io.github.tlaplus.hardening.database.DatabaseColumns.ERROR;
import static io.github.tlaplus.hardening.database.DatabaseColumns.EVALUATED_NODES;
import static io.github.tlaplus.hardening.database.DatabaseColumns.GENERATION;
import static io.github.tlaplus.hardening.database.DatabaseColumns.HASH;
import static io.github.tlaplus.hardening.database.DatabaseColumns.ID;
import static io.github.tlaplus.hardening.database.DatabaseColumns.INPUT_BYTES;
import static io.github.tlaplus.hardening.database.DatabaseColumns.KIND;
import static io.github.tlaplus.hardening.database.DatabaseColumns.METRICS;
import static io.github.tlaplus.hardening.database.DatabaseColumns.NAME;
import static io.github.tlaplus.hardening.database.DatabaseColumns.OCCURRENCES;
import static io.github.tlaplus.hardening.database.DatabaseColumns.OPERATOR;
import static io.github.tlaplus.hardening.database.DatabaseColumns.PARENT;
import static io.github.tlaplus.hardening.database.DatabaseColumns.PARENT_NAME;
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
import io.github.tlaplus.hardening.mutation.MutationOperator;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * The rows that one decoded corpus entry contributes: its {@code entry} row, followed by the rows
 * that reference it: one {@code knownDefect} row per signature, one {@code mutationOperator} row
 * per operator, one {@code stage} row per stage record, one {@code expr} row per expression
 * construct of its replayed input and one {@code exprEdge} row per edge between constructs.
 */
record EntryRows(Row entry, List<Row> dependents) {
    EntryRows {
        dependents = List.copyOf(dependents);
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
        var dependents = new ArrayList<Row>();
        envelope.generation().ifPresent(generation -> {
            entry.set(COHORT, generation.cohort()).set(RICHNESS, generation.richness());
            generation.generation().ifPresent(value -> entry.set(GENERATION, value));
            dependents.addAll(positions(DatabaseTable.KNOWN_DEFECT, SIGNATURE, id, generation.knownDefects()));
            generation.mutation().ifPresent(mutation -> {
                entry.set(PARENT, mutation.parent());
                dependents.addAll(positions(
                        DatabaseTable.MUTATION_OPERATOR,
                        OPERATOR,
                        id,
                        mutation.operators().stream().map(MutationOperator::encodedName).toList()));
            });
        });
        for (var stage : envelope.stages()) {
            dependents.add(stageRow(id, stage));
        }
        switch (replay) {
            case ReplayOutcome.Replayed(var counts) -> {
                entry.set(EVALUATED_NODES, counts.nodes());
                counts.exprs().forEach((name, occurrences) -> dependents.add(
                        new Row(DatabaseTable.EXPR)
                                .set(ENTRY_ID, id)
                                .set(NAME, name)
                                .set(OCCURRENCES, occurrences)));
                counts.edges().forEach((edge, occurrences) -> dependents.add(
                        new Row(DatabaseTable.EXPR_EDGE)
                                .set(ENTRY_ID, id)
                                .set(PARENT_NAME, edge.parent())
                                .set(CHILD_NAME, edge.child())
                                .set(OCCURRENCES, occurrences)));
            }
            case ReplayOutcome.Failed(var error) -> entry.set(REPLAY_ERROR, error);
        }
        return new EntryRows(entry, dependents);
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
        var rows = new ArrayList<Row>(1 + dependents.size());
        rows.add(entry);
        rows.addAll(dependents);
        return rows;
    }

    /** Returns one row per value of an ordered list, keyed by the value's position. */
    private static List<Row> positions(DatabaseTable table, Column value, long id, List<String> values) {
        var rows = new ArrayList<Row>(values.size());
        for (var position = 0; position < values.size(); position++) {
            rows.add(new Row(table).set(ENTRY_ID, id).set(POSITION, position).set(value, values.get(position)));
        }
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
