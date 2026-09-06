from __future__ import annotations

import csv
import sys
import tempfile
import unittest
from pathlib import Path

import cbor2

sys.path.insert(0, str(Path(__file__).resolve().parent))
import triager


HASH_A = "a" * 64
HASH_B = "b" * 64


def results(
    failed: triager.Checker,
    detail: str | None,
    *,
    other_verdict: str = "pass",
    code: int = 75,
) -> dict[triager.Checker, triager.CheckerResult]:
    return {
        failed: triager.CheckerResult("fail", code, detail),
        failed.other: triager.CheckerResult(other_verdict, None, None),
    }


def envelope(
    failed: triager.Checker,
    detail: str | None,
    *,
    other_verdict: str = "pass",
    code: int = 75,
) -> dict[str, object]:
    stages: dict[str, object] = {
        "aggregator": {"verdict": "fail"},
        failed.value: {"verdict": "fail", "code": code},
        failed.other.value: {"verdict": other_verdict},
    }
    if detail is not None:
        stages[failed.value]["detail"] = detail  # type: ignore[index]
    return {"kind": "expr", "input": b"fixture", "stages": stages}


class AggregatorClassificationTest(unittest.TestCase):
    def test_classifies_tlc_failure(self) -> None:
        actual = triager.classify_aggregator(
            results(triager.Checker.TLC, "Attempted to apply Head to the empty sequence."),
            HASH_A,
        )
        self.assertEqual("head-of-empty-sequence.md", actual)

    def test_classifies_apalache_failure(self) -> None:
        actual = triager.classify_aggregator(
            results(
                triager.Checker.APALACHE,
                "Input error (see the manual): Division by zero at complicated expression…",
            ),
            HASH_A,
        )
        self.assertEqual("division-by-zero-apalache-fails.md", actual)

    def test_classifies_new_conformance_and_printer_groups(self) -> None:
        cases = (
            (
                "Attempted to compute cardinality of the value",
                "cardinality-of-infinite-set.md",
            ),
            (
                "Attempted to evaluate an expression of form S \\subseteq T, but S was not enumer…",
                "subset-test-over-infinite-set.md",
            ),
            (
                "Attempted to evaluate an expression of form P => Q when P was",
                "apalache-printer-008.md",
            ),
        )
        for detail, issue in cases:
            with self.subTest(issue=issue):
                self.assertEqual(
                    issue,
                    triager.classify_aggregator(
                        results(triager.Checker.TLC, detail), HASH_A
                    ),
                )

    def test_other_checker_pass_and_counterexample_classify_alike(self) -> None:
        for other_verdict in triager.OTHER_CHECKER_COMPLETED:
            with self.subTest(other_verdict=other_verdict):
                actual = triager.classify_aggregator(
                    results(
                        triager.Checker.TLC,
                        "Attempted to apply Head to the empty sequence.",
                        other_verdict=other_verdict,
                    ),
                    HASH_A,
                )
                self.assertEqual("head-of-empty-sequence.md", actual)

    def test_unknown_and_missing_details_are_new(self) -> None:
        for detail in (None, "An unfamiliar diagnostic"):
            with self.subTest(detail=detail):
                self.assertEqual(
                    triager.NEW_FINDING,
                    triager.classify_aggregator(
                        results(triager.Checker.TLC, detail), HASH_A
                    ),
                )

    def test_existing_crash_classification_is_unchanged(self) -> None:
        diagnostic = "\n".join(
            (
                "TLC error code 2180 mapped to exit status 255",
                "Error: 0^0 is undefined.",
            )
        )
        self.assertEqual(
            "tlc-002.md",
            triager.classify(triager.CrashKind.TLC, diagnostic, HASH_A),
        )

    def test_classifies_worker_timeout(self) -> None:
        cases = (
            (triager.CrashKind.APALACHE, "Apalache worker timed out after PT30S"),
            (triager.CrashKind.TLC, "TLC worker timed out after PT30S"),
        )
        for crash_kind, diagnostic in cases:
            with self.subTest(crash_kind=crash_kind):
                self.assertEqual(
                    triager.WORKER_TIMEOUT,
                    triager.classify(crash_kind, diagnostic + "\n", HASH_A),
                )

    def test_worker_timeout_precedes_signature_match(self) -> None:
        diagnostic = "\n".join(
            (
                "Apalache worker timed out after PT30S",
                "java.lang.UnsupportedOperationException: "
                "Quantification over InfSet[CellTFrom(Int)] is not supported yet",
                "\tat at.forsyte.apalache.tla.bmcmt.rules.QuantRule.apply(QuantRule.scala:59)",
            )
        )
        self.assertEqual(
            triager.WORKER_TIMEOUT,
            triager.classify(triager.CrashKind.APALACHE, diagnostic, HASH_A),
        )

    def test_classifies_quantification_over_int_expression(self) -> None:
        diagnostic = "\n".join(
            (
                "java.lang.UnsupportedOperationException: "
                "Quantification over InfSet[CellTFrom(Int)] is not supported yet",
                "\tat at.forsyte.apalache.tla.bmcmt.rules.QuantRule.apply(QuantRule.scala:59)",
            )
        )
        self.assertEqual(
            "apalache-bmc-012.md",
            triager.classify(triager.CrashKind.APALACHE, diagnostic, HASH_A),
        )

    def test_quantification_expansion_stays_bmc_005(self) -> None:
        diagnostic = "\n".join(
            (
                "java.lang.UnsupportedOperationException: "
                "Expansion of InfSet[CellTFrom(Int)] is not supported yet",
                "\tat at.forsyte.apalache.tla.bmcmt.rules.QuantRule."
                "expandExistsOrForall(QuantRule.scala:120)",
                "\tat at.forsyte.apalache.tla.bmcmt.rules.QuantRule.apply(QuantRule.scala:69)",
            )
        )
        self.assertEqual(
            "apalache-bmc-005.md",
            triager.classify(triager.CrashKind.APALACHE, diagnostic, HASH_A),
        )

    def test_classifies_foldset_accumulator_membership(self) -> None:
        diagnostic = "\n".join(
            (
                "java.util.NoSuchElementException: key not found: $C$0",
                "\tat at.forsyte.apalache.tla.bmcmt.Binding.apply(Binding.scala:11)",
                "\tat at.forsyte.apalache.tla.bmcmt.rules.SetInRule.apply(SetInRule.scala:40)",
                "\tat at.forsyte.apalache.tla.bmcmt.rules.FoldSetRule."
                "$anonfun$apply$1(FoldSetRule.scala:111)",
                "\tat at.forsyte.apalache.tla.bmcmt.rules.FoldSetRule.apply(FoldSetRule.scala:96)",
            )
        )
        self.assertEqual(
            "apalache-bmc-013.md",
            triager.classify(triager.CrashKind.APALACHE, diagnostic, HASH_A),
        )

    def test_reports_ambiguous_matches(self) -> None:
        duplicate = triager.AggregatorSignature(
            "duplicate.md",
            triager.Checker.TLC,
            75,
            (triager.all_of(r"^In applying the function$"),),
        )
        original = triager.AGGREGATOR_SIGNATURES
        try:
            triager.AGGREGATOR_SIGNATURES = original + (duplicate,)
            with self.assertRaisesRegex(triager.TriageError, "multiple issues"):
                triager.classify_aggregator(
                    results(triager.Checker.TLC, "In applying the function"), HASH_A
                )
        finally:
            triager.AGGREGATOR_SIGNATURES = original


class AggregatorDirectoryTest(unittest.TestCase):
    def write_entry(self, directory: Path, name: str, document: object) -> Path:
        path = directory / name
        path.write_bytes(cbor2.dumps(document))
        return path

    def test_classifies_entries_in_hash_order(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            corpus = Path(temporary)
            directory = corpus / triager.AGGREGATOR_DIRECTORY
            directory.mkdir()
            self.write_entry(
                directory,
                f"{HASH_B}.cbor",
                envelope(triager.Checker.TLC, "unknown"),
            )
            self.write_entry(
                directory,
                f"{HASH_A}.cbor",
                envelope(
                    triager.Checker.APALACHE,
                    "<unknown>: unsupported expression: STRING",
                ),
            )

            self.assertEqual(
                [
                    (HASH_A, "string-set-unsupported.md"),
                    (HASH_B, triager.NEW_FINDING),
                ],
                triager.classify_aggregator_directory(corpus),
            )

    def test_rejects_malformed_cbor(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            corpus = Path(temporary)
            directory = corpus / triager.AGGREGATOR_DIRECTORY
            directory.mkdir()
            (directory / f"{HASH_A}.cbor").write_bytes(b"\x1a")
            with self.assertRaisesRegex(triager.TriageError, "cannot decode"):
                triager.classify_aggregator_directory(corpus)

    def test_rejects_symlink(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            corpus = Path(temporary)
            directory = corpus / triager.AGGREGATOR_DIRECTORY
            directory.mkdir()
            target = corpus / "target.cbor"
            target.write_bytes(cbor2.dumps(envelope(triager.Checker.TLC, "unknown")))
            (directory / f"{HASH_A}.cbor").symlink_to(target)
            with self.assertRaisesRegex(triager.TriageError, "not a regular file"):
                triager.classify_aggregator_directory(corpus)

    def test_rejects_trailing_cbor_data(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            corpus = Path(temporary)
            directory = corpus / triager.AGGREGATOR_DIRECTORY
            directory.mkdir()
            path = self.write_entry(
                directory,
                f"{HASH_A}.cbor",
                envelope(triager.Checker.TLC, "unknown"),
            )
            path.write_bytes(path.read_bytes() + b"trailing")
            with self.assertRaisesRegex(triager.TriageError, "trailing CBOR data"):
                triager.classify_aggregator_directory(corpus)

    def test_rejects_missing_failure_code(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            corpus = Path(temporary)
            directory = corpus / triager.AGGREGATOR_DIRECTORY
            directory.mkdir()
            document = envelope(triager.Checker.TLC, "unknown")
            del document["stages"]["tlc"]["code"]  # type: ignore[index]
            self.write_entry(directory, f"{HASH_A}.cbor", document)
            with self.assertRaisesRegex(triager.TriageError, "has no code"):
                triager.classify_aggregator_directory(corpus)

    def test_rejects_unexpected_file(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            corpus = Path(temporary)
            directory = corpus / triager.AGGREGATOR_DIRECTORY
            directory.mkdir()
            (directory / "notes.txt").write_text("unexpected", encoding="utf-8")
            with self.assertRaisesRegex(triager.TriageError, "unexpected file"):
                triager.classify_aggregator_directory(corpus)


class ReportTest(unittest.TestCase):
    def test_writes_csv_atomically(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "report.csv"
            triager.write_report(path, [(HASH_A, triager.NEW_FINDING)])
            with path.open(newline="", encoding="utf-8") as source:
                self.assertEqual(
                    [["entry_hash", "issue"], [HASH_A, triager.NEW_FINDING]],
                    list(csv.reader(source)),
                )
            self.assertEqual([], list(path.parent.glob(".*.tmp")))


if __name__ == "__main__":
    unittest.main()
