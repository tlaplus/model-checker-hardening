from __future__ import annotations

import contextlib
import csv
import io
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

    def test_classifies_tlc_constant_property_restrictions(self) -> None:
        cases = (
            ("The property of Prop is equal to FALSE", 150),
            ("The spec is trivially false because FALSE is false.", 150),
            # corpus22 4660e132: TLC names a bound variable of the property.
            ("The spec is trivially false because q36 is false.", 150),
            ("Temporal formula is a tautology (its negation is unsatisfiable).", 75),
        )
        for detail, code in cases:
            for other in ("pass", "counterexample"):
                with self.subTest(detail=detail, other=other):
                    self.assertEqual(
                        "constant-property-tlc-rejects.md",
                        triager.classify_aggregator(
                            results(triager.Checker.TLC, detail, other_verdict=other, code=code),
                            HASH_A,
                        ),
                    )

    def test_classifies_apalache_temporal_limitations(self) -> None:
        cases = (
            ("<unknown>: unsupported expression: ENABLED (step < 5)", "enabled-apalache-unsupported.md"),
            # corpus20 e339bb85: the stored detail can end right after the operator.
            ("<unknown>: unsupported expression: ENABLED", "enabled-apalache-unsupported.md"),
            (
                "scala.NotImplementedError: Handling fairness is not supported yet!",
                "fairness-apalache-unsupported.md",
            ),
        )
        for detail, issue in cases:
            for other in ("pass", "counterexample"):
                with self.subTest(issue=issue, other=other):
                    self.assertEqual(
                        issue,
                        triager.classify_aggregator(
                            results(triager.Checker.APALACHE, detail, other_verdict=other), HASH_A
                        ),
                    )

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

    def test_sequence_index_out_of_bounds_is_application_outside_domain(self) -> None:
        """TLC reports an out-of-range sequence or tuple index as a tuple access."""
        for detail in (
            "Attempted to access index 0 of tuple",
            "Attempted to access index 3 of tuple",
            "Attempted to access index -2 of tuple",
        ):
            with self.subTest(detail=detail):
                self.assertEqual(
                    "function-application-outside-domain.md",
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
                # corpus22 17d662de: the CASE is inside the action of ENABLED.
                "In computing ENABLED, TLC encountered a CASE with no conditions true.",
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

    def test_classifies_empty_codomain_pick(self) -> None:
        diagnostic = "\n".join(
            (
                "java.lang.RuntimeException: The set $C$8 is statically empty. "
                "Pick should not be called on that.",
                "\tat at.forsyte.apalache.tla.bmcmt.rules.support.CherryPick.pick(CherryPick.scala:81)",
                "\tat at.forsyte.apalache.tla.bmcmt.rules.support.CherryPick."
                "$anonfun$pickFunFromFunSet$6(CherryPick.scala:998)",
                "\tat at.forsyte.apalache.tla.bmcmt.rules.support.CherryPick."
                "pickFunFromFunSet(CherryPick.scala:997)",
            )
        )
        self.assertEqual(
            "apalache-bmc-018.md",
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

    def test_classifies_next_state_cardinality_as_unmapped_exit_status(self) -> None:
        """corpus19 662f7598: Cardinality(Int) in Next exits under 2181."""
        diagnostic = "\n".join(
            (
                "TLC error code 2181 mapped to exit status 255",
                "Finished computing initial states: 1 distinct state generated.",
                "Error: Attempted to compute cardinality of the value",
                "Int",
                "Error: The behavior up to this point is:",
            )
        )
        self.assertEqual(
            "tlc-002.md",
            triager.classify(triager.CrashKind.TLC, diagnostic, HASH_A),
        )

    def test_classifies_invariant_error_with_foreign_call_stack(self) -> None:
        """corpus18 fabd5358: the message is the invariant's, the code is Head's."""
        diagnostic = "\n".join(
            (
                "TLC error code 2184 mapped to exit status 255",
                "Error: Evaluating invariant Inv failed.",
                "Attempted to compute the value of an expression of form",
                "CHOOSE x \\in S: P, but no element of S satisfied P.",
                "line 112, col 4 to line 163, col 8 of module FuzzInput",
                "Error: The behavior up to this point is:",
                "Error: The error occurred when TLC was evaluating the nested",
                "expressions at the following positions:",
                "0. Line 59, column 9 to line 106, column 25 in FuzzInput",
            )
        )
        self.assertEqual(
            "tlc-007.md",
            triager.classify(triager.CrashKind.TLC, diagnostic, HASH_A),
        )

    def test_classifies_constant_eventuality_error_before_initial_states_as_tlc_009(self) -> None:
        """corpus20 0719eea2 and 77176b08: the property is <> over a constant CHOOSE."""
        for exception, message in (
            ("tlc2.tool.EvalException", ": Attempted to apply Head to the empty sequence."),
            ("java.lang.RuntimeException", ": Attempted to compute the value of an expression of form"),
        ):
            diagnostic = "\n".join(
                (
                    "TLC error code 1000 mapped to exit status 255",
                    "Starting... (2026-09-14 16:05:12)",
                    "Error: TLC threw an unexpected exception.",
                    "This was probably caused by an error in the spec or model.",
                    "See the User Output or TLC Console for clues to what happened.",
                    f"The exception was a {exception}",
                    message,
                    "Finished in 00s at (2026-09-14 16:05:12)",
                )
            )
            with self.subTest(exception=exception):
                self.assertEqual(
                    "tlc-009.md",
                    triager.classify(triager.CrashKind.TLC, diagnostic, HASH_A),
                )

    def test_error_1000_while_computing_states_is_not_tlc_009(self) -> None:
        diagnostic = "\n".join(
            (
                "TLC error code 1000 mapped to exit status 255",
                "Starting... (2026-09-14 16:05:12)",
                "Computing initial states...",
                "Error: TLC threw an unexpected exception.",
                "The exception was a java.lang.RuntimeException",
                ": In applying the function",
                "which is not in its domain.",
            )
        )
        self.assertEqual(
            "tlc-001.md",
            triager.classify(triager.CrashKind.TLC, diagnostic, HASH_A),
        )

    def test_module_error_inside_invariant_with_call_stack_stays_tlc_002(self) -> None:
        """corpus18 a6be9eb6: the invariant raised the reported module error itself."""
        diagnostic = "\n".join(
            (
                "TLC error code 2178 mapped to exit status 255",
                "Error: Evaluating invariant Inv failed.",
                "Overflow when computing 88^5",
                "Error: The behavior up to this point is:",
                "Error: The error occurred when TLC was evaluating the nested",
                "expressions at the following positions:",
                "0. Line 48, column 8 to line 48, column 24 in FuzzInput",
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

    def test_classifies_enabled_unchanged_of_expression(self) -> None:
        # corpus24 d65d9c03 and 7fd26b9f: stored truncated, no WF or SF in the spec.
        detail = ("The action formula A appearing in a WF_v(A) or SF_v(A) operator "
                  "does not specif…")
        for other in ("pass", "counterexample"):
            with self.subTest(other=other):
                self.assertEqual(
                    "tlc-012.md",
                    triager.classify_aggregator(
                        results(triager.Checker.TLC, detail, other_verdict=other), HASH_A),
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
            with contextlib.redirect_stderr(io.StringIO()) as stderr:
                classification = triager.classify_aggregator(
                    results(triager.Checker.TLC, "In applying the function"), HASH_A
                )
            self.assertNotEqual("duplicate.md", classification)
            self.assertIn("multiple issues", stderr.getvalue())
        finally:
            triager.AGGREGATOR_SIGNATURES = original


class FirstMatchTest(unittest.TestCase):
    def test_first_match_prefers_catalog_order_and_warns(self):
        with contextlib.redirect_stderr(io.StringIO()) as stderr:
            self.assertEqual("NEW", triager.first_match([], "path", "issues"))
            self.assertEqual("a.md", triager.first_match(["a.md", "a.md"], "path", "issues"))
            self.assertEqual("", stderr.getvalue())
            self.assertEqual("b.md", triager.first_match(["b.md", "a.md"], "path", "findings"))
        self.assertEqual(
            "triager: warning: path matches multiple findings: b.md, a.md; using b.md\n",
            stderr.getvalue(),
        )


DOMAIN_ERROR_ESCAPE = (
    "Error: TLC threw an unexpected exception.",
    "The exception was a java.lang.RuntimeException",
    ": In applying the function",
    "[Tag31 |-> {}],",
    "which is not in its domain.",
)


def classify_quietly(
    test: unittest.TestCase, kind: triager.CrashKind, diagnostic: str
) -> str:
    """Classify and require that no ambiguity warning was printed."""
    with contextlib.redirect_stderr(io.StringIO()) as stderr:
        classification = triager.classify(kind, diagnostic, HASH_A)
    test.assertEqual("", stderr.getvalue())
    return classification


class Corpus22CrashTest(unittest.TestCase):
    def test_domain_error_before_initial_states_is_only_tlc_009(self) -> None:
        """corpus22 048fbcdd: a domain error in a constant ~> operand."""
        diagnostic = "\n".join(
            (
                "TLC error code 1000 mapped to exit status 255",
                "Starting... (2026-09-14 18:29:36)",
                *DOMAIN_ERROR_ESCAPE,
            )
        )
        self.assertEqual(
            "tlc-009.md", classify_quietly(self, triager.CrashKind.TLC, diagnostic)
        )

    def test_liveness_escape_after_initial_states_is_tlc_010(self) -> None:
        """corpus22 358f84eb (wrapped) and 3eedba61 (behavior only)."""
        prefix = (
            "TLC error code 1000 mapped to exit status 255",
            "Starting... (2026-09-14 18:50:03)",
            "Implied-temporal checking--satisfiability problem has 1 branches.",
            "Computing initial states...",
            "Finished computing initial states: 1 distinct state generated at 2026-09-14 18:50:03.",
        )
        for name, body in (
            ("wrapped", ("Error: The error occurred when TLC was evaluating the nested",
                         *DOMAIN_ERROR_ESCAPE)),
            ("behavior only", ("Error: The behavior up to this point is:",
                               "State 1: <Initial predicate>")),
        ):
            with self.subTest(name):
                self.assertEqual(
                    "tlc-010.md",
                    classify_quietly(
                        self, triager.CrashKind.TLC, "\n".join((*prefix, *body))
                    ),
                )

    def test_domain_error_while_computing_initial_states_stays_tlc_001(self) -> None:
        diagnostic = "\n".join(
            (
                "TLC error code 1000 mapped to exit status 255",
                "Starting... (2026-09-14 18:50:03)",
                "Implied-temporal checking--satisfiability problem has 1 branches.",
                "Computing initial states...",
                *DOMAIN_ERROR_ESCAPE,
            )
        )
        self.assertEqual(
            "tlc-001.md", classify_quietly(self, triager.CrashKind.TLC, diagnostic)
        )

    def test_module_error_in_action_property_is_tlc_002(self) -> None:
        """corpus22 8e1c9a32 (SubSeq, 2183) and 9ba661a0 (0^0, 2180)."""
        for code, message in (
            (2183, "The second argument of SubSeq must be in the domain of its first argument:"),
            (2180, "0^0 is undefined."),
        ):
            diagnostic = "\n".join(
                (
                    f"TLC error code {code} mapped to exit status 255",
                    "Error: Evaluating action property Prop failed.",
                    message,
                    "Error: The behavior up to this point is:",
                )
            )
            with self.subTest(code=code):
                self.assertEqual(
                    "tlc-002.md",
                    classify_quietly(self, triager.CrashKind.TLC, diagnostic),
                )

    def test_classifies_apalache_assignment_errors(self) -> None:
        """corpus22 7d93a530 and a spurious manual assignment."""
        for message in (
            "Illegal assignment inside an assignment-free expression.",
            "Manual assignment is spurious, var1 is already assigned!",
        ):
            diagnostic = "\n".join(
                (
                    "Apalache exited with status 255",
                    "PASS #10: TransitionFinderPass                                    I@21:30:56.800",
                    f"Assignment error: <[UNKNOWN]>: {message} See "
                    "https://apalache-mc.org/docs/apalache/principles/assignments.html E@21:30:56.841",
                    "EXITCODE: ERROR (255)",
                )
            )
            with self.subTest(message=message):
                self.assertEqual(
                    "apalache-assignments-001.md",
                    classify_quietly(self, triager.CrashKind.APALACHE, diagnostic),
                )

    def test_empty_unexpected_expression_in_temporal_pass(self) -> None:
        """corpus22 9e4e2256: (~var0 ~> var1) ~> FALSE."""
        diagnostic = "\n".join(
            (
                "Apalache exited with status 255",
                "PASS #5: TemporalPass                                             I@21:16:48.122",
                "  > Rewriting temporal operators...                               I@21:16:48.122",
                "  > Adding logic for loop finding                                 I@21:16:48.123",
                "<unknown>: unexpected expression:                                 E@21:16:48.130",
                "Unexpected expressions in the specification (see the error messages) E@21:16:48.130",
                "EXITCODE: ERROR (255)",
            )
        )
        self.assertEqual(
            "apalache-temporal-001.md",
            classify_quietly(self, triager.CrashKind.APALACHE, diagnostic),
        )

    def test_unexpected_expression_in_a_later_pass_is_not_temporal_001(self) -> None:
        diagnostic = "\n".join(
            (
                "Apalache exited with status 255",
                "PASS #5: TemporalPass                                             I@21:16:48.122",
                "PASS #11: OptimizationPass                                        I@21:16:48.200",
                "<unknown>: unexpected expression:                                 E@21:16:48.230",
                "EXITCODE: ERROR (255)",
            )
        )
        self.assertEqual(
            "NEW", classify_quietly(self, triager.CrashKind.APALACHE, diagnostic)
        )

    def test_foldset_lambda_type_error_is_temporal_002(self) -> None:
        """corpus22 36cd5261."""
        diagnostic = "\n".join(
            (
                "Apalache exited with status 255",
                "PASS #13: BoundedChecker                                          I@21:27:55.900",
                "<unknown>: internal error in type checking: FoldSet argument Lambda26$1 should "
                "have the tag ((Bool, MODEL) => Bool), found Bool. E@21:27:56.125",
            )
        )
        self.assertEqual(
            "apalache-temporal-002.md",
            classify_quietly(self, triager.CrashKind.APALACHE, diagnostic),
        )

    def test_unassigned_quantified_variable_in_temporal_property_is_temporal_003(self) -> None:
        """corpus23 99aa1906: \\E q27 \\in {} around <> and ~>."""
        diagnostic = "\n".join(
            (
                "Apalache exited with status 255",
                "  > Set a temporal property to Liveness                           I@08:16:37.953",
                "PASS #13: BoundedChecker                                          I@08:16:38.050",
                "This error may show up when CONSTANTS are not initialized.        E@08:16:38.167",
                "Input error (see the manual): SubstRule: Variable q27$1 is not assigned a value E@08:16:38.174",
            )
        )
        self.assertEqual(
            "apalache-temporal-003.md",
            classify_quietly(self, triager.CrashKind.APALACHE, diagnostic),
        )

    def test_unassigned_variable_without_temporal_property_stays_new(self) -> None:
        diagnostic = "\n".join(
            (
                "Apalache exited with status 255",
                "PASS #13: BoundedChecker                                          I@08:16:38.050",
                "Input error (see the manual): SubstRule: Variable N$1 is not assigned a value E@08:16:38.174",
            )
        )
        self.assertEqual(
            "NEW", classify_quietly(self, triager.CrashKind.APALACHE, diagnostic)
        )

    def test_doubly_separated_name_is_temporal_004(self) -> None:
        """corpus23 a840f3c3: a two-variable function constructor under ~>."""
        diagnostic = "\n".join(
            (
                "Apalache exited with status 255",
                "  > UniqueRenamer                                                 I@07:20:14.694",
                "Unhandled exception                                               E@07:20:14.695",
                "java.lang.IllegalArgumentException: Variable names should never contain more than one separator",
                "\tat at.forsyte.apalache.tla.lir.transformations.standard.IncrementalRenaming$.parseName(IncrementalRenaming.scala:43)",
            )
        )
        self.assertEqual(
            "apalache-temporal-004.md",
            classify_quietly(self, triager.CrashKind.APALACHE, diagnostic),
        )

    def test_builder_stack_overflow_is_builder_001(self) -> None:
        """corpus23 d0f6bfa2: -20480 .. 0 \\subseteq {}."""
        diagnostic = "\n".join(
            (
                "Apalache exited with status 255",
                "PASS #13: BoundedChecker                                          I@07:36:17.671",
                "Unhandled exception                                               E@07:36:22.594",
                "java.lang.StackOverflowError",
                "\tat scala.collection.Iterator$$anon$6.hasNext(Iterator.scala:487)",
                "\tat at.forsyte.apalache.tla.types.TypeUnifier.unify(TypeUnifier.scala:49)",
                "\tat at.forsyte.apalache.tla.typecomp.signatures.FlexibleEquality$.commonSupertype(FlexibleEquality.scala:23)",
            )
        )
        self.assertEqual(
            "apalache-builder-001.md",
            classify_quietly(self, triager.CrashKind.APALACHE, diagnostic),
        )


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
