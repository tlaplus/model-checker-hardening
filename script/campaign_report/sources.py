"""Inputs of the report: the exported database, the corpus files and the build."""

from __future__ import annotations

import sqlite3
import subprocess
import tomllib
import xml.etree.ElementTree as ElementTree
import zipfile
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

import cbor2

from .errors import ReportError

# The database schema this report reads (`PRAGMA user_version`, corpus-database manual §2).
SCHEMA_VERSION = 9
REPOSITORY = Path(__file__).resolve().parents[2]
STATS_FILE = ".workflow-stats.cbor"
CONFIG_FILE = "config.toml"
CHECKERS_FILE = ".checkers"


@dataclass(frozen=True)
class ExportOptions:
    fuzztla: Path
    lock: bool
    max_cpus: int | None


def export_database(corpus: Path, output: Path, options: ExportOptions) -> str:
    """Runs `fuzztla export-db` and returns its summary line."""
    command = [
        str(options.fuzztla),
        "export-db",
        "--corpus",
        str(corpus),
        "--output",
        str(output),
        "--force",
    ]
    if not options.lock:
        command.append("--no-lock")
    if options.max_cpus is not None:
        command.append(f"--max-cpus={options.max_cpus}")
    try:
        result = subprocess.run(command, capture_output=True, text=True, check=False)
    except OSError as error:
        raise ReportError(f"cannot run {options.fuzztla}: {error}") from error
    if result.returncode != 0:
        raise ReportError(
            f"export-db failed with status {result.returncode}: {result.stderr.strip()}"
        )
    return result.stdout.strip()


def open_database(path: Path) -> sqlite3.Connection:
    """Opens an exported database read-only; TEMP tables remain writable."""
    if not path.is_file():
        raise ReportError(f"database does not exist: {path}")
    connection = sqlite3.connect(f"{path.resolve().as_uri()}?mode=ro", uri=True)
    version = connection.execute("PRAGMA user_version").fetchone()[0]
    if version != SCHEMA_VERSION:
        connection.close()
        raise ReportError(
            f"database {path} has schema version {version}; this report reads "
            f"version {SCHEMA_VERSION}: export the corpus again or update the report"
        )
    return connection


@dataclass(frozen=True)
class GeneratorStats:
    attempts: int
    rejected: int
    richness_rejected: int
    duplicates: int
    clones: int
    known_defects: dict[str, int]
    richness_samples: int
    minimum_richness: float
    maximum_richness: float
    average_richness: float


@dataclass(frozen=True)
class WorkflowStats:
    """`.workflow-stats.cbor`: cumulative over every controlled exit of `fuzztla run`."""

    total_ns: int
    generator_ns: int
    stage_ns: dict[str, int]
    generator: GeneratorStats


def load_stats(corpus: Path) -> WorkflowStats | None:
    path = corpus / STATS_FILE
    if not path.exists():
        return None
    try:
        with path.open("rb") as source:
            document = cbor2.load(source)
    except (OSError, cbor2.CBORDecodeError) as error:
        raise ReportError(f"cannot read {path}: {error}") from error
    try:
        elapsed = document["elapsedNs"]
        generator = document["generator"]
        return WorkflowStats(
            total_ns=elapsed["total"],
            generator_ns=elapsed["generator"],
            stage_ns=dict(elapsed["stages"]),
            generator=GeneratorStats(
                attempts=generator["attempts"],
                rejected=generator["rejected"],
                richness_rejected=generator["richnessRejected"],
                duplicates=generator["duplicates"],
                clones=generator.get("clones", 0),
                known_defects=dict(generator.get("knownDefects", {})),
                richness_samples=generator["richnessSamples"],
                minimum_richness=generator["minimumRichness"],
                maximum_richness=generator["maximumRichness"],
                average_richness=generator["averageRichness"],
            ),
        )
    except (KeyError, TypeError) as error:
        raise ReportError(f"unexpected shape of {path}: missing {error}") from error


@dataclass(frozen=True)
class Signature:
    id: str
    description: str
    references: tuple[Path, ...]


@dataclass(frozen=True)
class CorpusSettings:
    path: Path
    config: dict[str, Any]
    checkers: tuple[str, ...]
    signatures: dict[str, Signature] = field(default_factory=dict)

    @property
    def config_path(self) -> Path:
        return self.path / CONFIG_FILE


def load_settings(corpus: Path) -> CorpusSettings:
    config_path = corpus / CONFIG_FILE
    try:
        config = tomllib.loads(config_path.read_text(encoding="utf-8"))
    except (OSError, tomllib.TOMLDecodeError) as error:
        raise ReportError(f"cannot read {config_path}: {error}") from error
    checkers = _read_marker(corpus / CHECKERS_FILE)
    return CorpusSettings(
        path=corpus,
        config=config,
        checkers=tuple(checkers.split(",")) if checkers else (),
        signatures=_load_signatures(corpus, config),
    )


def _read_marker(path: Path) -> str | None:
    try:
        return path.read_text(encoding="utf-8").strip() or None
    except FileNotFoundError:
        return None
    except OSError as error:
        raise ReportError(f"cannot read {path}: {error}") from error


def _load_signatures(corpus: Path, config: dict[str, Any]) -> dict[str, Signature]:
    """The known-defect signatures the run filtered with, by id."""
    files = config.get("workflow", {}).get("inputs", {}).get("known_defects", [])
    signatures: dict[str, Signature] = {}
    for relative in files:
        path = (corpus / relative).resolve()
        try:
            document = tomllib.loads(path.read_text(encoding="utf-8"))
        except (OSError, tomllib.TOMLDecodeError) as error:
            raise ReportError(f"cannot read signature database {path}: {error}") from error
        for signature in document.get("signature", []):
            signatures[signature["id"]] = Signature(
                id=signature["id"],
                description=" ".join(signature.get("description", "").split()),
                references=tuple(
                    (path.parent / reference).resolve()
                    for reference in signature.get("references", [])
                ),
            )
    return signatures


@dataclass(frozen=True)
class Provenance:
    """The export's own record, and the checkout that generates the report.

    The corpus records no checker versions, so the versions are those of the
    report-time checkout, not necessarily those of the run.
    """

    fuzztla_version: str
    exported_at: str
    technique: str
    fuzztla_commit: str | None
    dirty: bool
    tla2tools_version: str | None
    apalache_version: str | None


def load_provenance(
    connection: sqlite3.Connection, repository: Path = REPOSITORY
) -> Provenance:
    export = dict(connection.execute("SELECT key, value FROM export"))
    commit = _git(repository, "rev-parse", "--short", "HEAD")
    status = _git(repository, "status", "--porcelain", "--untracked-files=no")
    return Provenance(
        fuzztla_version=export.get("fuzztlaVersion", "unknown"),
        exported_at=export.get("exportedAt", "unknown"),
        technique=export.get("technique", "unknown"),
        fuzztla_commit=commit,
        dirty=bool(status),
        tla2tools_version=_pom_property(repository / "pom.xml", "tla2tools.version"),
        apalache_version=_jar_version(repository / "target" / "apalache.jar"),
    )


def _git(repository: Path, *arguments: str) -> str | None:
    try:
        result = subprocess.run(
            ["git", "-C", str(repository), *arguments],
            capture_output=True,
            text=True,
            check=False,
        )
    except OSError:
        return None
    return result.stdout.strip() if result.returncode == 0 else None


def _pom_property(pom: Path, name: str) -> str | None:
    try:
        root = ElementTree.parse(pom).getroot()
    except (OSError, ElementTree.ParseError):
        return None
    namespace = {"m": "http://maven.apache.org/POM/4.0.0"}
    element = root.find(f"m:properties/m:{name}", namespace)
    return element.text if element is not None else None


def _jar_version(jar: Path) -> str | None:
    try:
        with zipfile.ZipFile(jar) as archive:
            manifest = archive.read("META-INF/MANIFEST.MF").decode("utf-8")
    except (OSError, KeyError, zipfile.BadZipFile):
        return None
    for line in manifest.splitlines():
        if line.startswith("Implementation-Version:"):
            return line.split(":", 1)[1].strip()
    return None
