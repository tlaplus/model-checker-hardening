"""SQL over the exported database; every query of the report lives here.

`CorpusQueries` first builds TEMP tables on the connection, so the exported file
stays unchanged:

- `stageOnce`: one stage record per (input, stage). An input can sit in several
  directories at once (corpus-database manual §2.2), each copy with the same
  earlier stage records; counting rows of `stage` would count them twice.
- `entryOutcome`: one row per checked input with its `Outcome` and a
  representative `entryId` for joins with `expr` and `exprEdge`.
"""

from __future__ import annotations

import sqlite3
from dataclasses import dataclass
from datetime import datetime, timedelta

import numpy as np

from .stages import EXPLORERS, Outcome, Stage
from .triage import TRIAGE_TABLE

KNOWN_DEFECTS_DIRECTORY = "00-known-defects"
AGGREGATOR_FAIL_DIRECTORY = "03aggregator-fail"
# A pause longer than this between stage records starts a new session.
SESSION_GAP = timedelta(minutes=15)
# Distinct inputs whose evaluated code contains an operator before its lift is shown.
MIN_LIFT_SUPPORT = 5

_STAGE_ONCE = """
CREATE TEMP TABLE stageOnce AS
SELECT e.hash AS hash, s.*
FROM stage s
JOIN entry e ON e.id = s.entryId
JOIN (SELECT min(s2.entryId) AS entryId, s2.stage AS stage
      FROM stage s2 JOIN entry e2 ON e2.id = s2.entryId
      GROUP BY e2.hash, s2.stage) k
  ON k.entryId = s.entryId AND k.stage = s.stage
"""

# SQLite takes a bare column from the row that holds min(rank).
_ENTRY_OUTCOME = """
CREATE TEMP TABLE entryOutcome AS
SELECT hash, id AS entryId, outcome, min(rank) AS rank
FROM (SELECT hash, id, {outcome} AS outcome, {rank} AS rank FROM entry)
WHERE outcome IS NOT NULL
GROUP BY hash
"""

# The shallow patterns of ADR 0008, first match wins (corpus-database manual §3).
SHALLOW_PATTERN = """
CASE
  WHEN s.phase IS NULL THEN 'no metrics'
  WHEN s.verdict = 'pass' AND s.initStates = 0 THEN 'vacuous pass'
  WHEN s.verdict = 'counterexample' AND s.phase = 'init' THEN 'initial-state violation'
  WHEN s.verdict = 'fail' AND s.phase = 'init' THEN 'early failure'
  WHEN s.actionsDiscovering = 0 THEN 'no discovering action'
  WHEN s.projectedStates <= 1 THEN 'counter-only progress'
  ELSE 'none'
END
"""

STATE_METRICS = (
    "distinctStates",
    "generatedStates",
    "projectedStates",
    "depth",
    "projectedDepth",
    "actionsDiscovering",
    "actions",
    "maxCardinality",
    "maxNesting",
    "maxStateNodes",
)


@dataclass(frozen=True)
class Session:
    start: datetime
    end: datetime
    stage_records: int

    @property
    def duration(self) -> timedelta:
        return self.end - self.start


@dataclass(frozen=True)
class StageSummary:
    stage: Stage
    verdicts: dict[str, int]
    durations_ms: np.ndarray

    @property
    def records(self) -> int:
        return sum(self.verdicts.values())

    def percentile_ms(self, q: float) -> float:
        return float(np.percentile(self.durations_ms, q)) if self.durations_ms.size else 0.0


@dataclass(frozen=True)
class TriagedEntry:
    """A triaged entry and its checker results, for the tables of entries to investigate."""

    hash: str
    directory: str
    label: str
    results: tuple[tuple[str, str, int | None, str | None], ...]


@dataclass(frozen=True, eq=False)
class ConstructPairs:
    """Pairwise construct coverage: inputs whose evaluated code contains both constructs.

    `names` are ordered by the number of inputs that contain them, most first, then
    by name; this order gives the construct indices of the report's table and heatmap.
    `together[a, b]` counts the inputs with both `names[a]` and `names[b]`; the
    diagonal counts the inputs with one construct.
    """

    inputs: int  # checked inputs
    names: tuple[str, ...]
    together: np.ndarray

    @property
    def constructs(self) -> int:
        return len(self.names)

    @property
    def possible(self) -> int:
        return self.constructs * (self.constructs - 1) // 2

    def index(self, name: str) -> int:
        """The construct's 1-based index in frequency order."""
        return self.names.index(name) + 1

    @property
    def pairs(self) -> list[tuple[str, str, int]]:
        """(first, second, inputs) of the pairs some input has, most frequent first."""
        first, second = np.triu_indices(self.constructs, k=1)
        pairs = [
            (self.names[a], self.names[b], int(self.together[a, b]))
            for a, b in zip(first, second)
            if self.together[a, b] > 0
        ]
        return sorted(pairs, key=lambda pair: (-pair[2], self.index(pair[0]), self.index(pair[1])))

    @property
    def missing(self) -> list[tuple[str, str]]:
        """Pairs of constructs seen that no input has together."""
        first, second = np.triu_indices(self.constructs, k=1)
        return [(self.names[a], self.names[b]) for a, b in zip(first, second)
                if self.together[a, b] == 0]


def pair_counts(names: list[str], memberships: np.ndarray, inputs: int) -> ConstructPairs:
    """Counts co-occurring pairs from (input index, construct index) rows.

    With M the inputs × constructs 0/1 matrix, (MᵀM)[a, b] is the number of inputs
    that contain both a and b, so one matrix product counts every pair.
    """
    matrix = np.zeros((inputs, len(names)), dtype=np.float32)
    if memberships.size:
        matrix[memberships[:, 0], memberships[:, 1]] = 1
    together = (matrix.T @ matrix).round().astype(np.int64)
    order = sorted(range(len(names)), key=lambda i: (-together[i, i], names[i]))
    return ConstructPairs(inputs, tuple(names[i] for i in order), together[np.ix_(order, order)])


@dataclass(frozen=True)
class Lift:
    name: str
    support: int
    lift: float


def parse_time(text: str) -> datetime:
    return datetime.fromisoformat(text)


def to_datetime64(texts: list[str]) -> np.ndarray:
    """ISO-8601 UTC text with a trailing Z, as the export writes it, to datetime64[ms]."""
    return np.array([text.rstrip("Z") for text in texts], dtype="datetime64[ms]")


def split_sessions(minutes: list[tuple[str, int, str]], gap: timedelta = SESSION_GAP) -> list[Session]:
    """Groups (start minute, records, latest end) rows, sorted by minute, into sessions."""
    sessions: list[Session] = []
    for minute, records, latest_end in minutes:
        start = parse_time(minute + ":00Z")
        end = parse_time(latest_end)
        if sessions and start - sessions[-1].end <= gap:
            last = sessions[-1]
            sessions[-1] = Session(last.start, max(last.end, end), last.stage_records + records)
        else:
            sessions.append(Session(start, end, records))
    return sessions


def lifts(
    counts: dict[str, int], total: int, group_counts: dict[str, int], group_total: int
) -> list[Lift]:
    """How much more often each name occurs in a group than in the whole population."""
    if total == 0 or group_total == 0:
        return []
    result = [
        Lift(name, support, (support / group_total) / (counts[name] / total))
        for name, support in group_counts.items()
        if support >= MIN_LIFT_SUPPORT and counts.get(name)
    ]
    return sorted(result, key=lambda lift: (-lift.lift, lift.name))


class CorpusQueries:
    def __init__(self, connection: sqlite3.Connection):
        self.connection = connection
        self._operator_counts: tuple[dict[str, dict[str, int]], dict[str, int]] | None = None
        self._construct_pairs: ConstructPairs | None = None
        connection.execute(_STAGE_ONCE)
        connection.execute("CREATE INDEX temp.stageOnceStage ON stageOnce(stage, verdict)")
        connection.execute("CREATE INDEX temp.stageOnceEntry ON stageOnce(entryId, stage)")
        connection.execute(
            _ENTRY_OUTCOME.format(
                outcome=Outcome.sql_case("directory"), rank=Outcome.sql_rank("directory")
            )
        )
        connection.execute("CREATE INDEX temp.entryOutcomeEntry ON entryOutcome(entryId)")
        # The checkers the entries record, whatever the configuration now says.
        self.checkers = tuple(
            stage for stage in self.stages() if stage.checks and stage is not Stage.PARSER
        )
        explorers = [stage for stage in EXPLORERS if stage in self.checkers]
        self.explorer: Stage | None = explorers[0] if explorers else None

    def _rows(self, sql: str, *parameters: object) -> list[tuple]:
        return self.connection.execute(sql, parameters).fetchall()

    def _scalar(self, sql: str, *parameters: object) -> object:
        return self.connection.execute(sql, parameters).fetchone()[0]

    # Campaign

    def stages(self) -> list[Stage]:
        names = {row[0] for row in self._rows("SELECT DISTINCT stage FROM stageOnce")}
        return [stage for stage in Stage if stage.value in names]

    def sessions(self) -> list[Session]:
        return split_sessions(
            self._rows(
                "SELECT substr(startTime, 1, 16), count(*), max(endTime) "
                "FROM stageOnce GROUP BY 1 ORDER BY 1"
            )
        )

    def stage_summary(self, stage: Stage) -> StageSummary:
        rows = self._rows(
            "SELECT verdict, durationMillis FROM stageOnce WHERE stage = ?", stage.value
        )
        verdicts: dict[str, int] = {}
        for verdict, _ in rows:
            verdicts[verdict] = verdicts.get(verdict, 0) + 1
        return StageSummary(stage, verdicts, np.array([row[1] for row in rows], dtype=float))

    def durations_by_verdict(self, stage: Stage) -> dict[str, np.ndarray]:
        result: dict[str, list[int]] = {}
        for verdict, millis in self._rows(
            "SELECT verdict, durationMillis FROM stageOnce WHERE stage = ?", stage.value
        ):
            result.setdefault(verdict, []).append(millis)
        return {verdict: np.array(values, dtype=float) for verdict, values in result.items()}

    def directory_counts(self) -> list[tuple[str, int]]:
        return self._rows("SELECT directory, count(*) FROM entry GROUP BY directory ORDER BY directory")

    def distinct_inputs(self) -> int:
        return int(self._scalar("SELECT count(DISTINCT hash) FROM entry"))

    def origins(self) -> dict[str, int]:
        """Admitted inputs by origin: PBT, mutant, or adopted from a base corpus."""
        return dict(
            self._rows(
                "SELECT CASE WHEN e.parent IS NULL THEN 'generated by PBT' "
                "WHEN EXISTS (SELECT 1 FROM mutationOperator m WHERE m.entryId = e.id) "
                "THEN 'mutants' ELSE 'adopted from a base corpus' END, count(DISTINCT e.hash) "
                "FROM entry e WHERE e.directory <> ? GROUP BY 1 ORDER BY 1",
                KNOWN_DEFECTS_DIRECTORY,
            )
        )

    def generations(self) -> tuple[int, int | None]:
        count, latest = self._rows(
            "SELECT count(DISTINCT generation), max(generation) FROM entry WHERE directory <> ?",
            KNOWN_DEFECTS_DIRECTORY,
        )[0]
        return int(count), latest

    def stage_end_times(self, stage: Stage) -> np.ndarray:
        rows = self._rows(
            "SELECT endTime FROM stageOnce WHERE stage = ? ORDER BY endTime", stage.value
        )
        return to_datetime64([row[0] for row in rows])

    def generation_funnel(self) -> list[tuple[int, int, int, int, int]]:
        """(generation, admitted, mutants, agreeing, selected) per generation."""
        return self._rows(
            "SELECT e.generation, count(DISTINCT e.hash), "
            "count(DISTINCT CASE WHEN e.parent IS NOT NULL THEN e.hash END), "
            "count(DISTINCT CASE WHEN o.outcome IN (?, ?) THEN e.hash END), "
            "count(DISTINCT CASE WHEN o.outcome = ? THEN e.hash END) "
            "FROM entry e LEFT JOIN entryOutcome o ON o.hash = e.hash "
            "WHERE e.directory <> ? AND e.generation IS NOT NULL "
            "GROUP BY e.generation ORDER BY e.generation",
            Outcome.SELECTED.value,
            Outcome.AGREEING.value,
            Outcome.SELECTED.value,
            KNOWN_DEFECTS_DIRECTORY,
        )

    def selected_states_by_generation(self) -> list[tuple[int, float]]:
        """Median distinct states of the selected inputs, per generation."""
        if self.explorer is None:
            return []
        values: dict[int, list[int]] = {}
        for generation, states in self._rows(
            "SELECT e.generation, s.distinctStates FROM entryOutcome o "
            "JOIN entry e ON e.id = o.entryId "
            "JOIN stageOnce s ON s.entryId = o.entryId AND s.stage = ? "
            "WHERE o.outcome = ? AND e.generation IS NOT NULL AND s.distinctStates IS NOT NULL",
            self.explorer.value,
            Outcome.SELECTED.value,
        ):
            values.setdefault(generation, []).append(states)
        return [(g, float(np.median(v))) for g, v in sorted(values.items())]

    # Findings

    def aggregated(self) -> dict[str, int]:
        return dict(self._rows("SELECT aggregator, count(*) FROM verdictPair GROUP BY 1"))

    def verdict_pairs(self) -> list[tuple]:
        """(aggregator, verdict per checker..., entries), most frequent first."""
        columns = ", ".join(stage.value for stage in self.checkers)
        if not columns:
            return []
        return self._rows(
            f"SELECT aggregator, {columns}, count(*) AS n FROM verdictPair "
            f"GROUP BY aggregator, {columns} ORDER BY n DESC"
        )

    def triage_labels(self) -> list[tuple[str, str, str]]:
        """(hash, directory, label) of every triaged entry."""
        return self._rows(f"SELECT hash, directory, label FROM {TRIAGE_TABLE} ORDER BY hash, directory")

    def triage_counts(self) -> list[tuple[str, str, int]]:
        return self._rows(
            f"SELECT directory, label, count(*) FROM {TRIAGE_TABLE} "
            "GROUP BY directory, label ORDER BY directory, count(*) DESC, label"
        )

    def triaged_entries(self, labels: tuple[str, ...], limit: int) -> list[TriagedEntry]:
        """Entries with one of the labels, at most `limit` per directory, with checker results."""
        placeholders = ", ".join("?" for _ in labels)
        checker_names = [stage.value for stage in self.checkers]
        rows = self._rows(
            f"SELECT t.hash, t.directory, t.label, e.id FROM {TRIAGE_TABLE} t "
            "JOIN entry e ON e.hash = t.hash AND e.directory = t.directory "
            f"WHERE t.label IN ({placeholders}) ORDER BY t.directory, t.label, t.hash",
            *labels,
        )
        shown: dict[str, int] = {}
        entries = []
        for entry_hash, directory, label, entry_id in rows:
            if shown.get(directory, 0) >= limit:
                continue
            shown[directory] = shown.get(directory, 0) + 1
            results = tuple(
                row
                for row in self._rows(
                    "SELECT stage, verdict, code, detail FROM stage WHERE entryId = ?", entry_id
                )
                if row[0] in checker_names
            )
            entries.append(TriagedEntry(entry_hash, directory, label, results))
        return entries

    def triage_events(self) -> list[tuple[str, str, str, str]]:
        """(hash, directory, label, time) of each triaged entry, when its deciding stage ended."""
        events = []
        deciding = {stage.crash_kind.value: stage for stage in Stage if stage.crash_kind}
        deciding[AGGREGATOR_FAIL_DIRECTORY] = Stage.AGGREGATOR
        for directory, stage in deciding.items():
            events += self._rows(
                f"SELECT t.hash, t.directory, t.label, s.endTime FROM {TRIAGE_TABLE} t "
                "JOIN entry e ON e.hash = t.hash AND e.directory = t.directory "
                "JOIN stage s ON s.entryId = e.id AND s.stage = ? WHERE t.directory = ?",
                stage.value,
                directory,
            )
        return events

    def known_defect_samples(self) -> dict[str, int]:
        return dict(
            self._rows(
                "SELECT k.signature, count(*) FROM knownDefect k JOIN entry e ON e.id = k.entryId "
                "WHERE e.directory = ? AND k.position = 0 GROUP BY k.signature",
                KNOWN_DEFECTS_DIRECTORY,
            )
        )

    def unreadable(self) -> list[tuple[str, int]]:
        return self._rows("SELECT directory, count(*) FROM unreadable GROUP BY 1 ORDER BY 1")

    def replay_errors(self) -> list[tuple[str, int]]:
        return self._rows(
            "SELECT replayError, count(*) FROM entry WHERE replayError IS NOT NULL "
            "GROUP BY 1 ORDER BY 2 DESC LIMIT 10"
        )

    def failure_codes(self) -> list[tuple[str, str, int | None, int]]:
        """(stage, verdict, code, records) of the stages that run a tool."""
        names = [stage.value for stage in Stage if stage.checks]
        placeholders = ", ".join("?" for _ in names)
        return self._rows(
            "SELECT stage, verdict, code, count(*) FROM stageOnce "
            f"WHERE stage IN ({placeholders}) GROUP BY stage, verdict, code",
            *names,
        )

    # Input quality

    def input_sizes(self) -> list[tuple[str, int, int | None, int | None]]:
        """(outcome, inputBytes, evaluatedNodes, distinctStates) per checked input."""
        return self._rows(
            "SELECT o.outcome, e.inputBytes, e.evaluatedNodes, s.distinctStates "
            "FROM entryOutcome o JOIN entry e ON e.id = o.entryId "
            "LEFT JOIN stageOnce s ON s.entryId = o.entryId AND s.stage = ?",
            self.explorer.value if self.explorer else None,
        )

    def state_metrics(self) -> list[tuple]:
        """(outcome, STATE_METRICS...) of the agreeing inputs, from the explorer stage."""
        if self.explorer is None:
            return []
        columns = ", ".join(f"s.{metric}" for metric in STATE_METRICS)
        return self._rows(
            f"SELECT o.outcome, {columns} FROM entryOutcome o "
            "JOIN stageOnce s ON s.entryId = o.entryId AND s.stage = ? "
            "WHERE o.outcome IN (?, ?)",
            self.explorer.value,
            Outcome.SELECTED.value,
            Outcome.AGREEING.value,
        )

    def shallow_patterns(self) -> list[tuple[str, int]]:
        if self.explorer is None:
            return []
        return self._rows(
            f"SELECT {SHALLOW_PATTERN} AS pattern, count(*) FROM entryOutcome o "
            "JOIN stageOnce s ON s.entryId = o.entryId AND s.stage = ? "
            "WHERE o.outcome IN (?, ?) GROUP BY pattern ORDER BY count(*) DESC",
            self.explorer.value,
            Outcome.SELECTED.value,
            Outcome.AGREEING.value,
        )

    def cohorts(self) -> list[tuple[int, int]]:
        return self._rows(
            "SELECT cohort, count(DISTINCT hash) FROM entry "
            "WHERE directory <> ? AND cohort IS NOT NULL GROUP BY cohort ORDER BY cohort",
            KNOWN_DEFECTS_DIRECTORY,
        )

    def richness(self) -> np.ndarray:
        rows = self._rows(
            "SELECT richness FROM entry e JOIN entryOutcome o ON o.entryId = e.id "
            "WHERE richness IS NOT NULL"
        )
        return np.array([row[0] for row in rows], dtype=float)

    def operator_counts(self) -> tuple[dict[str, dict[str, int]], dict[str, int]]:
        """Inputs whose evaluated code contains each construct, by outcome; and inputs by outcome.

        The scan of `expr` is the slowest query, so its result is kept.
        """
        if self._operator_counts is None:
            self._operator_counts = self._scan_operators()
        return self._operator_counts

    def _scan_operators(self) -> tuple[dict[str, dict[str, int]], dict[str, int]]:
        by_outcome: dict[str, dict[str, int]] = {}
        for outcome, name, inputs in self._rows(
            "SELECT o.outcome, x.name, count(*) FROM expr x "
            "JOIN entryOutcome o ON o.entryId = x.entryId GROUP BY o.outcome, x.name"
        ):
            by_outcome.setdefault(outcome, {})[name] = inputs
        totals = dict(self._rows("SELECT outcome, count(*) FROM entryOutcome GROUP BY outcome"))
        return by_outcome, totals

    def construct_pairs(self) -> ConstructPairs:
        """Co-occurring construct pairs over the checked inputs, computed once."""
        if self._construct_pairs is None:
            self._construct_pairs = self._count_construct_pairs()
        return self._construct_pairs

    def _count_construct_pairs(self) -> ConstructPairs:
        self.connection.execute(
            "CREATE TEMP TABLE IF NOT EXISTS constructName AS SELECT DISTINCT x.name AS name "
            "FROM expr x JOIN entryOutcome o ON o.entryId = x.entryId ORDER BY x.name"
        )
        names = [row[0] for row in self._rows("SELECT name FROM constructName ORDER BY rowid")]
        # Both rowids count from 1, so they become matrix indices after subtracting 1.
        rows = self._rows(
            "SELECT o.rowid - 1, n.rowid - 1 FROM expr x "
            "JOIN entryOutcome o ON o.entryId = x.entryId "
            "JOIN constructName n ON n.name = x.name"
        )
        inputs = int(self._scalar("SELECT count(*) FROM entryOutcome"))
        return pair_counts(names, np.array(rows, dtype=np.int64).reshape(-1, 2), inputs)

    def new_edges_by_generation(self, outcome: Outcome | None) -> list[tuple[int, int]]:
        """(generation, operator edges first seen in it), over checked inputs or one outcome."""
        condition = "WHERE o.outcome = ?" if outcome else ""
        parameters = (outcome.value,) if outcome else ()
        return self._rows(
            "SELECT g, count(*) FROM (SELECT min(e.generation) AS g FROM exprEdge x "
            "JOIN entryOutcome o ON o.entryId = x.entryId JOIN entry e ON e.id = x.entryId "
            f"{condition} GROUP BY x.parentName, x.childName) "
            "WHERE g IS NOT NULL GROUP BY g ORDER BY g",
            *parameters,
        )

