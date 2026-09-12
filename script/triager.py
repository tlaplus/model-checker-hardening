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


# The other checker's completed verdicts. A documented conformance class is
# fixed by the failing checker's diagnostic, not by whether the other checker's
# completed run reported no violation ("pass") or a counterexample: both are
# completions, and the aggregator records the fail/pass and fail/counterexample
# pairs alike as a deviation. "fail" and "crashed" on the other side are a
# separate, unclassified event (and "crashed" never reaches here).
OTHER_CHECKER_COMPLETED = frozenset(("pass", "counterexample"))


@dataclass(frozen=True)
class AggregatorSignature:
    issue_file: str
    failed_checker: Checker
    code: int
    alternatives: tuple[PatternSet, ...]

    def matches(self, results: dict[Checker, CheckerResult]) -> bool:
        failed = results[self.failed_checker]
        other = results[self.failed_checker.other]
        return (
            failed.verdict == "fail"
            and failed.code == self.code
            and failed.detail is not None
            and other.verdict in OTHER_CHECKER_COMPLETED
            and any(
                alternative.matches(failed.detail)
                for alternative in self.alternatives
            )
        )


def all_of(*patterns: str) -> PatternSet:
    return PatternSet(tuple(re.compile(pattern, re.MULTILINE) for pattern in patterns))


def finding(file: str, kind: CrashKind, *alternatives: PatternSet) -> FindingSignature:
    return FindingSignature(file, kind, alternatives)


EVALUATION_FAILURE_CODE = 75


def failure(
    file: str, checker: Checker, *patterns: str, code: int = EVALUATION_FAILURE_CODE
) -> AggregatorSignature:
    return AggregatorSignature(file, checker, code, tuple(all_of(pattern) for pattern in patterns))


def apalache_error(pattern: str) -> PatternSet:
    return all_of(r"^Apalache exited with status 255$", pattern)


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
    finding("sany-001.md", CrashKind.PARSER,
            all_of(r"java\.util\.UnknownFormatConversionException: Conversion = ':'",
                   r"tla2sany\.semantic\.Errors\$ErrorDetails\.getMessage")),
    finding("tlc-001.md", CrashKind.TLC,
            tlc_runtime_error(r"In applying the function", r"which is not in its domain\.")),
    finding("tlc-002.md", CrashKind.TLC,
            all_of(r"^TLC error code 2184 mapped to exit status 255$",
                   r"Error: Attempted to apply (?:Head|Tail) to the empty sequence\."),
            all_of(r"^TLC error code 2183 mapped to exit status 255$",
                   r"Error: The second argument of SubSeq must be in the domain of its first argument:"),
            all_of(r"^TLC error code 2180 mapped to exit status 255$", r"Error: 0\^0 is undefined\."),
            all_of(r"^TLC error code 2179 mapped to exit status 255$", r"Error: The second argument of \\div is 0\."),
            all_of(r"^TLC error code 2169 mapped to exit status 255$",
                   r"Error: The second argument of % should be a positive number"),
            all_of(r"^TLC error code 2169 mapped to exit status 255$",
                   r"Error: The second argument of \^ should be a natural number"),
            all_of(r"^TLC error code 2178 mapped to exit status 255$",
                   r"Overflow when computing ")),
    finding("tlc-003.md", CrashKind.TLC,
            tlc_runtime_error(r"Attempted to compare the set .+ with the value:"),
            tlc_runtime_error(r"Attempted to compare overridden value .+ with non-overridden value:"),
            tlc_runtime_error(r"Attempted to compute the number of elements in the overridden value"),
            tlc_runtime_error(r"Attempted to enumerate S \\ T when S:"),
            tlc_runtime_error(r"Attempted to enumerate S \\cap T when neither S:"),
            tlc_runtime_error(r"Attempted to enumerate UNION\(s\), but some element of s is nonenumerable\."),
            tlc_runtime_error(r"Attempted to enumerate \{ x \\in S : p\(x\) \} when S:")),
    finding("apalache-printer-008.md", CrashKind.TLC,
            all_of(r"^TLC error code 2102 mapped to exit status 255$",
                   r"Error: current state is not a legal state", r"/\\ step = null")),
    finding("apalache-bmc-001.md", CrashKind.APALACHE,
            all_of(r"scala\.NotImplementedError: A set filter over .+ is not implemented",
                   r"at\.forsyte\.apalache\.tla\.bmcmt\.rules\.SetFilterRule\.apply")),
    # The rule frame is what separates this from apalache-bmc-001: both report
    # NotImplementedError over the same symbolic-set representations, but from
    # SetMapRule and SetFilterRule respectively, and they are separate fixes.
    finding("apalache-bmc-014.md", CrashKind.APALACHE,
            all_of(r"scala\.NotImplementedError: A set map over .+ is not implemented",
                   r"at\.forsyte\.apalache\.tla\.bmcmt\.rules\.SetMapRule\.apply")),
    finding("apalache-bmc-003.md", CrashKind.APALACHE,
            apalache_error(r"checker error: Unexpected equality test over types")),
    finding("apalache-bmc-004.md", CrashKind.APALACHE,
            all_of(r"java\.lang\.ClassCastException: class com\.microsoft\.z3\.RealExpr cannot be cast to class com\.microsoft\.z3\.IntExpr",
                   r"at\.forsyte\.apalache\.tla\.bmcmt\.smt\.Z3SolverContext\.toArithExpr")),
    finding("apalache-bmc-005.md", CrashKind.APALACHE,
            all_of(r"java\.lang\.UnsupportedOperationException: Expansion of InfSet\[CellTFrom\(Int\)\] is not supported yet",
                   r"at\.forsyte\.apalache\.tla\.bmcmt\.rules\.QuantRule\.expandExistsOrForall")),
    finding("apalache-bmc-006.md", CrashKind.APALACHE,
            apalache_error(r"rewriter error: Do not know how pick an element from a set of type:")),
    finding("apalache-bmc-009.md", CrashKind.APALACHE,
            all_of(r"java\.lang\.IllegalArgumentException: requirement failed: The right-hand side of a function set should be: a finite set or a powerset",
                   r"at\.forsyte\.apalache\.tla\.bmcmt\.rules\.FunSetCtorRule\.apply")),
    finding("apalache-bmc-010.md", CrashKind.APALACHE,
            all_of(r"internal error in type checking: Applying UNION to CHOOSE", r"of type PowSet\[Set\(")),
    finding("apalache-bmc-011.md", CrashKind.APALACHE,
            all_of(r"java\.lang\.AssertionError: assertion failed",
                   r"at\.forsyte\.apalache\.tla\.bmcmt\.LazyEquality\.subsetEq",
                   r"at\.forsyte\.apalache\.tla\.bmcmt\.LazyEquality\.mkFunSetEq")),
    # Skolemizable \E over a set expression that evaluates to Int/Nat.
    # Distinct from apalache-bmc-005, which is the expansion path with the
    # "Expansion of InfSet[...]" message from QuantRule.expandExistsOrForAll.
    finding("apalache-bmc-012.md", CrashKind.APALACHE,
            all_of(r"java\.lang\.UnsupportedOperationException: Quantification over InfSet\[CellTFrom\(Int\)\] is not supported yet",
                   r"at\.forsyte\.apalache\.tla\.bmcmt\.rules\.QuantRule\.apply")),
    finding("apalache-bmc-013.md", CrashKind.APALACHE,
            all_of(r"java\.util\.NoSuchElementException: key not found: \$C\$\d+",
                   r"at\.forsyte\.apalache\.tla\.bmcmt\.Binding\.apply",
                   r"at\.forsyte\.apalache\.tla\.bmcmt\.rules\.SetInRule\.apply",
                   r"at\.forsyte\.apalache\.tla\.bmcmt\.rules\.Fold(?:Set|Seq)Rule\.")),
    finding("apalache-cli-001.md", CrashKind.APALACHE,
            apalache_error(r"Input error \(see the manual\): Cardinality expected a finite set, found: (?:InfSet|FinFunSet|PowSet)\["),
            apalache_error(r"Input error \(see the manual\): Expected a constant integer range in \[ \.\. \]"),
            apalache_error(r"Input error \(see the manual\): Found a set map over an infinite set"),
            apalache_error(r"known limitation: FoldSet is not supported over an infinite set"),
            apalache_error(r"Input error \(see the manual\): Negative power at "),
            apalache_error(r"Input error \(see the manual\): (?:The power at|The result of) .+ exceedes the limit"),
            apalache_error(r"rewriter error: Accessing a non-existing variant option via tag "),
            apalache_error(r"rewriter error: Range bounds are too large to fit in scala\.Int"),
            apalache_error(r"error when rewriting to SMT: SMT \d+: z3 reports UNKNOWN"),
            apalache_error(r"rewriter error: Trying to expand a set of functions\. This will blow up the solver\.")),
    finding("apalache-optimizer-001.md", CrashKind.APALACHE,
            all_of(r"unexpected expression: undeclared operator LocalOp\d+\$\d+ \[FlatLanguagePred\]",
                   r"ConstSimplifier", r"ExprOptimizer")),
    finding("apalache-json-002.md", CrashKind.APALACHE,
            all_of(r"java\.lang\.OutOfMemoryError: Java heap space", r"DefaultType1Parser")),
)

# These signatures intentionally use only diagnostics that uniquely identify a
# documented class. Generic infinite-set and malformed-operand messages remain NEW.
#
# An aggregator entry stores one line, not a transcript: the checker keeps the
# first "Error:" line and truncates it to CheckerFailure.MAXIMUM_DETAIL_CHARACTERS
# (80) with an ellipsis. An alternative here must therefore match a single
# truncated line -- a multi-pattern all_of() can never match -- and must be
# anchored at the start so a cut tail cannot defeat it. TLC frequently reports a
# wrapper first ("Evaluating invariant Inv failed.", "TLC threw an unexpected
# exception.", "Attempted to apply the operator overridden by the Java method"),
# so the root cause is not in the stored line at all and those groups stay NEW.
AGGREGATOR_SIGNATURES = (
    failure("function-application-outside-domain.md", Checker.TLC,
            r"^In applying the function$", r"^Attempted to apply function:$"),
    # Four TLC messages open with this prefix, and TLC line-wraps them at
    # different points, so the stored first line separates only one of them:
    # "...of an expression of" is always "form CHOOSE x \in S: P, but S was not
    # enumerable", a different conformance class. The "...of form" spelling is
    # shared by the no-witness CHOOSE, the N-tuples CHOOSE, and SUBSET over a
    # non-enumerable set; 40 sampled corpus14 entries were all the first, so it
    # stays here, but a reruns check is the only way to split it further.
    failure("choose-without-witness.md", Checker.TLC,
            r"^Attempted to compute the value of an expression of form$"),
    failure("choose-over-infinite-set.md", Checker.TLC,
            r"^Attempted to compute the value of an expression of$"),
    failure("head-of-empty-sequence.md", Checker.TLC,
            r"^Attempted to apply Head to the empty sequence\.$"),
    failure("case-without-matching-arm.md", Checker.TLC,
            r"^Attempted to evaluate a CASE with no conditions true\.$",
            r"^In computing next states, TLC encountered a CASE with no conditions true\.$"),
    failure("subseq-outside-domain.md", Checker.TLC,
            r"^The second argument of SubSeq must be in the domain of its first argument:$"),
    failure("tail-of-empty-sequence.md", Checker.TLC,
            r"^Attempted to apply Tail to the empty sequence\.$"),
    failure("zero-power-zero-tlc-fails.md", Checker.TLC, r"^0\^0 is undefined\.$"),
    failure("integer-outside-tlc-range.md", Checker.TLC,
            r"^TLC can't handle a number this big\.$",
            # Any operator whose result leaves TLC's integer range reports this;
            # corpus8 adds a multiplication to corpus3's exponentiations.
            r"^Overflow when computing "),
    failure("modulo-nonpositive-divisor.md", Checker.TLC,
            r"^The second argument of % should be a positive number"),
    failure("division-by-zero-tlc-fails.md", Checker.TLC,
            r"^The second argument of \\div is 0\.$"),
    failure("infinite-set-as-state-value.md", Checker.TLC,
            r"^TLC has found a state in which the value of a variable contains (?:Int|Nat)$"),
    failure("filter-over-infinite-set.md", Checker.TLC,
            r"^Attempted to enumerate \{ x \\in S : p\(x\) \} when S:$"),
    failure("union-containing-infinite-set.md", Checker.TLC,
            r"^Attempted to enumerate UNION\(s\), but some element of s is nonenumerable\.$",
            r"^Attempted to enumerate S \\cup T when S:$"),
    failure("function-over-infinite-domain.md", Checker.TLC,
            r"^Attempted to enumerate a set of the form \[D -> R\],but the domain D:$",
            # Truncation cuts the tail whenever the overridden value prints long,
            # so anchor on the value name and not on the closing period.
            r"^Attempted to compute the number of elements in the overridden value (?:Int|Nat|Seq\()"),
    failure("string-set-tlc-fails.md", Checker.TLC,
            r"^Attempted to compute the number of elements in the overridden value STRING"),
    # A membership test against a filter over Nat whose predicate raises. TLC
    # reports neither the enumeration limit nor the underlying error.
    failure("filter-over-infinite-set.md", Checker.TLC, r"^Cannot decide if element:$"),
    failure("quantification-over-infinite-set.md", Checker.TLC,
            r"^TLC encountered (?:the |a )non-enumerable quantifier bound$"),
    failure("finite-set-containing-infinite-set.md", Checker.TLC,
            # Truncation drops the "with <value>:" tail whenever the compared
            # value is long, so match only the prefix TLC always emits.
            r"^Attempted to compare (?:the set|overridden value) ",
            r"^Attempted to check equality of the set .+ with the value:$"),
    # The same operand reached as the element of a membership test rather than
    # as a value being compared. TLC names the element, not the set, so the
    # finite-set-containing-infinite-set alternatives above cannot match.
    failure("infinite-set-as-membership-element.md", Checker.TLC,
            r"^Attempted to check if the non-enumerable value$"),
    failure("cardinality-of-infinite-set.md", Checker.TLC,
            r"^Attempted to compute cardinality of the value$"),
    failure("difference-with-infinite-set.md", Checker.TLC,
            r"^Attempted to enumerate S \\ T when S:$"),
    failure("intersection-of-infinite-sets.md", Checker.TLC,
            r"^Attempted to enumerate S \\cap T when neither S:$"),
    failure("subset-test-over-infinite-set.md", Checker.TLC,
            r"^Attempted to evaluate an expression of form S \\subseteq T, but S was not enumer"),
    failure("cartesian-product-with-infinite-set.md", Checker.TLC,
            r"^Attempted to enumerate a set of the form s1 \\X s2 \.\.\. \\X sn,$"),
    failure("function-set-over-infinite-set.md", Checker.TLC,
            r"^Attempted to enumerate a set of the form \[D -> R\],but the range R:$"),
    failure("negative-exponent-tlc-fails.md", Checker.TLC,
            r"^The second argument of \^ should be a natural number"),
    failure("apalache-printer-008.md", Checker.TLC,
            r"^Attempted to evaluate an expression of form P (?:=>|<=>|/\\|\\/) Q when P",
            r"^A non-boolean expression ",
            r"^Attempted to apply the operator ~ to a non-boolean$",
            r"^Evaluating an expression of the form t \\o s when s is not a sequence:$",
            r"^Attempted to check if the value:$",
            # A LET body that absorbed a conjunct leaves a state variable
            # unassigned, so TLC evaluates an identifier the IR always binds.
            r"^In evaluation, the identifier \w+ is either undefined or not an operator\.",
            # The same absorption inside Next: every generated action primes
            # every variable, so an unprimed one means the source lost a
            # conjunct that the IR still has.
            r"^Successor state is not completely specified by action ",
            r"^TLC expected a boolean value, but did not find one",
            r"^Attempted to check equality of integer -?\d+ with non-integer:",
            r"^Attempted to apply the operator DOMAIN to a non-function",
            # A TLC module override refusing an operand of the wrong Java value
            # class. Reachable only once the detail stores the innermost
            # failure: before that, the override wrapper was the stored line.
            r"^Cannot cast tlc2\.value\.\S+ to tlc2\.value\.\S+$"),
    # Inv is Bool in the IR, so a set-valued invariant is printed source that
    # no longer denotes the tree. A constant FALSE invariant is the separate,
    # legitimate TLC restriction below and must not match here.
    failure("apalache-printer-008.md", Checker.TLC,
            r"^The invariant of Inv is equal to (?!FALSE$)", code=150),
    failure("constant-false-invariant.md", Checker.TLC,
            r"^The invariant of Inv is equal to FALSE$", code=150),
    # TLC declines an operand whose finiteness it cannot decide; Apalache
    # answers TRUE for the same set. Reachable once the detail stores the
    # innermost failure, since TLC reports this inside the override wrapper.
    failure("apalache-bmc-007.md", Checker.TLC,
            r"^Attempted to check if expression of form \{x \\in S : p\(x\)\} is a finite set",
            # The same operand reached as a set expression rather than a filter,
            # for example "Nat \\cap Int" or "{0} \\cup Int \\ Nat".
            r"^Attempted to check if the set "),
    failure("non-enumerable-initial-assignment.md", Checker.TLC,
            r"^In computing initial states, the right side of \\IN is not enumerable\.$",
            # The same enumeration limit reached from the next-state action.
            r"^In computing next states, the right side of \\IN is not enumerable\.$"),
    failure("modulo-by-zero-apalache-fails.md", Checker.APALACHE,
            r"^Input error \(see the manual\): Mod by zero at "),
    failure("division-by-zero-apalache-fails.md", Checker.APALACHE,
            r"^Input error \(see the manual\): Division by zero at "),
    failure("zero-power-zero-apalache-fails.md", Checker.APALACHE,
            r"^Input error \(see the manual\): 0 \^ 0 is undefined$"),
    failure("sequence-set-unsupported.md", Checker.APALACHE,
            r"^<unknown>: unsupported expression: Seq\(_\) produces an infinite set"),
    failure("string-set-unsupported.md", Checker.APALACHE,
            r"^<unknown>: unsupported expression: STRING$"),
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
    return unique_match(matches, f"{crash_kind.value}/{entry_hash}", "findings")


def classify_aggregator(
    results: dict[Checker, CheckerResult], entry_hash: str
) -> str:
    matches = {
        signature.issue_file
        for signature in AGGREGATOR_SIGNATURES
        if signature.matches(results)
    }
    return unique_match(matches, f"{AGGREGATOR_DIRECTORY}/{entry_hash}", "issues")


def unique_match(matches: set[str], location: str, description: str) -> str:
    if not matches:
        return NEW_FINDING
    if len(matches) > 1:
        issues = ", ".join(sorted(matches))
        raise TriageError(f"{location} matches multiple {description}: {issues}")
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
