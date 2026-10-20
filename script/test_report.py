from __future__ import annotations

import contextlib
import io
import sqlite3
import sys
import tempfile
import unittest
from datetime import timedelta
from pathlib import Path

import cbor2

sys.path.insert(0, str(Path(__file__).resolve().parent))
import report
from campaign_report import fmt, plots, queries, sources
from campaign_report.errors import ReportError
from campaign_report.stages import Outcome, Stage
from campaign_report.triage import LabelClass

SCHEMA = Path(__file__).resolve().parent / "testdata" / "corpus-schema-v9.sql"
NO_DATA = "*No data for this plot in this corpus.*"

H_SELECTED, H_AGREEING, H_STATES, H_CRASH, H_KNOWN = (c * 64 for c in "12345")

CONFIG = """
[generator]
kind = "module"
max_nodes = 64

[workflow]
max_entries = 100
checkers = {checkers}

[workflow.inputs]
known_defects = ["signatures.toml"]

[workflow.tlc]
timeout_sec = 30

[workflow.tlcn]
timeout_sec = 30
workers = 4

[workflow.apalache]
workers = 16
"""

SIGNATURES = """
[[signature]]
id = "modulo-by-literal-zero"
references = []
description = "Mod by zero."
match = ['(MOD _ 0)']
"""

STATS = {
    "elapsedNs": {
        "total": 7_200 * 10**9,
        "generator": 3_600 * 10**9,
        "stages": {"parser": 60 * 10**9, "tlc": 3_000 * 10**9, "tlcn": 3_100 * 10**9},
    },
    "generator": {
        "attempts": 10,
        "rejected": 0,
        "richnessRejected": 2,
        "duplicates": 1,
        "clones": 1,
        "knownDefects": {"modulo-by-literal-zero": 3},
        "richnessSamples": 5,
        "minimumRichness": 0.0,
        "maximumRichness": 9.0,
        "averageRichness": 3.0,
    },
}


def time(hour: int, minute: int, second: int = 0) -> str:
    return f"2026-10-19T{hour:02d}:{minute:02d}:{second:02d}.000Z"


class Fixture:
    """A small corpus and its exported database: two sessions an hour apart."""

    def __init__(self, root: Path, checkers: tuple[str, ...] = ("tlc", "tlcn"), stats: bool = True):
        self.corpus = root / "corpus"
        self.corpus.mkdir()
        listed = ", ".join(f'"{c}"' for c in checkers)
        (self.corpus / "config.toml").write_text(CONFIG.format(checkers=f"[{listed}]"))
        (self.corpus / "signatures.toml").write_text(SIGNATURES)
        if stats:
            (self.corpus / ".workflow-stats.cbor").write_bytes(cbor2.dumps(STATS))
        self.checkers = checkers
        self.database = root / "corpus.sqlite"
        self.connection = sqlite3.connect(self.database)
        self.connection.executescript(SCHEMA.read_text())
        self.connection.executemany(
            "INSERT INTO export VALUES (?, ?)",
            [("corpus", str(self.corpus)), ("exportedAt", time(12, 0)),
             ("fuzztlaVersion", "fuzztla test"), ("technique", "pbt")],
        )
        self.next_id = 1
        self._populate()
        self.connection.commit()
        self.connection.close()

    def entry(self, directory: str, entry_hash: str, generation: int, parent: str | None = None) -> int:
        entry_id = self.next_id
        self.next_id += 1
        self.connection.execute(
            "INSERT INTO entry (id, directory, hash, kind, inputBytes, cohort, richness, generation, "
            "parent, evaluatedNodes) VALUES (?, ?, ?, 'module', ?, ?, ?, ?, ?, ?)",
            (entry_id, directory, entry_hash, 40 + entry_id, entry_id % 3, 2.0 * entry_id,
             generation, parent, 100 + entry_id),
        )
        return entry_id

    def stage(self, entry_id: int, stage: str, verdict: str, start: str, end: str, **metrics) -> None:
        columns = ["entryId", "stage", "verdict", "startTime", "endTime", "durationMillis", *metrics]
        values = [entry_id, stage, verdict, start, end, 1000, *metrics.values()]
        self.connection.execute(
            f"INSERT INTO stage ({', '.join(columns)}) VALUES ({', '.join('?' for _ in values)})", values
        )

    def checked(self, entry_id: int, hour: int, verdicts: dict[str, tuple[str, dict]]) -> None:
        self.stage(entry_id, "parser", "pass", time(hour, 0), time(hour, 1))
        for checker in self.checkers:
            verdict, metrics = verdicts[checker]
            self.stage(entry_id, checker, verdict, time(hour, 1), time(hour, 2), **metrics)

    def expr(self, entry_id: int, *names: str) -> None:
        self.connection.executemany(
            "INSERT INTO expr VALUES (?, ?, 1)", [(entry_id, name) for name in names]
        )
        self.connection.executemany(
            "INSERT INTO exprEdge VALUES (?, ?, ?, 1)",
            [(entry_id, parent, child) for parent, child in zip(names, names[1:])],
        )

    def _populate(self) -> None:
        explored = dict(phase="complete", initStates=1, distinctStates=12, generatedStates=30,
                        projectedStates=6, depth=4, projectedDepth=3, actions=3, actionsFired=2,
                        actionsDiscovering=2, maxStateNodes=9, maxCardinality=3, maxNesting=1)
        shallow = dict(explored, distinctStates=0, initStates=0, actionsDiscovering=0)
        passing = {checker: ("pass", explored) for checker in self.checkers}

        selected = self.entry("04quality-pass", H_SELECTED, 0)
        self.checked(selected, 9, passing)
        self.stage(selected, "aggregator", "pass", time(9, 2), time(9, 2))
        self.stage(selected, "quality", "pass", time(9, 2), time(9, 3))
        self.expr(selected, "AND", "EQ", "PLUS")

        agreeing = self.entry("04quality-fail", H_AGREEING, 1, parent=H_SELECTED)
        self.connection.execute("INSERT INTO mutationOperator VALUES (?, 0, 'bitflip')", (agreeing,))
        self.checked(agreeing, 9, {checker: ("pass", shallow) for checker in self.checkers})
        self.stage(agreeing, "aggregator", "pass", time(9, 3), time(9, 3))
        self.stage(agreeing, "quality", "fail", time(9, 3), time(9, 4))
        self.expr(agreeing, "AND", "EQ")

        if len(self.checkers) > 1:
            self._state_count_disagreement(explored)

        # One input crashed in TLC; its TLC-n copy waits in 02tlcn-pass with the same parser record.
        crash = self.entry("02tlc-crash", H_CRASH, 1)
        copy = self.entry("02tlcn-pass", H_CRASH, 1)
        for entry_id in (crash, copy):
            self.stage(entry_id, "parser", "pass", time(11, 5), time(11, 6))
        self.stage(crash, "tlc", "crashed", time(11, 6), time(11, 7))
        self.expr(crash, "AND", "MOD")
        self._write_envelope("02tlc-crash", H_CRASH, {"tlc": {"verdict": "crashed"}})
        (self.corpus / "02tlc-crash" / f"{H_CRASH}.stacktrace").write_text("unrecognized failure\n")

        known = self.entry("00-known-defects", H_KNOWN, 1)
        self.connection.execute(
            "INSERT INTO knownDefect VALUES (?, 0, 'modulo-by-literal-zero')", (known,)
        )

    def _state_count_disagreement(self, explored: dict) -> None:
        """Completed TLC and TLC-n searches with different state counts (ADR 0019)."""
        states = self.entry("03aggregator-fail", H_STATES, 1)
        self.checked(states, 11, {"tlc": ("pass", explored),
                                  "tlcn": ("pass", dict(explored, distinctStates=13))})
        self.stage(states, "aggregator", "fail", time(11, 2), time(11, 2))
        self.expr(states, "AND", "MOD")
        self._write_envelope("03aggregator-fail", H_STATES, {
            "aggregator": {"verdict": "fail"},
            "tlc": {"verdict": "pass", "metrics": {"phase": "complete", "distinctStates": 12}},
            "tlcn": {"verdict": "pass", "metrics": {"phase": "complete", "distinctStates": 13}},
        })

    def _write_envelope(self, directory: str, entry_hash: str, stages: dict) -> None:
        (self.corpus / directory).mkdir(exist_ok=True)
        envelope = {"kind": "module", "input": b"", "gen": {"generation": 1}, "stages": stages}
        (self.corpus / directory / f"{entry_hash}.cbor").write_bytes(cbor2.dumps(envelope))


def run_report(fixture: Fixture) -> tuple[int, str]:
    with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()) as err:
        status = report.main([str(fixture.corpus), "--db", str(fixture.database)])
    return status, err.getvalue()


class SessionTest(unittest.TestCase):
    def test_a_pause_longer_than_the_gap_starts_a_new_session(self) -> None:
        sessions = queries.split_sessions([
            ("2026-10-19T09:00", 3, time(9, 1)),
            ("2026-10-19T09:10", 2, time(9, 12)),
            ("2026-10-19T10:00", 4, time(10, 5)),
        ])

        self.assertEqual([s.stage_records for s in sessions], [5, 4])
        self.assertEqual(sessions[0].duration, timedelta(minutes=12))

    def test_a_pause_of_exactly_the_gap_continues_the_session(self) -> None:
        sessions = queries.split_sessions([
            ("2026-10-19T09:00", 1, time(9, 0, 30)),
            ("2026-10-19T09:15", 1, time(9, 16)),
        ], gap=timedelta(minutes=15, seconds=-30))

        self.assertEqual(len(sessions), 1)


class LinkTest(unittest.TestCase):
    def test_links_relative_from_a_corpus_beside_the_document(self) -> None:
        link = fmt.link("tlc-002.md", Path("/repo/findings/tlc-002.md"), Path("/repo/corpus66"))

        self.assertEqual(link, "[tlc-002.md](../findings/tlc-002.md)")

    def test_links_absolute_when_only_the_root_is_shared(self) -> None:
        link = fmt.link("tlc-002.md", Path("/repo/findings/tlc-002.md"), Path("/tmp/corpus"))

        self.assertEqual(link, "[tlc-002.md](/repo/findings/tlc-002.md)")


class LiftTest(unittest.TestCase):
    def test_lift_compares_the_share_in_a_group_with_the_share_overall(self) -> None:
        result = queries.lifts({"MOD": 10, "AND": 100}, 100, {"MOD": 5, "AND": 10}, 10)

        self.assertEqual([(lift.name, lift.lift) for lift in result], [("MOD", 5.0), ("AND", 1.0)])

    def test_constructs_below_the_minimum_support_are_left_out(self) -> None:
        support = queries.MIN_LIFT_SUPPORT - 1

        self.assertEqual(queries.lifts({"MOD": 10}, 100, {"MOD": support}, 10), [])


class EnumTest(unittest.TestCase):
    def test_label_classes_of_triager_labels(self) -> None:
        self.assertIs(LabelClass.of("NEW"), LabelClass.NEW)
        self.assertIs(LabelClass.of("STATE_COUNT"), LabelClass.STATE_COUNT)
        self.assertIs(LabelClass.of("tlc-002.md"), LabelClass.DOCUMENTED)

    def test_stage_order_is_the_pipeline_order(self) -> None:
        self.assertEqual(
            [stage.value for stage in Stage],
            ["parser", "tlc", "tlcn", "apalache", "aggregator", "quality"],
        )

    def test_outcomes_partition_their_directories(self) -> None:
        directories = [d for outcome in Outcome for d in outcome.directories]

        self.assertEqual(len(directories), len(set(directories)))

    def test_the_figure_registry_is_pinned(self) -> None:
        # Figure names are the published image paths; renaming one breaks links to old reports.
        self.assertEqual(
            [figure.name for figure in plots.FIGURES],
            ["throughput", "generation-funnel", "checker-times", "discovery", "verdict-matrix",
             "triage-labels", "known-defects", "failure-codes", "input-size", "state-space",
             "shallow-and-values", "operator-coverage"],
        )


class DatabaseTest(unittest.TestCase):
    def test_refuses_another_schema_version(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "old.sqlite"
            connection = sqlite3.connect(path)
            connection.execute("PRAGMA user_version = 8")
            connection.close()

            with self.assertRaisesRegex(ReportError, "schema version 8"):
                sources.open_database(path)

    def test_queries_count_an_input_in_two_directories_once(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            fixture = Fixture(Path(directory))
            connection = sources.open_database(fixture.database)
            connection.execute("CREATE TEMP TABLE triage (hash TEXT, directory TEXT, label TEXT)")
            corpus_queries = queries.CorpusQueries(connection)

            # Five inputs, one quarantined; the crashed input has a copy in 02tlcn-pass.
            self.assertEqual(corpus_queries.stage_summary(Stage.PARSER).records, 4)
            self.assertEqual(corpus_queries.distinct_inputs(), 5)
            self.assertEqual(
                dict(corpus_queries.shallow_patterns()), {"none": 1, "vacuous pass": 1}
            )
            self.assertEqual(len(corpus_queries.sessions()), 2)
            # Without a `.checkers` file, the checkers come from the stage records.
            self.assertEqual(corpus_queries.checkers, (Stage.TLC, Stage.TLCN))
            connection.close()


class ReportTest(unittest.TestCase):
    def test_writes_every_section_and_every_figure_or_a_note(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            fixture = Fixture(Path(directory))

            status, errors = run_report(fixture)

            self.assertEqual(status, 0, errors)
            text = (fixture.corpus / "report.md").read_text()
            for heading in ("## Summary", "## 1. Campaign", "## 2. Findings", "## 3. Input quality",
                            "### 1.3. Stages", "### 2.2. Crashes and disagreements"):
                self.assertIn(heading, text)
            missing = 0
            for figure in plots.FIGURES:
                self.assertIn(f". {figure.title}\n", text)
                if (fixture.corpus / ".report" / figure.file_name).exists():
                    self.assertIn(f"(.report/{figure.file_name})", text)
                else:
                    missing += 1
            self.assertEqual(text.count(NO_DATA), missing)
            self.assertTrue((fixture.corpus / ".report" / "config.toml").is_file())
            self.assertIn("`STATE_COUNT`", text)
            self.assertIn(f"`{H_STATES[:8]}`", text)
            self.assertIn("1 `NEW`, 1 `STATE_COUNT`", text)
            # No triage CSV is written to the corpus.
            self.assertEqual(list(fixture.corpus.glob("*-triage.csv")), [])

    def test_a_corpus_without_statistics_and_with_one_checker_skips_their_figures(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            fixture = Fixture(Path(directory), checkers=("tlc",), stats=False)

            status, errors = run_report(fixture)

            self.assertEqual(status, 0, errors)
            text = (fixture.corpus / "report.md").read_text()
            self.assertIn("has no `.workflow-stats.cbor`", text)
            self.assertFalse((fixture.corpus / ".report" / "known-defects.png").exists())
            self.assertFalse((fixture.corpus / ".report" / "verdict-matrix.png").exists())
            self.assertIn(NO_DATA, text)
            self.assertNotIn("workflow.tlcn.workers", text)
            self.assertNotIn("workflow.apalache.workers", text)

    def test_reports_a_missing_corpus(self) -> None:
        with tempfile.TemporaryDirectory() as directory, \
                contextlib.redirect_stderr(io.StringIO()) as err:
            status = report.main([str(Path(directory) / "missing"), "--db", "x.sqlite"])

        self.assertEqual(status, 1)
        self.assertIn("not a corpus directory", err.getvalue())


if __name__ == "__main__":
    unittest.main()
