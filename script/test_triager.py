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

    def test_classifies_corpus8_residual_details(self) -> None:
        """Details replayed from corpus8's NEW aggregator rows.

        Each is stored exactly as the corpus holds it: TLC's first "Error:" line,
        stripped to one line and cut to 80 characters.
        """
        cases = (
            (
                "In evaluation, the identifier step is either undefined or not an operator.",
                "apalache-printer-008.md",
            ),
            (
                "TLC expected a boolean value, but did not find one. line 31, col 3 to line 186,…",
                "apalache-printer-008.md",
            ),
            (
                "Attempted to check equality of integer 0 with non-integer:",
                "apalache-printer-008.md",
            ),
            (
                "Attempted to apply the operator DOMAIN to a non-function",
                "apalache-printer-008.md",
            ),
            (
                "Overflow when computing -657264081*84",
                "integer-outside-tlc-range.md",
            ),
            (
                "Attempted to compare overridden value Seq({<<[field5 |-> FALSE, field6 |-> {}],…",
                "finite-set-containing-infinite-set.md",
            ),
            (
                "Attempted to check if the non-enumerable value",
                "infinite-set-as-membership-element.md",
            ),
            # TLC wraps these two variants at different points, so the stored
            # first line is what separates the classes. A regression that
            # merges them files the enumeration limit as a missing witness.
            (
                "Attempted to compute the value of an expression of form",
                "choose-without-witness.md",
            ),
            (
                "Attempted to compute the value of an expression of",
                "choose-over-infinite-set.md",
            ),
        )
        for detail, issue in cases:
            with self.subTest(detail=detail):
                self.assertEqual(
                    issue,
                    triager.classify_aggregator(
                        results(triager.Checker.TLC, detail), HASH_A
                    ),
                )

    def test_set_valued_invariant_is_printer_corruption(self) -> None:
        """Code 150 splits by value: FALSE is a TLC restriction, a set is corruption."""
        cases = (
            ("The invariant of Inv is equal to FALSE", "constant-false-invariant.md"),
            ("The invariant of Inv is equal to {}", "apalache-printer-008.md"),
            (
                'The invariant of Inv is equal to {"default_OF_MODEL"} \\cup TRUE',
                "apalache-printer-008.md",
            ),
        )
        for detail, issue in cases:
            with self.subTest(detail=detail):
                self.assertEqual(
                    issue,
                    triager.classify_aggregator(
                        results(triager.Checker.TLC, detail, code=150), HASH_A
                    ),
                )

    def test_classifies_details_stored_as_the_innermost_failure(self) -> None:
        """Root causes TLC reports inside a wrapper.

        A corpus written before `TlcFailureDetail` unwrapped those wrappers stores
        the wrapper instead; see test_truncated_wrapper_details_stay_new.
        """
        cases = (
            (
                "Cannot cast tlc2.value.impl.BoolValue to tlc2.value.impl.IntValue",
                "apalache-printer-008.md",
            ),
            (
                "In computing next states, TLC encountered a CASE with no conditions true.",
                "case-without-matching-arm.md",
            ),
            (
                "Attempted to check if expression of form {x \\in S : p(x)} is a finite set, but c…",
                "apalache-bmc-007.md",
            ),
        )
        for detail, issue in cases:
            with self.subTest(detail=detail):
                self.assertEqual(
                    issue,
                    triager.classify_aggregator(
                        results(triager.Checker.TLC, detail), HASH_A
                    ),
                )

    def test_truncated_wrapper_details_stay_new(self) -> None:
        """TLC reports a wrapper first, so the stored line cannot identify a class."""
        for detail in (
            "Evaluating invariant Inv failed.",
            "TLC threw an unexpected exception.",
            "Attempted to apply the operator overridden by the Java method",
        ):
            with self.subTest(detail=detail):
                self.assertEqual(
                    triager.NEW_FINDING,
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

    def test_classifies_foldseq_accumulator_membership(self) -> None:
        diagnostic = "\n".join(
            (
                "java.util.NoSuchElementException: key not found: $C$0",
                "\tat at.forsyte.apalache.tla.bmcmt.Binding.apply(Binding.scala:11)",
                "\tat at.forsyte.apalache.tla.bmcmt.rules.SetInRule.apply(SetInRule.scala:40)",
                "\tat at.forsyte.apalache.tla.bmcmt.rules.NegRule.apply(NegRule.scala:27)",
                "\tat at.forsyte.apalache.tla.bmcmt.rules.FoldSeqRule.binOp$1(FoldSeqRule.scala:72)",
                "\tat at.forsyte.apalache.tla.bmcmt.rules.FoldSeqRule.apply(FoldSeqRule.scala:76)",
            )
        )
        self.assertEqual(
            "apalache-bmc-013.md",
            triager.classify(triager.CrashKind.APALACHE, diagnostic, HASH_A),
        )

    def test_classifies_corpus10_residual_details(self) -> None:
        """Details replayed from corpus10's NEW aggregator rows.

        Each is stored exactly as the corpus holds it: TLC's first "Error:"
        line, stripped to one line and cut to 80 characters.
        """
        cases = (
            (
                "In computing next states, the right side of \\IN is not enumerable.",
                "non-enumerable-initial-assignment.md",
            ),
            (
                "Attempted to check if the set Nat \\cap Intis finite.",
                "apalache-bmc-007.md",
            ),
            (
                "Attempted to check if the set {0} \\cup Int \\ Natis finite.",
                "apalache-bmc-007.md",
            ),
            (
                "Successor state is not completely specified by action Next of the next-state re\u2026",
                "apalache-printer-008.md",
            ),
        )
        for detail, issue in cases:
            with self.subTest(detail=detail):
                self.assertEqual(
                    issue,
                    triager.classify_aggregator(
                        results(triager.Checker.TLC, detail), HASH_A
                    ),
                )

    def test_classifies_module_overflow_as_unmapped_exit_status(self) -> None:
        diagnostic = "\n".join(
            (
                "TLC error code 2178 mapped to exit status 255",
                "Error: Evaluating invariant Inv failed.",
                "Overflow when computing -57^6",
            )
        )
        self.assertEqual(
            "tlc-002.md",
            triager.classify(triager.CrashKind.TLC, diagnostic, HASH_A),
        )

    def test_set_map_and_set_filter_are_separate_findings(self) -> None:
        set_map = "\n".join(
            (
                "scala.NotImplementedError: A set map over PowSet[Set(Str)] is not implemented",
                "\tat at.forsyte.apalache.tla.bmcmt.rules.support.MapBase."
                "findSetCellAndElemType$1(MapBase.scala:48)",
                "\tat at.forsyte.apalache.tla.bmcmt.rules.SetMapRule.apply(SetMapRule.scala:29)",
            )
        )
        set_filter = "\n".join(
            (
                "scala.NotImplementedError: A set filter over PowSet[Set(Str)] is not implemented",
                "\tat at.forsyte.apalache.tla.bmcmt.rules.SetFilterRule.apply(SetFilterRule.scala:32)",
            )
        )
        self.assertEqual(
            "apalache-bmc-014.md",
            triager.classify(triager.CrashKind.APALACHE, set_map, HASH_A),
        )
        self.assertEqual(
            "apalache-bmc-001.md",
            triager.classify(triager.CrashKind.APALACHE, set_filter, HASH_A),
        )

    def test_classifies_truncated_and_new_overridden_value_details(self) -> None:
        results = {
            triager.Checker.TLC: triager.CheckerResult(
                "fail", 75,
                "Attempted to compute the number of elements in the overridden "
                "value Seq({FALSE}\u2026"),
            triager.Checker.APALACHE: triager.CheckerResult("counterexample", None, None),
        }
        self.assertEqual(
            "function-over-infinite-domain.md", triager.classify_aggregator(results, HASH_A))
        results[triager.Checker.TLC] = triager.CheckerResult(
            "fail", 75,
            "Attempted to compute the number of elements in the overridden value STRING.")
        results[triager.Checker.APALACHE] = triager.CheckerResult("pass", None, None)
        self.assertEqual(
            "string-set-tlc-fails.md", triager.classify_aggregator(results, HASH_A))

    def test_classifies_undecidable_membership_over_nat(self) -> None:
        results = {
            triager.Checker.TLC: triager.CheckerResult(
                "fail", 75, "Cannot decide if element:"),
            triager.Checker.APALACHE: triager.CheckerResult("pass", None, None),
        }
        self.assertEqual(
            "filter-over-infinite-set.md", triager.classify_aggregator(results, HASH_A))

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


class UniqueMatchTest(unittest.TestCase):
    def test_unique_match_preserves_ambiguity_wording_and_sorting(self):
        self.assertEqual("NEW", triager.unique_match(set(), "path", "issues"))
        self.assertEqual("a.md", triager.unique_match({"a.md"}, "path", "issues"))
        with self.assertRaisesRegex(triager.TriageError, "path matches multiple findings: a.md, b.md"):
            triager.unique_match({"b.md", "a.md"}, "path", "findings")


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
