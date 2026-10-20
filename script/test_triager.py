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


# The checker each fixture compares a failing checker with, by default.
OTHER = {
    triager.Checker.TLC: triager.Checker.APALACHE,
    triager.Checker.TLCN: triager.Checker.APALACHE,
    triager.Checker.APALACHE: triager.Checker.TLC,
}


def results(
    failed: triager.Checker,
    detail: str | None,
    *,
    other_verdict: str = "pass",
    code: int = 75,
) -> dict[triager.Checker, triager.CheckerResult]:
    return {
        failed: triager.CheckerResult("fail", code, detail),
        OTHER[failed]: triager.CheckerResult(other_verdict, None, None),
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
        OTHER[failed].value: {"verdict": other_verdict},
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

    def test_classifies_subseq_third_argument_outside_domain(self) -> None:
        """corpus29 343b4004 and dcffb35f."""
        actual = triager.classify_aggregator(
            results(
                triager.Checker.TLC,
                "The third argument of SubSeq must be in the domain of its first argument:",
            ),
            HASH_A,
        )
        self.assertEqual("subseq-outside-domain.md", actual)

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

    def test_classifies_community_modules_override_details(self) -> None:
        """corpus43: 0014d801, 0051805f, 021b351f and 01a17416."""
        for detail, expected in (
            ("The argument of IsInjective should be a function, but instead it is:",
             "tlc-016.md"),
            ("The value", "tlc-017.md"),
            ("The argument of LongestCommonPrefix should be a non-empty set, but instead it i\u2026",
             "choose-without-witness.md"),
            ("Applying FoldBag to the following value,", "bag-nonpositive-multiplicity.md"),
        ):
            for other in ("pass", "counterexample"):
                with self.subTest(detail=detail, other=other):
                    with contextlib.redirect_stderr(io.StringIO()) as stderr:
                        actual = triager.classify_aggregator(
                            results(triager.Checker.TLC, detail, other_verdict=other), HASH_A)
                    self.assertEqual(expected, actual)
                    self.assertEqual("", stderr.getvalue())

    def test_reports_ambiguous_matches(self) -> None:
        duplicate = triager.AggregatorSignature(
            "duplicate.md",
            triager.Tool.TLC,
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

    def test_module_error_in_invariant_without_error_prefix_is_tlc_002(self) -> None:
        """corpus29 5ec18198: % by 0 in Inv, printed without its own "Error:" prefix."""
        for code, message in (
            (2169, "The second argument of % should be a positive number, but instead it is:"),
            (2183, "The third argument of SubSeq must be in the domain of its first argument:"),
        ):
            diagnostic = "\n".join(
                (
                    f"TLC error code {code} mapped to exit status 255",
                    "Error: Evaluating invariant Inv failed.",
                    message,
                    "0",
                    "Error: The behavior up to this point is:",
                    "Error: The error occurred when TLC was evaluating the nested",
                    "expressions at the following positions:",
                    "0. Line 60, column 3 to line 60, column 76 in FuzzInput",
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

    def test_inliner_unification_error_is_temporal_002(self) -> None:
        """corpus29 69b942b4: the ApaFoldSeqLeft variant of the nested one-conjunct list."""
        diagnostic = "\n".join(
            (
                "Apalache exited with status 255",
                "PASS #13: BoundedChecker                                          I@19:01:46.403",
                "<unknown>: internal error in type checking: Inliner: Unable to unify the signature "
                "Bool of Lambda108$1 with the type $callSiteType at call site E@19:01:46.224",
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

    def test_builder_state_chain_without_typecomp_is_builder_001(self) -> None:
        """corpus46 fd8ffc38: the printed trace ends inside the IndexedStateT chain."""
        diagnostic = "\n".join(
            (
                "Apalache exited with status 255",
                "PASS #13: BoundedChecker                                          I@05:46:49.192",
                "Unhandled exception                                               E@05:46:49.631",
                "java.lang.StackOverflowError",
                "\tat scala.collection.immutable.List.filterNot(List.scala:505)",
                "\tat at.forsyte.apalache.tla.pp.ConstSimplifierBase.$anonfun$simplifyShallow$1(ConstSimplifierBase.scala:181)",
                "\tat at.forsyte.apalache.tla.bmcmt.rewriter.ConstSimplifierForSmt.$anonfun$simplifyShallow$1(ConstSimplifierForSmt.scala:70)",
                "\tat scalaz.IndexedStateT.$anonfun$map$3(StateT.scala:89)",
                "\tat scalaz.IdInstances$$anon$1.point(Id.scala:20)",
                "\tat scalaz.IndexedStateT.apply(StateT.scala:14)",
            )
        )
        self.assertEqual(
            "apalache-builder-001.md",
            classify_quietly(self, triager.CrashKind.APALACHE, diagnostic),
        )

    def test_community_modules_override_errors(self) -> None:
        """corpus43 177b83d4, 3914df96, 070a0d4b and 932e82c8."""
        for expected, lines in (
            ("tlc-016.md", (
                "TLC error code 2283 mapped to exit status 255",
                "Error: The argument of IsInjective should be a function, but instead it is:",
                "(FALSE :> FALSE @@ TRUE :> FALSE)",
                "Error: The behavior up to this point is:",
            )),
            ("tlc-017.md", (
                "TLC error code 2154 mapped to exit status 255",
                "Error: Attempted to apply the operator overridden by the Java method",
                "public static tlc2.value.impl.Value tlc2.overrides.Functions.antiFunction(tlc2.value.impl.Value),",
                "but it produced the following error:",
                "The value",
                "{{FALSE}, {TRUE}}",
                "occurs multiple times in the function domain.",
            )),
            ("tlc-002.md", (
                "TLC error code 2283 mapped to exit status 255",
                "Error: The argument of LongestCommonPrefix should be a non-empty set, but instead it is:",
                "{}",
            )),
            ("tlc-002.md", (
                "TLC error code 2176 mapped to exit status 255",
                "Error: Evaluating action property Prop failed.",
                "Applying FoldBag to the following value,",
                "which is not an element of Nat:",
                "0 (in: 1:>0)",
            )),
        ):
            with self.subTest(expected=expected, first=lines[1]):
                self.assertEqual(
                    expected,
                    classify_quietly(self, triager.CrashKind.TLC, "\n".join(lines)),
                )

    def test_foreign_error_under_a_community_modules_constant_stays_new(self) -> None:
        """Error 2283 alone does not identify an override."""
        diagnostic = "\n".join((
            "TLC error code 2283 mapped to exit status 255",
            "Error: The argument of SumSet should be a set of integers, but instead it is:",
        ))
        self.assertEqual(
            triager.NEW_FINDING,
            classify_quietly(self, triager.CrashKind.TLC, diagnostic),
        )


class Corpus53CrashTest(unittest.TestCase):
    INITIAL_STATES = (
        "TLC error code 1000 mapped to exit status 255",
        "Starting... (2026-09-26 16:01:13)",
        "Implied-temporal checking--satisfiability problem has 1 branches.",
        "Computing initial states...",
    )

    def test_tableau_error_on_initial_state_is_tlc_010(self) -> None:
        """corpus53 415f8dad (tuple index) and d4d262c4 (Head, then the invariant)."""
        for name, body in (
            ("tuple index", (
                "Error: Attempted to access index 0 of tuple",
                "<<TRUE, FALSE>>",
                "which is out of bounds.",
                "While working on the initial state:",
            )),
            ("with invariant violation", (
                "Error: Attempted to apply Head to the empty sequence.",
                "Error: Invariant Inv is violated by the initial state:",
            )),
        ):
            with self.subTest(name):
                self.assertEqual(
                    "tlc-010.md",
                    classify_quietly(
                        self, triager.CrashKind.TLC, "\n".join((*self.INITIAL_STATES, *body))
                    ),
                )

    def test_tableau_error_needs_implied_temporal_checking(self) -> None:
        diagnostic = "\n".join((
            "TLC error code 1000 mapped to exit status 255",
            "Starting... (2026-09-26 16:01:13)",
            "Computing initial states...",
            "Error: Attempted to access index 0 of tuple",
        ))
        self.assertEqual(
            triager.NEW_FINDING,
            classify_quietly(self, triager.CrashKind.TLC, diagnostic),
        )

    def test_stack_overflow_is_tlc_performance_001(self) -> None:
        """corpus53 0dee39ab: SeqReverse of a sequence that doubles in every step."""
        diagnostic = "\n".join((
            "TLC error code 1005 mapped to exit status 255",
            "Finished computing initial states: 1 distinct state generated at 2026-09-26 19:46:55.",
            "Error: This was a Java StackOverflowError. It was probably the result",
            "of an incorrect recursive function definition that caused TLC to enter",
        ))
        self.assertEqual(
            "tlc-performance-001.md",
            classify_quietly(self, triager.CrashKind.TLC, diagnostic),
        )

    def test_general_error_stack_overflow_is_tlc_performance_001(self) -> None:
        """corpus58 c10a1c47: SetUnionAll of a powerset overflows under error 1000."""
        diagnostic = "\n".join((
            "TLC error code 1000 mapped to exit status 255",
            "Linting of module FuzzInput",
            "Error: This was a Java StackOverflowError. It was probably the result",
            "of an incorrect recursive function definition that caused TLC to enter",
            "While working on the initial state:",
        ))
        self.assertEqual(
            "tlc-performance-001.md",
            classify_quietly(self, triager.CrashKind.TLC, diagnostic),
        )

    def test_liveness_trace_stack_overflow_is_tlc_019(self) -> None:
        """corpus64 4c8676fa, rerun with -Xss512k and the worker's shutdown hook."""
        diagnostic = "\n".join((
            "TLC worker exited while processing the input",
            "TLC worker stderr:",
            "Checking temporal properties for the complete state space with 18 total distinct states at (2026-10-16 03:54:47)",
            "Error: TLC threw an unexpected exception.",
            "This was probably caused by an error in the spec or model.",
            "See the User Output or TLC Console for clues to what happened.",
            "The exception was a java.util.concurrent.ExecutionException",
            ": java.lang.StackOverflowError",
        ))
        self.assertEqual(
            "tlc-019.md",
            classify_quietly(self, triager.CrashKind.TLC, diagnostic),
        )

    def test_worker_exit_without_output_stays_new(self) -> None:
        """corpus64 4c8676fa as recorded, before the worker printed pending output."""
        self.assertEqual(
            triager.NEW_FINDING,
            classify_quietly(
                self, triager.CrashKind.TLC, "TLC worker exited while processing the input"),
        )

    def test_union_of_nonenumerable_sets_is_tlc_003(self) -> None:
        """corpus58 c27fda4c: Int \\cup Int compared inside a set of sets."""
        diagnostic = "\n".join((
            "TLC error code 1000 mapped to exit status 255",
            "Linting of module FuzzInput",
            "Error: TLC threw an unexpected exception.",
            "This was probably caused by an error in the spec or model.",
            "See the User Output or TLC Console for clues to what happened.",
            "The exception was a java.lang.RuntimeException",
            ": Attempted to enumerate S \\cup T when S:",
            "Int",
            "and T:",
            "Int",
            "are not both enumerable",
        ))
        self.assertEqual(
            "tlc-003.md",
            classify_quietly(self, triager.CrashKind.TLC, diagnostic),
        )

    def test_choose_without_witness_in_lazy_initial_value_is_tlc_001(self) -> None:
        """corpus59 a96cc07a: printing the error state re-raises a CHOOSE error."""
        diagnostic = "\n".join((
            "TLC error code 1000 mapped to exit status 255",
            "Linting of module FuzzInput",
            "Starting... (2026-10-12 15:39:01)",
            "Computing initial states...",
            "Error: TLC threw an unexpected exception.",
            "This was probably caused by an error in the spec or model.",
            "See the User Output or TLC Console for clues to what happened.",
            "The exception was a java.lang.RuntimeException",
            ": Attempted to compute the value of an expression of form",
            "CHOOSE x \\in S: P, but no element of S satisfied P.",
            "line 844, col 32 to line 1384, col 37 of module FuzzInput",
        ))
        self.assertEqual(
            "tlc-001.md",
            classify_quietly(self, triager.CrashKind.TLC, diagnostic),
        )


def verdicts(**stages: str | tuple[int, str]) -> dict[triager.Checker, triager.CheckerResult]:
    """Checker results by stage name: a verdict, or a (code, detail) failure."""
    return {
        triager.Checker(stage): (
            triager.CheckerResult("fail", *value)
            if isinstance(value, tuple)
            else triager.CheckerResult(value, None, None)
        )
        for stage, value in stages.items()
    }


class ParallelTlcTest(unittest.TestCase):
    """ADR 0018: TLC-n is TLC with more workers, triaged by TLC's signatures."""

    HEAD = (75, "Attempted to apply Head to the empty sequence.")
    TAIL = (75, "Attempted to apply Tail to the empty sequence.")

    def test_tlcn_crash_uses_tlc_signatures(self) -> None:
        diagnostic = "\n".join(
            (
                "TLC error code 2184 mapped to exit status 255",
                "Error: Attempted to apply Head to the empty sequence.",
            )
        )
        self.assertEqual(
            "tlc-002.md", triager.classify(triager.CrashKind.TLCN, diagnostic, HASH_A))

    def test_counterexample_against_evaluation_error_is_first_error_race(self) -> None:
        """corpus66 37371512, 86ce95dd: either side may report the counterexample."""
        for stages in (
            {"tlc": self.HEAD, "tlcn": "counterexample"},
            {"tlc": "counterexample", "tlcn": self.HEAD},
            {"tlc": self.HEAD, "tlcn": "counterexample", "apalache": "counterexample"},
        ):
            with self.subTest(stages=stages):
                self.assertEqual(
                    triager.FIRST_ERROR_RACE,
                    triager.classify_aggregator(verdicts(**stages), HASH_A))

    def test_other_tlc_disagreements_are_new(self) -> None:
        for stages in (
            {"tlc": "pass", "tlcn": "counterexample"},
            {"tlc": self.HEAD, "tlcn": "pass"},
            {"tlc": (150, "The invariant of Inv is equal to FALSE"), "tlcn": "counterexample"},
            # Apalache's pass is a disagreement that the race does not explain.
            {"tlc": self.HEAD, "tlcn": "counterexample", "apalache": "pass"},
            # TLC and TLC-n fail differently; neither document explains both.
            {"tlc": self.HEAD, "tlcn": self.TAIL, "apalache": "pass"},
        ):
            with self.subTest(stages=stages):
                with contextlib.redirect_stderr(io.StringIO()):
                    actual = triager.classify_aggregator(verdicts(**stages), HASH_A)
                self.assertEqual(triager.NEW_FINDING, actual)

    def test_tlc_and_tlcn_failing_alike_against_apalache_is_classified(self) -> None:
        self.assertEqual(
            "head-of-empty-sequence.md",
            triager.classify_aggregator(
                verdicts(tlc=self.HEAD, tlcn=self.HEAD, apalache="pass"), HASH_A))

    def test_apalache_failure_against_both_tlc_checkers_is_classified(self) -> None:
        stages = verdicts(
            tlc="pass", tlcn="pass",
            apalache=(75, "<unknown>: unsupported expression: STRING"))
        self.assertEqual(
            "string-set-unsupported.md", triager.classify_aggregator(stages, HASH_A))

    def test_reads_an_envelope_without_apalache(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / f"{HASH_A}.cbor"
            path.write_bytes(cbor2.dumps({"stages": {
                "aggregator": {"verdict": "fail"},
                "tlc": {"verdict": "fail", "code": 75, "detail": self.HEAD[1]},
                "tlcn": {"verdict": "counterexample"},
            }}))
            self.assertEqual(
                verdicts(tlc=self.HEAD, tlcn="counterexample"),
                triager.read_aggregator_results(path))

    def test_different_state_counts_take_precedence(self) -> None:
        """ADR 0019: a signature for a third checker must not hide the mismatch."""
        tlc = triager.CheckerResult("pass", None, None, 7)
        tlcn = triager.CheckerResult("pass", None, None, 8)
        string = triager.CheckerResult(
            "fail", 75, "<unknown>: unsupported expression: STRING")
        for results in (
            {triager.Checker.TLC: tlc, triager.Checker.TLCN: tlcn},
            {triager.Checker.TLC: tlc, triager.Checker.TLCN: tlcn,
             triager.Checker.APALACHE: string},
        ):
            with self.subTest(results=results):
                self.assertEqual(
                    triager.STATE_COUNT, triager.classify_aggregator(results, HASH_A))

    def stage(self, verdict: str, phase: str, states: int) -> dict[str, object]:
        return {"verdict": verdict,
                "metrics": {"phase": phase, "distinctStates": states}}

    def read(self, **stages: object) -> dict[triager.Checker, triager.CheckerResult]:
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / f"{HASH_A}.cbor"
            path.write_bytes(cbor2.dumps(
                {"stages": {"aggregator": {"verdict": "fail"}, **stages}}))
            return triager.read_aggregator_results(path)

    def test_reads_the_state_counts_of_completed_searches(self) -> None:
        results = self.read(
            tlc=self.stage("pass", "complete", 7),
            tlcn=self.stage("pass", "complete", 8))
        self.assertEqual([7, 8], [result.reachable_states for result in results.values()])
        self.assertEqual(triager.STATE_COUNT, triager.classify_aggregator(results, HASH_A))

    def test_ignores_the_state_counts_of_searches_that_stopped(self) -> None:
        results = self.read(
            tlc=self.stage("counterexample", "explore", 3),
            tlcn={**self.stage("fail", "explore", 5), "code": 75, "detail": self.HEAD[1]})
        self.assertEqual([None, None], [result.reachable_states for result in results.values()])

    def test_rejects_completed_searches_that_agree(self) -> None:
        with self.assertRaisesRegex(triager.TriageError, "agreeing"):
            self.read(
                tlc=self.stage("pass", "complete", 7),
                tlcn=self.stage("pass", "complete", 7))

    def test_rejects_a_non_integer_state_count(self) -> None:
        with self.assertRaisesRegex(triager.TriageError, "distinctStates"):
            self.read(
                tlc=self.stage("pass", "complete", 7),
                tlcn={"verdict": "pass",
                      "metrics": {"phase": "complete", "distinctStates": "8"}})

    def test_rejects_an_envelope_with_one_checker(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / f"{HASH_A}.cbor"
            path.write_bytes(cbor2.dumps({"stages": {
                "aggregator": {"verdict": "fail"},
                "tlc": {"verdict": "counterexample"},
            }}))
            with self.assertRaisesRegex(triager.TriageError, "fewer than two"):
                triager.read_aggregator_results(path)

    def test_skips_crash_directories_the_corpus_does_not_have(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            corpus = Path(temporary)
            for name in ("01parser-crash", "02tlc-crash", "02apa-crash",
                         triager.AGGREGATOR_DIRECTORY):
                (corpus / name).mkdir()
            reports = triager.triage(corpus, Path(__file__).resolve().parent.parent)
            self.assertEqual(
                ["01parser-crash-triage.csv", "02tlc-crash-triage.csv",
                 "02apa-crash-triage.csv", triager.AGGREGATOR_REPORT],
                [report.name for report in reports])


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
