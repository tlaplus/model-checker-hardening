#!/usr/bin/env python3
"""Classify corpus crashes and aggregator failures by conservative signatures."""

from __future__ import annotations

import argparse
import csv
import os
import re
import sys
import tempfile
from dataclasses import dataclass
from enum import Enum
from pathlib import Path
from typing import Any, Pattern, Sequence

try:
    import cbor2
except ImportError:  # Report a useful error when aggregator triage needs it.
    cbor2 = None
    CBOR_READ_ERRORS = (OSError,)
else:
    CBOR_READ_ERRORS = (OSError, cbor2.CBORDecodeError)


class TriageError(Exception):
    """Raised when crash evidence or the signature catalog is invalid."""


class Checker(Enum):
    TLC = "tlc"
    APALACHE = "apalache"

    @property
    def other(self) -> Checker:
        return Checker.APALACHE if self is Checker.TLC else Checker.TLC


class CrashKind(Enum):
    PARSER = "01parser-crash"
    TLC = "02tlc-crash"
    APALACHE = "02apa-crash"

    @property
    def report_name(self) -> str:
        return f"{self.value}-triage.csv"


@dataclass(frozen=True)
class PatternSet:
    required: tuple[Pattern[str], ...]

    def matches(self, diagnostic: str) -> bool:
        return all(pattern.search(diagnostic) is not None for pattern in self.required)


@dataclass(frozen=True)
class FindingSignature:
    finding_file: str
    crash_kind: CrashKind
    alternatives: tuple[PatternSet, ...]

    def matches(self, diagnostic: str) -> bool:
        return any(alternative.matches(diagnostic) for alternative in self.alternatives)


@dataclass(frozen=True)
class CheckerResult:
    verdict: str
    code: int | None
    detail: str | None


@dataclass(frozen=True)
class AggregatorSignature:
    issue_file: str
    failed_checker: Checker
    other_verdict: str
    code: int
    alternatives: tuple[PatternSet, ...]

    def matches(self, results: dict[Checker, CheckerResult]) -> bool:
        failed = results[self.failed_checker]
        other = results[self.failed_checker.other]
        return (
            failed.verdict == "fail"
            and failed.code == self.code
            and failed.detail is not None
            and other.verdict == self.other_verdict
            and any(
                alternative.matches(failed.detail)
                for alternative in self.alternatives
            )
        )


def all_of(*patterns: str) -> PatternSet:
    return PatternSet(tuple(re.compile(pattern, re.MULTILINE) for pattern in patterns))


def tlc_runtime_error(*patterns: str) -> PatternSet:
    return all_of(
        r"^TLC error code 1000 mapped to exit status 255$",
        r"Error: TLC threw an unexpected exception\.",
        r"The exception was a java\.lang\.RuntimeException",
        *patterns,
    )


# Keep signatures conservative. Add an alternative only after a crash has been
# confirmed to have the same root cause as the finding it names.
SIGNATURES = (
    FindingSignature(
        "sany-001.md",
        CrashKind.PARSER,
        (
            all_of(
                r"java\.util\.UnknownFormatConversionException: Conversion = ':'",
                r"tla2sany\.semantic\.Errors\$ErrorDetails\.getMessage",
            ),
        ),
    ),
    FindingSignature(
        "tlc-001.md",
        CrashKind.TLC,
        (
            tlc_runtime_error(
                r"In applying the function",
                r"which is not in its domain\.",
            ),
        ),
    ),
    FindingSignature(
        "tlc-002.md",
        CrashKind.TLC,
        (
            all_of(
                r"^TLC error code 2184 mapped to exit status 255$",
                r"Error: Attempted to apply (?:Head|Tail) to the empty sequence\.",
            ),
            all_of(
                r"^TLC error code 2183 mapped to exit status 255$",
                r"Error: The second argument of SubSeq must be in the domain of its first argument:",
            ),
            all_of(
                r"^TLC error code 2180 mapped to exit status 255$",
                r"Error: 0\^0 is undefined\.",
            ),
            all_of(
                r"^TLC error code 2179 mapped to exit status 255$",
                r"Error: The second argument of \\div is 0\.",
            ),
            all_of(
                r"^TLC error code 2169 mapped to exit status 255$",
                r"Error: The second argument of % should be a positive number",
            ),
        ),
    ),
    FindingSignature(
        "tlc-003.md",
        CrashKind.TLC,
        (
            tlc_runtime_error(r"Attempted to compare the set .+ with the value:"),
            tlc_runtime_error(
                r"Attempted to compare overridden value .+ with non-overridden value:"
            ),
            tlc_runtime_error(
                r"Attempted to compute the number of elements in the overridden value"
            ),
            tlc_runtime_error(r"Attempted to enumerate S \\ T when S:"),
            tlc_runtime_error(r"Attempted to enumerate S \\cap T when neither S:"),
            tlc_runtime_error(
                r"Attempted to enumerate UNION\(s\), but some element of s is nonenumerable\."
            ),
            tlc_runtime_error(r"Attempted to enumerate \{ x \\in S : p\(x\) \} when S:"),
        ),
    ),
    FindingSignature(
        "apalache-printer-008.md",
        CrashKind.TLC,
        (
            all_of(
                r"^TLC error code 2102 mapped to exit status 255$",
                r"Error: current state is not a legal state",
                r"/\\ step = null",
            ),
        ),
    ),
    FindingSignature(
        "apalache-bmc-001.md",
        CrashKind.APALACHE,
        (
            all_of(
                r"scala\.NotImplementedError: A set filter over .+ is not implemented",
                r"at\.forsyte\.apalache\.tla\.bmcmt\.rules\.SetFilterRule\.apply",
            ),
        ),
    ),
    FindingSignature(
        "apalache-bmc-003.md",
        CrashKind.APALACHE,
        (
            all_of(
                r"^Apalache exited with status 255$",
                r"checker error: Unexpected equality test over types",
            ),
        ),
    ),
    FindingSignature(
        "apalache-bmc-004.md",
        CrashKind.APALACHE,
        (
            all_of(
                r"java\.lang\.ClassCastException: class com\.microsoft\.z3\.RealExpr cannot be cast to class com\.microsoft\.z3\.IntExpr",
                r"at\.forsyte\.apalache\.tla\.bmcmt\.smt\.Z3SolverContext\.toArithExpr",
            ),
        ),
    ),
    FindingSignature(
        "apalache-bmc-005.md",
        CrashKind.APALACHE,
        (
            all_of(
                r"java\.lang\.UnsupportedOperationException: Expansion of InfSet\[CellTFrom\(Int\)\] is not supported yet",
                r"at\.forsyte\.apalache\.tla\.bmcmt\.rules\.QuantRule\.expandExistsOrForall",
            ),
        ),
    ),
    FindingSignature(
        "apalache-bmc-006.md",
        CrashKind.APALACHE,
        (
            all_of(
                r"^Apalache exited with status 255$",
                r"rewriter error: Do not know how pick an element from a set of type:",
            ),
        ),
    ),
    FindingSignature(
        "apalache-bmc-009.md",
        CrashKind.APALACHE,
        (
            all_of(
                r"java\.lang\.IllegalArgumentException: requirement failed: The right-hand side of a function set should be: a finite set or a powerset",
                r"at\.forsyte\.apalache\.tla\.bmcmt\.rules\.FunSetCtorRule\.apply",
            ),
        ),
    ),
    FindingSignature(
        "apalache-bmc-010.md",
        CrashKind.APALACHE,
        (
            all_of(
                r"internal error in type checking: Applying UNION to CHOOSE",
                r"of type PowSet\[Set\(",
            ),
        ),
    ),
    FindingSignature(
        "apalache-bmc-011.md",
        CrashKind.APALACHE,
        (
            all_of(
                r"java\.lang\.AssertionError: assertion failed",
                r"at\.forsyte\.apalache\.tla\.bmcmt\.LazyEquality\.subsetEq",
                r"at\.forsyte\.apalache\.tla\.bmcmt\.LazyEquality\.mkFunSetEq",
            ),
        ),
    ),
    FindingSignature(
        # Skolemizable \E over a set expression that evaluates to Int/Nat.
        # Distinct from apalache-bmc-005, which is the expansion path with the
        # "Expansion of InfSet[...]" message from QuantRule.expandExistsOrForall.
        "apalache-bmc-012.md",
        CrashKind.APALACHE,
        (
            all_of(
                r"java\.lang\.UnsupportedOperationException: Quantification over InfSet\[CellTFrom\(Int\)\] is not supported yet",
                r"at\.forsyte\.apalache\.tla\.bmcmt\.rules\.QuantRule\.apply",
            ),
        ),
    ),
    FindingSignature(
        "apalache-bmc-013.md",
        CrashKind.APALACHE,
        (
            all_of(
                r"java\.util\.NoSuchElementException: key not found: \$C\$\d+",
                r"at\.forsyte\.apalache\.tla\.bmcmt\.Binding\.apply",
                r"at\.forsyte\.apalache\.tla\.bmcmt\.rules\.SetInRule\.apply",
                r"at\.forsyte\.apalache\.tla\.bmcmt\.rules\.FoldSetRule\.",
            ),
        ),
    ),
    FindingSignature(
        "apalache-cli-001.md",
        CrashKind.APALACHE,
        (
            all_of(
                r"^Apalache exited with status 255$",
                r"Input error \(see the manual\): Cardinality expected a finite set, found: (?:InfSet|FinFunSet|PowSet)\[",
            ),
            all_of(
                r"^Apalache exited with status 255$",
                r"Input error \(see the manual\): Expected a constant integer range in \[ \.\. \]",
            ),
            all_of(
                r"^Apalache exited with status 255$",
                r"Input error \(see the manual\): Found a set map over an infinite set",
            ),
            all_of(
                r"^Apalache exited with status 255$",
                r"known limitation: FoldSet is not supported over an infinite set",
            ),
            all_of(
                r"^Apalache exited with status 255$",
                r"Input error \(see the manual\): Negative power at ",
            ),
            all_of(
                r"^Apalache exited with status 255$",
                r"Input error \(see the manual\): (?:The power at|The result of) .+ exceedes the limit",
            ),
            all_of(
                r"^Apalache exited with status 255$",
                r"rewriter error: Accessing a non-existing variant option via tag ",
            ),
            all_of(
                r"^Apalache exited with status 255$",
                r"rewriter error: Range bounds are too large to fit in scala\.Int",
            ),
            all_of(
                r"^Apalache exited with status 255$",
                r"error when rewriting to SMT: SMT \d+: z3 reports UNKNOWN",
            ),
            all_of(
                r"^Apalache exited with status 255$",
                r"rewriter error: Trying to expand a set of functions\. This will blow up the solver\.",
            ),
        ),
    ),
    FindingSignature(
        "apalache-optimizer-001.md",
        CrashKind.APALACHE,
        (
            all_of(
                r"unexpected expression: undeclared operator LocalOp\d+\$\d+ \[FlatLanguagePred\]",
                r"ConstSimplifier",
                r"ExprOptimizer",
            ),
        ),
    ),
    FindingSignature(
        "apalache-json-002.md",
        CrashKind.APALACHE,
        (
            all_of(
                r"java\.lang\.OutOfMemoryError: Java heap space",
                r"DefaultType1Parser",
            ),
        ),
    ),
)

# These signatures intentionally use only diagnostics that uniquely identify a
# documented class. Generic infinite-set and malformed-operand messages remain NEW.
AGGREGATOR_SIGNATURES = (
    AggregatorSignature(
        "function-application-outside-domain.md",
        Checker.TLC,
        "pass",
        75,
        (
            all_of(r"^In applying the function$"),
            all_of(r"^Attempted to apply function:$"),
        ),
    ),
    AggregatorSignature(
        "choose-without-witness.md",
        Checker.TLC,
        "pass",
        75,
        (all_of(r"^Attempted to compute the value of an expression of(?: form)?$"),),
    ),
    AggregatorSignature(
        "head-of-empty-sequence.md",
        Checker.TLC,
        "pass",
        75,
        (all_of(r"^Attempted to apply Head to the empty sequence\.$"),),
    ),
    AggregatorSignature(
        "case-without-matching-arm.md",
        Checker.TLC,
        "pass",
        75,
        (all_of(r"^Attempted to evaluate a CASE with no conditions true\.$"),),
    ),
    AggregatorSignature(
        "subseq-outside-domain.md",
        Checker.TLC,
        "pass",
        75,
        (all_of(r"^The second argument of SubSeq must be in the domain of its first argument:$"),),
    ),
    AggregatorSignature(
        "tail-of-empty-sequence.md",
        Checker.TLC,
        "pass",
        75,
        (all_of(r"^Attempted to apply Tail to the empty sequence\.$"),),
    ),
    AggregatorSignature(
        "zero-power-zero-tlc-fails.md",
        Checker.TLC,
        "pass",
        75,
        (all_of(r"^0\^0 is undefined\.$"),),
    ),
    AggregatorSignature(
        "integer-outside-tlc-range.md",
        Checker.TLC,
        "pass",
        75,
        (
            all_of(r"^TLC can't handle a number this big\.$"),
            all_of(r"^Overflow when computing -?\d+\^\d+$"),
        ),
    ),
    AggregatorSignature(
        "modulo-nonpositive-divisor.md",
        Checker.TLC,
        "pass",
        75,
        (all_of(r"^The second argument of % should be a positive number"),),
    ),
    AggregatorSignature(
        "division-by-zero-tlc-fails.md",
        Checker.TLC,
        "pass",
        75,
        (all_of(r"^The second argument of \\div is 0\.$"),),
    ),
    AggregatorSignature(
        "infinite-set-as-state-value.md",
        Checker.TLC,
        "pass",
        75,
        (all_of(r"^TLC has found a state in which the value of a variable contains (?:Int|Nat)$"),),
    ),
    AggregatorSignature(
        "filter-over-infinite-set.md",
        Checker.TLC,
        "pass",
        75,
        (all_of(r"^Attempted to enumerate \{ x \\in S : p\(x\) \} when S:$"),),
    ),
    AggregatorSignature(
        "union-containing-infinite-set.md",
        Checker.TLC,
        "pass",
        75,
        (
            all_of(
                r"^Attempted to enumerate UNION\(s\), but some element of s is nonenumerable\.$"
            ),
            all_of(r"^Attempted to enumerate S \\cup T when S:$"),
        ),
    ),
    AggregatorSignature(
        "function-over-infinite-domain.md",
        Checker.TLC,
        "pass",
        75,
        (
            all_of(
                r"^Attempted to enumerate a set of the form \[D -> R\],but the domain D:$"
            ),
            all_of(
                r"^Attempted to compute the number of elements in the overridden value (?:Int|Nat|Seq\(.+\))\.$"
            ),
        ),
    ),
    AggregatorSignature(
        "quantification-over-infinite-set.md",
        Checker.TLC,
        "pass",
        75,
        (
            all_of(r"^TLC encountered (?:the |a )non-enumerable quantifier bound$"),
        ),
    ),
    AggregatorSignature(
        "finite-set-containing-infinite-set.md",
        Checker.TLC,
        "pass",
        75,
        (
            all_of(r"^Attempted to compare (?:the set|overridden value) .+ with (?:the value|non-overridden value):$"),
            all_of(r"^Attempted to check equality of the set .+ with the value:$"),
        ),
    ),
    AggregatorSignature(
        "cardinality-of-infinite-set.md",
        Checker.TLC,
        "pass",
        75,
        (all_of(r"^Attempted to compute cardinality of the value$"),),
    ),
    AggregatorSignature(
        "difference-with-infinite-set.md",
        Checker.TLC,
        "pass",
        75,
        (all_of(r"^Attempted to enumerate S \\ T when S:$"),),
    ),
    AggregatorSignature(
        "intersection-of-infinite-sets.md",
        Checker.TLC,
        "pass",
        75,
        (all_of(r"^Attempted to enumerate S \\cap T when neither S:$"),),
    ),
    AggregatorSignature(
        "subset-test-over-infinite-set.md",
        Checker.TLC,
        "pass",
        75,
        (all_of(r"^Attempted to evaluate an expression of form S \\subseteq T, but S was not enumer"),),
    ),
    AggregatorSignature(
        "cartesian-product-with-infinite-set.md",
        Checker.TLC,
        "pass",
        75,
        (all_of(r"^Attempted to enumerate a set of the form s1 \\X s2 \.\.\. \\X sn,$"),),
    ),
    AggregatorSignature(
        "function-set-over-infinite-set.md",
        Checker.TLC,
        "pass",
        75,
        (all_of(r"^Attempted to enumerate a set of the form \[D -> R\],but the range R:$"),),
    ),
    AggregatorSignature(
        "negative-exponent-tlc-fails.md",
        Checker.TLC,
        "pass",
        75,
        (all_of(r"^The second argument of \^ should be a natural number"),),
    ),
    AggregatorSignature(
        "apalache-printer-008.md",
        Checker.TLC,
        "pass",
        75,
        (
            all_of(r"^Attempted to evaluate an expression of form P (?:=>|<=>|/\\|\\/) Q when P"),
            all_of(r"^A non-boolean expression "),
            all_of(r"^Attempted to apply the operator ~ to a non-boolean$"),
            all_of(r"^Evaluating an expression of the form t \\o s when s is not a sequence:$"),
            all_of(r"^Attempted to check if the value:$"),
        ),
    ),
    AggregatorSignature(
        "constant-false-invariant.md",
        Checker.TLC,
        "pass",
        150,
        (all_of(r"^The invariant of Inv is equal to FALSE$"),),
    ),
    AggregatorSignature(
        "non-enumerable-initial-assignment.md",
        Checker.TLC,
        "pass",
        75,
        (all_of(r"^In computing initial states, the right side of \\IN is not enumerable\.$"),),
    ),
    AggregatorSignature(
        "modulo-by-zero-apalache-fails.md",
        Checker.APALACHE,
        "pass",
        75,
        (all_of(r"^Input error \(see the manual\): Mod by zero at "),),
    ),
    AggregatorSignature(
        "division-by-zero-apalache-fails.md",
        Checker.APALACHE,
        "pass",
        75,
        (all_of(r"^Input error \(see the manual\): Division by zero at "),),
    ),
    AggregatorSignature(
        "zero-power-zero-apalache-fails.md",
        Checker.APALACHE,
        "pass",
        75,
        (all_of(r"^Input error \(see the manual\): 0 \^ 0 is undefined$"),),
    ),
    AggregatorSignature(
        "sequence-set-unsupported.md",
        Checker.APALACHE,
        "pass",
        75,
        (all_of(r"^<unknown>: unsupported expression: Seq\(_\) produces an infinite set"),),
    ),
    AggregatorSignature(
        "string-set-unsupported.md",
        Checker.APALACHE,
        "pass",
        75,
        (all_of(r"^<unknown>: unsupported expression: STRING$"),),
    ),
)

ENTRY_NAME = re.compile(r"(?P<hash>[0-9a-f]{64})\.cbor\Z")
STACKTRACE_NAME = re.compile(r"(?P<hash>[0-9a-f]{64})\.stacktrace\Z")
CSV_HEADER = ("entry_hash", "issue")
NEW_FINDING = "NEW"

# A worker timeout is the harness killing a slow run; the diagnostic carries no
# tool output to classify against a finding. Bucket it separately so that NEW
# keeps meaning "an unclassified crash that needs investigation". The message is
# "<Apalache|TLC> worker timed out after <Duration>" (IsolatedWorkerProcess).
WORKER_TIMEOUT = "TIMEOUT"
WORKER_TIMEOUT_PATTERN = re.compile(r"^\w+ worker timed out after ", re.MULTILINE)
AGGREGATOR_DIRECTORY = "03aggregator-fail"
AGGREGATOR_REPORT = "03aggregator-fail-triage.csv"
VALID_VERDICTS = frozenset(("pass", "counterexample", "fail", "crashed"))


def validate_catalog_references(
    directories: Sequence[Path], referenced_files: set[str], description: str
) -> None:
    files_by_name: dict[str, list[Path]] = {}
    for directory in directories:
        if not directory.is_dir():
            raise TriageError(f"{description} directory does not exist: {directory}")
        for document in directory.rglob("*.md"):
            if document.name == "README.md":
                continue
            files_by_name.setdefault(document.name, []).append(document)

    for referenced_file in sorted(referenced_files):
        matches = files_by_name.get(referenced_file, [])
        if not matches:
            raise TriageError(
                f"signature references missing {description}: {referenced_file}"
            )
        if len(matches) > 1:
            locations = ", ".join(str(path) for path in sorted(matches))
            raise TriageError(
                f"signature references ambiguous {description} "
                f"{referenced_file}: {locations}"
            )


def validate_signature_catalog(repository_root: Path) -> None:
    findings = repository_root / "findings"
    conformance = repository_root / "conformance"
    validate_catalog_references(
        (findings,),
        {signature.finding_file for signature in SIGNATURES},
        "finding",
    )
    validate_catalog_references(
        (findings, conformance),
        {signature.issue_file for signature in AGGREGATOR_SIGNATURES},
        "triage document",
    )


def classify(crash_kind: CrashKind, diagnostic: str, entry_hash: str) -> str:
    if WORKER_TIMEOUT_PATTERN.search(diagnostic):
        return WORKER_TIMEOUT
    matches = {
        signature.finding_file
        for signature in SIGNATURES
        if signature.crash_kind is crash_kind and signature.matches(diagnostic)
    }
    if not matches:
        return NEW_FINDING
    if len(matches) > 1:
        findings = ", ".join(sorted(matches))
        raise TriageError(
            f"{crash_kind.value}/{entry_hash} matches multiple findings: {findings}"
        )
    return next(iter(matches))


def classify_aggregator(
    results: dict[Checker, CheckerResult], entry_hash: str
) -> str:
    matches = {
        signature.issue_file
        for signature in AGGREGATOR_SIGNATURES
        if signature.matches(results)
    }
    if not matches:
        return NEW_FINDING
    if len(matches) > 1:
        issues = ", ".join(sorted(matches))
        raise TriageError(
            f"{AGGREGATOR_DIRECTORY}/{entry_hash} matches multiple issues: {issues}"
        )
    return next(iter(matches))


def require_regular_file(path: Path, description: str) -> None:
    if path.is_symlink() or not path.is_file():
        raise TriageError(f"{description} is not a regular file: {path}")


def read_diagnostic(path: Path) -> str:
    require_regular_file(path, "crash diagnostic")
    try:
        return path.read_text(encoding="utf-8")
    except UnicodeDecodeError as error:
        raise TriageError(f"crash diagnostic is not valid UTF-8: {path}") from error
    except OSError as error:
        raise TriageError(f"cannot read crash diagnostic {path}: {error}") from error


def decode_cbor(path: Path) -> Any:
    require_regular_file(path, "aggregator entry")
    if cbor2 is None:
        raise TriageError(
            "aggregator triage requires cbor2; install script/requirements.txt"
        )
    try:
        with path.open("rb") as source:
            decoder = cbor2.CBORDecoder(source)
            document = decoder.decode()
            if source.read(1):
                raise TriageError(f"aggregator entry has trailing CBOR data: {path}")
            return document
    except TriageError:
        raise
    except CBOR_READ_ERRORS as error:
        raise TriageError(f"cannot decode aggregator entry {path}: {error}") from error


def require_map(value: Any, field: str, path: Path) -> dict[Any, Any]:
    if not isinstance(value, dict):
        raise TriageError(f"aggregator entry field '{field}' must be a map: {path}")
    return value


def read_checker_result(
    stages: dict[Any, Any], checker: Checker, path: Path
) -> CheckerResult:
    metadata = require_map(
        stages.get(checker.value), f"stages.{checker.value}", path
    )
    verdict = metadata.get("verdict")
    if verdict not in VALID_VERDICTS:
        raise TriageError(
            f"aggregator entry has invalid stages.{checker.value}.verdict: {path}"
        )
    code = metadata.get("code")
    if code is not None and (isinstance(code, bool) or not isinstance(code, int)):
        raise TriageError(
            f"aggregator entry field 'stages.{checker.value}.code' must be an integer: {path}"
        )
    detail = metadata.get("detail")
    if detail is not None and not isinstance(detail, str):
        raise TriageError(
            f"aggregator entry field 'stages.{checker.value}.detail' must be text: {path}"
        )
    if verdict == "fail" and code is None:
        raise TriageError(
            f"aggregator entry failed stage '{checker.value}' has no code: {path}"
        )
    if verdict != "fail" and (code is not None or detail is not None):
        raise TriageError(
            f"aggregator entry non-failed stage '{checker.value}' has failure metadata: {path}"
        )
    return CheckerResult(verdict, code, detail)


def read_aggregator_results(path: Path) -> dict[Checker, CheckerResult]:
    document = require_map(decode_cbor(path), "document", path)
    stages = require_map(document.get("stages"), "stages", path)
    aggregator = require_map(stages.get("aggregator"), "stages.aggregator", path)
    if aggregator.get("verdict") != "fail":
        raise TriageError(f"entry in aggregator-fail does not have fail verdict: {path}")
    results = {
        checker: read_checker_result(stages, checker, path) for checker in Checker
    }
    if results[Checker.TLC].verdict == results[Checker.APALACHE].verdict:
        raise TriageError(f"aggregator entry contains agreeing checker verdicts: {path}")
    if "crashed" in (result.verdict for result in results.values()):
        raise TriageError(f"aggregator entry contains a crashed checker result: {path}")
    return results


def classify_aggregator_directory(corpus: Path) -> list[tuple[str, str]]:
    directory = corpus / AGGREGATOR_DIRECTORY
    if directory.is_symlink() or not directory.is_dir():
        raise TriageError(f"aggregator directory does not exist: {directory}")
    try:
        children = list(directory.iterdir())
    except OSError as error:
        raise TriageError(f"cannot list aggregator directory {directory}: {error}") from error

    rows = []
    for path in children:
        entry_match = ENTRY_NAME.fullmatch(path.name)
        if entry_match is None:
            raise TriageError(f"unexpected file in aggregator directory: {path}")
        require_regular_file(path, "aggregator entry")
        entry_hash = entry_match.group("hash")
        rows.append(
            (entry_hash, classify_aggregator(read_aggregator_results(path), entry_hash))
        )
    return sorted(rows)


def classify_directory(corpus: Path, crash_kind: CrashKind) -> list[tuple[str, str]]:
    directory = corpus / crash_kind.value
    if directory.is_symlink() or not directory.is_dir():
        raise TriageError(f"crash directory does not exist: {directory}")

    entries: dict[str, Path] = {}
    stacktraces: dict[str, Path] = {}
    try:
        children = list(directory.iterdir())
    except OSError as error:
        raise TriageError(f"cannot list crash directory {directory}: {error}") from error

    for path in children:
        entry_match = ENTRY_NAME.fullmatch(path.name)
        if entry_match:
            require_regular_file(path, "corpus entry")
            entries[entry_match.group("hash")] = path
            continue

        stacktrace_match = STACKTRACE_NAME.fullmatch(path.name)
        if stacktrace_match:
            require_regular_file(path, "crash diagnostic")
            stacktraces[stacktrace_match.group("hash")] = path
            continue

        raise TriageError(f"unexpected file in crash directory: {path}")

    missing_stacktraces = sorted(entries.keys() - stacktraces.keys())
    if missing_stacktraces:
        entry_hash = missing_stacktraces[0]
        raise TriageError(
            f"crash entry has no matching diagnostic: {directory / (entry_hash + '.cbor')}"
        )

    orphan_stacktraces = sorted(stacktraces.keys() - entries.keys())
    if orphan_stacktraces:
        entry_hash = orphan_stacktraces[0]
        raise TriageError(
            "crash diagnostic has no matching entry: "
            f"{directory / (entry_hash + '.stacktrace')}"
        )

    return [
        (
            entry_hash,
            classify(
                crash_kind,
                read_diagnostic(stacktraces[entry_hash]),
                entry_hash,
            ),
        )
        for entry_hash in sorted(entries)
    ]


def write_report(path: Path, rows: Sequence[tuple[str, str]]) -> None:
    temporary_path: Path | None = None
    try:
        with tempfile.NamedTemporaryFile(
            mode="w",
            encoding="utf-8",
            newline="",
            prefix=f".{path.name}.",
            suffix=".tmp",
            dir=path.parent,
            delete=False,
        ) as output:
            temporary_path = Path(output.name)
            writer = csv.writer(output, lineterminator="\n")
            writer.writerow(CSV_HEADER)
            writer.writerows(rows)
            output.flush()
            os.fsync(output.fileno())
        os.replace(temporary_path, path)
        temporary_path = None
    except OSError as error:
        raise TriageError(f"cannot write triage report {path}: {error}") from error
    finally:
        if temporary_path is not None:
            try:
                temporary_path.unlink(missing_ok=True)
            except OSError:
                pass


def triage(corpus: Path, repository_root: Path) -> list[Path]:
    if corpus.is_symlink() or not corpus.is_dir():
        raise TriageError(f"corpus directory does not exist: {corpus}")

    validate_signature_catalog(repository_root)
    reports = {
        crash_kind: classify_directory(corpus, crash_kind) for crash_kind in CrashKind
    }
    aggregator_rows = classify_aggregator_directory(corpus)

    report_paths = []
    for crash_kind in CrashKind:
        report_path = corpus / crash_kind.report_name
        write_report(report_path, reports[crash_kind])
        report_paths.append(report_path)
    aggregator_report = corpus / AGGREGATOR_REPORT
    write_report(aggregator_report, aggregator_rows)
    report_paths.append(aggregator_report)
    return report_paths


def parse_arguments(arguments: Sequence[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Classify corpus crashes and conformance aggregator failures."
    )
    parser.add_argument("corpus", type=Path, help="initialized FuzzTLA corpus directory")
    return parser.parse_args(arguments)


def main(arguments: Sequence[str] | None = None) -> int:
    options = parse_arguments(arguments)
    repository_root = Path(__file__).resolve().parent.parent
    try:
        reports = triage(options.corpus.resolve(), repository_root)
    except TriageError as error:
        print(f"triager: {error}", file=sys.stderr)
        return 1

    for report in reports:
        print(report)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
