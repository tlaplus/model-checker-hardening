"""Triage labels of crashes and disagreements, from `script/triager.py`, in a TEMP table."""

from __future__ import annotations

import sqlite3
from dataclasses import dataclass
from enum import Enum
from pathlib import Path

import triager

from .errors import ReportError

TRIAGE_TABLE = "triage"


class LabelClass(Enum):
    """What a triage label says about an entry, in the order the report lists them.

    Every class but DOCUMENTED is one label of the triager; a documented label is
    the file name of a finding or conformance document.
    """

    DOCUMENTED = ("documented", None, "matches a documented finding or conformance issue")
    NEW = ("new", triager.NEW_FINDING, "unclassified; needs investigation")
    STATE_COUNT = (
        "state count",
        triager.STATE_COUNT,
        "completed searches reach different numbers of states (ADR 0019)",
    )
    FIRST_ERROR = (
        "first error",
        triager.FIRST_ERROR_RACE,
        "workers stop at different first errors (ADR 0018); not a defect",
    )
    TIMEOUT = ("timeout", triager.WORKER_TIMEOUT, "the harness killed a slow worker")

    def __new__(cls, name: str, triage_label: str | None, meaning: str) -> LabelClass:
        member = object.__new__(cls)
        member._value_ = name
        member.triage_label = triage_label
        member.meaning = meaning
        return member

    @classmethod
    def of(cls, label: str) -> LabelClass:
        return next((c for c in cls if c.triage_label == label), cls.DOCUMENTED)


@dataclass(frozen=True)
class Triage:
    """Where each documented label's document lives, for links from the report."""

    documents: dict[str, Path]

    def document(self, label: str) -> Path | None:
        return self.documents.get(label)


def load_triage(connection: sqlite3.Connection, corpus: Path, repository: Path) -> Triage:
    """Classifies the corpus with the triager and stores `triage(hash, directory, label)`.

    Nothing is written to the corpus: the triager's CSV reports are not produced.
    """
    try:
        triager.validate_signature_catalog(repository)
        rows = [
            (entry_hash, kind.value, label)
            for kind in triager.CrashKind
            if (corpus / kind.value).is_dir()
            for entry_hash, label in triager.classify_directory(corpus, kind)
        ]
        if (corpus / triager.AGGREGATOR_DIRECTORY).is_dir():
            rows += [
                (entry_hash, triager.AGGREGATOR_DIRECTORY, label)
                for entry_hash, label in triager.classify_aggregator_directory(corpus)
            ]
    except triager.TriageError as error:
        raise ReportError(f"triage failed: {error}") from error
    connection.execute(
        f"CREATE TEMP TABLE {TRIAGE_TABLE} "
        "(hash TEXT NOT NULL, directory TEXT NOT NULL, label TEXT NOT NULL, "
        "PRIMARY KEY (hash, directory))"
    )
    connection.executemany(f"INSERT INTO {TRIAGE_TABLE} VALUES (?, ?, ?)", rows)
    return Triage(_documents(repository))


def _documents(repository: Path) -> dict[str, Path]:
    """Finding and conformance documents by file name, as triage labels name them."""
    documents: dict[str, Path] = {}
    for directory in ("findings", "conformance"):
        for document in (repository / directory).rglob("*.md"):
            if document.name != "README.md":
                documents.setdefault(document.name, document)
    return documents
