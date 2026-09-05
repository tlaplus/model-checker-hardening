#!/usr/bin/env python3
"""Classify corpus crashes by conservative, deterministic signatures."""

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
from typing import Pattern, Sequence


class TriageError(Exception):
    """Raised when crash evidence or the signature catalog is invalid."""


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

ENTRY_NAME = re.compile(r"(?P<hash>[0-9a-f]{64})\.cbor\Z")
STACKTRACE_NAME = re.compile(r"(?P<hash>[0-9a-f]{64})\.stacktrace\Z")
CSV_HEADER = ("entry_hash", "issue")
NEW_FINDING = "NEW"


def validate_signature_catalog(repository_root: Path) -> None:
    findings_directory = repository_root / "findings"
    if not findings_directory.is_dir():
        raise TriageError(f"findings directory does not exist: {findings_directory}")

    files_by_name: dict[str, list[Path]] = {}
    for finding in findings_directory.rglob("*.md"):
        if finding.name == "README.md":
            continue
        files_by_name.setdefault(finding.name, []).append(finding)

    for finding_file in sorted({signature.finding_file for signature in SIGNATURES}):
        matches = files_by_name.get(finding_file, [])
        if not matches:
            raise TriageError(f"signature references missing finding: {finding_file}")
        if len(matches) > 1:
            locations = ", ".join(str(path) for path in sorted(matches))
            raise TriageError(
                f"signature references ambiguous finding {finding_file}: {locations}"
            )


def classify(crash_kind: CrashKind, diagnostic: str, entry_hash: str) -> str:
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

    report_paths = []
    for crash_kind in CrashKind:
        report_path = corpus / crash_kind.report_name
        write_report(report_path, reports[crash_kind])
        report_paths.append(report_path)
    return report_paths


def parse_arguments(arguments: Sequence[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Classify parser, TLC, and Apalache corpus crashes."
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
